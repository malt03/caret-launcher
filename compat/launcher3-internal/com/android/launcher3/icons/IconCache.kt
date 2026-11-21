/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Modified for TextLauncher: a stand-in that keeps only what TextLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.VisibleForTesting
import com.android.launcher3.Flags
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.Utilities
import com.android.launcher3.icons.CacheableShortcutCachingLogic.getComponent
import com.android.launcher3.icons.CacheableShortcutCachingLogic.getUser
import com.android.launcher3.icons.cache.BaseIconCache
import com.android.launcher3.icons.cache.CacheLookupFlag
import com.android.launcher3.icons.cache.LauncherActivityCachingLogic
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.PackageItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.ApplicationInfoWrapper
import com.android.launcher3.util.CancellableTask
import com.android.launcher3.util.ComponentKey
import java.util.concurrent.Executor
import java.util.function.Supplier

/**
 * Cache of application icons. Icons can be made from any thread.
 *
 * Stand-in for Launcher3's IconCache (android-17.0.0_r1), made from IconCache.kt by deleting what
 * TextLauncher does not have: Dagger, bulk loading for LoaderTask, home screen files, install
 * sessions, widget categories, archived apps and instant apps. Changes are marked with
 * "TextLauncher:".
 */
class IconCache(
    context: Context,
    dbFileName: String?,
    // TextLauncher: Executors.MODEL_EXECUTOR's looper, InvariantDeviceProfile's sizes and the
    // LauncherIcons pool come from the app.
    bgLooper: Looper,
    iconDpi: Int,
    iconPixelSize: Int,
    iconProvider: IconProvider,
    private val iconPool: LauncherIcons.IconPool,
) :
    BaseIconCache(
        context,
        dbFileName,
        bgLooper = bgLooper,
        iconDpi = iconDpi,
        iconPixelSize = iconPixelSize,
        inMemoryCache = true,
        iconProvider,
    ) {

    private val launcherApps: LauncherApps = context.getSystemService(LauncherApps::class.java)!!

    private val cancelledTask =
        // TextLauncher: the main executor of the context (Executors.MAIN_EXECUTOR).
        CancellableTask<Any?>({ null }, context.mainExecutor, {}).apply { cancel() }

    private val userManager: UserManager = context.getSystemService(UserManager::class.java)!!

    // TextLauncher: UserCache.getSerialNumberForUser, without the cache.
    override fun getSerialNumberForUser(user: UserHandle): Long =
        userManager.getSerialNumberForUser(user)

    // TextLauncher: InstantAppResolver's default; instant apps are not shown.
    override fun isInstantApp(info: ApplicationInfo): Boolean = false

    override val iconFactory: BaseIconFactory
        get() = iconPool.obtain()

    /** Updates the entries related to the given package in memory and persistent DB. */
    @Synchronized
    fun updateIconsForPkg(packageName: String, user: UserHandle) {
        val apps = launcherApps.getActivityList(packageName, user)

        // TextLauncher: no archived apps.
        removeIconsForPkg(packageName, user)
        val userSerial = userManager.getSerialNumberForUser(user)
        apps.forEach { addIconToDBAndMemCache(it, LauncherActivityCachingLogic, userSerial) }
    }

    /** Closes the cache DB. This will clear any in-memory cache. */
    fun close() {
        // Close the actual DB on the same thread where the cache is used.
        workerHandler.post {
            // This will clear all pending updates
            getUpdateHandler()
            iconDb.close()
        }
    }

    /**
     * Fetches high-res icon for the provided ItemInfo and updates the caller when done.
     *
     * @return a request ID that can be used to cancel the request.
     */
    @AnyThread
    fun updateIconInBackground(
        uiExecutor: Executor,
        caller: ItemInfoUpdateReceiver,
        info: ItemInfoWithIcon?,
        lookupFlag: CacheLookupFlag,
    ): CancellableTask<*> {
        val task: Supplier<ItemInfoWithIcon> =
            when (info) {
                is AppInfo,
                is WorkspaceItemInfo ->
                    Supplier {
                        getTitleAndIcon(info, lookupFlag)
                        info
                    }
                is PackageItemInfo ->
                    Supplier {
                        getTitleAndIconForApp(info, lookupFlag)
                        info
                    }
                else -> {
                    Log.i(TAG, "Icon update not supported for " + info?.javaClass?.name)
                    return cancelledTask
                }
            }

        // TextLauncher: the icon thread's priority is not raised while requests are pending.
        val endRunnable = Runnable {}

        val request = CancellableTask(task, uiExecutor, { caller.reapplyItemInfo(it) }, endRunnable)
        Utilities.postAsyncCallback(workerHandler, request)
        return request
    }

    /** Updates {@param application} only if a valid entry is found. */
    @Synchronized
    fun updateTitleAndIcon(application: AppInfo) {
        val entry =
            cacheLocked(
                requireNotNull(application.componentName) { "Component can't be null" },
                application.user,
                { null },
                LauncherActivityCachingLogic,
                application.matchingLookupFlag,
            )
        applyCacheEntry(entry, application)
    }

    /** Fill in `info` with the icon and label for `activityInfo` */
    @Synchronized
    fun getTitleAndIcon(
        info: ItemInfoWithIcon,
        activityInfo: LauncherActivityInfo?,
        lookupFlag: CacheLookupFlag,
    ) {
        // TextLauncher: no archived apps.
        // If we already have activity info, no need to use package icon
        getTitleAndIcon(info, lookupFlag.withUsePackageIcon(false)) { activityInfo }
    }

    /** Fill in [info] with the icon for [si] */
    @JvmOverloads
    fun getShortcutIcon(
        info: ItemInfoWithIcon,
        si: ShortcutInfo,
        appInfo: ApplicationInfoWrapper =
            ApplicationInfoWrapper(context, si.getPackage(), si.userHandle),
        lookupFlags: CacheLookupFlag = CacheLookupFlag.DEFAULT_LOOKUP_FLAG.withThemeIcon(),
    ) {
        val oldIcon = info.bitmap
        val csi = CacheableShortcutInfo(si, appInfo) { oldIcon }
        getShortcutIcon(info, csi, lookupFlags)
    }

    /**
     * Fill in [info] with the icon and label for [si]. If the icon is not available, and fallback
     * check returns true, it keeps the old icon. Shortcut entries are not kept in memory since they
     * are not frequently used
     */
    fun <T : ItemInfoWithIcon> getShortcutIcon(
        info: T,
        si: CacheableShortcutInfo,
        lookupFlags: CacheLookupFlag,
    ) {
        val user = getUser(si)
        var bitmapInfo =
            cacheLocked(
                    getComponent(si),
                    user,
                    { si },
                    CacheableShortcutCachingLogic,
                    lookupFlags.withSkipAddToMemCache(),
                )
                .bitmap
        if (bitmapInfo.isLowRes) {
            bitmapInfo = getDefaultIcon(user)
        }
        if (isDefaultIcon(bitmapInfo, user)) return
        info.bitmap = bitmapInfo.withBadgeInfo(getShortcutInfoBadge(si.shortcutInfo, lookupFlags))
    }

    /** Returns the badging info for the shortcut */
    fun getShortcutInfoBadge(shortcutInfo: ShortcutInfo, lookupFlags: CacheLookupFlag): BitmapInfo =
        getShortcutInfoBadgeItem(shortcutInfo, lookupFlags).bitmap

    @VisibleForTesting
    fun getShortcutInfoBadgeItem(
        shortcutInfo: ShortcutInfo,
        lookupFlags: CacheLookupFlag,
    ): ItemInfoWithIcon {
        // Check for badge override first.
        var pkg = shortcutInfo.getPackage()
        // TextLauncher: the badge override is not read (InstallSessionHelper.isTrustedPackage).
        run {
            // Try component based badge before trying the normal package badge
            val cn = shortcutInfo.activity
            if (cn != null) {
                // Get the app info for the source activity.
                val appInfo = AppInfo()
                appInfo.user = shortcutInfo.userHandle
                appInfo.componentName = cn
                appInfo.intent =
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(cn)
                getTitleAndIcon(appInfo, lookupFlags)
                return appInfo
            }
        }
        val pkgInfo = PackageItemInfo(pkg, shortcutInfo.userHandle)
        getTitleAndIconForApp(pkgInfo, lookupFlags)
        return pkgInfo
    }

    /**
     * Fill in {@param info} with the icon and label. If the corresponding activity is not found, it
     * reverts to the package icon.
     */
    @Synchronized
    fun getTitleAndIcon(info: ItemInfoWithIcon, lookupFlag: CacheLookupFlag) {
        // TextLauncher: no home screen files.
        if (info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
            val sk = ShortcutKey.fromItemInfo(info)
            val si: ShortcutInfo =
                sk.buildRequest(context).query(ShortcutRequest.ALL).getOrNull(0) ?: return
            getShortcutIcon(
                info,
                si,
                ApplicationInfoWrapper(context, si.getPackage(), si.userHandle),
                lookupFlag,
            )
        } else if (info.targetComponent == null) {
            // null info means not installed, but if we have a component from the intent then
            // we should still look in the cache for restored app icons.
            info.bitmap = getDefaultIcon(info.user)
            info.title = ""
            info.contentDescription = ""
        } else {
            val intent = info.intent
            getTitleAndIcon(info, lookupFlag.withUsePackageIcon()) {
                launcherApps.resolveActivity(intent, info.user)
            }
        }
    }

    /** Fill in {@param mWorkspaceItemInfo} with the icon and label for {@param info} */
    @Synchronized
    fun getTitleAndIcon(
        infoInOut: ItemInfoWithIcon,
        lookupFlag: CacheLookupFlag,
        activityInfoProvider: Supplier<LauncherActivityInfo?>,
    ) {
        val entry =
            cacheLocked(
                requireNotNull(infoInOut.targetComponent) { "Component can't be null" },
                infoInOut.user,
                activityInfoProvider,
                LauncherActivityCachingLogic,
                lookupFlag,
            )
        applyCacheEntry(entry, infoInOut)
    }

    // TextLauncher: no bulk loading (getTitlesAndIconsInBulk) for LoaderTask.

    /** Fill in {@param infoInOut} with the corresponding icon and label. */
    @Synchronized
    fun getTitleAndIconForApp(infoInOut: PackageItemInfo, lookupFlag: CacheLookupFlag) {
        val entry = getEntryForPackageLocked(infoInOut.packageName, infoInOut.user, lookupFlag)
        applyCacheEntry(entry, infoInOut)
        // TextLauncher: no widget categories.
    }

    @Synchronized
    private fun getBadgedIcon(bitmap: BitmapInfo?, user: UserHandle): BitmapInfo =
        bitmap?.withFlags(getUserFlagOpLocked(user)) ?: getDefaultIcon(user)

    private fun applyCacheEntry(entry: CacheEntry, info: ItemInfoWithIcon) {
        info.title = Utilities.trim(entry.title)
        // TextLauncher: no home screen files.
        info.contentDescription = entry.contentDescription
        info.bitmap = entry.bitmap
        // TextLauncher: no app title override for archived apps.
        if (entry.bitmap == null) {
            // TODO: entry.bitmap can never be null, so this should not happen at all.
            Log.wtf(TAG, "Cannot find bitmap from the cache, default icon was loaded.")
            info.bitmap = getDefaultIcon(info.user)
        }
        // TextLauncher: no archived apps, so no package override.
    }

    @VisibleForTesting
    @Synchronized
    fun isItemInDb(cacheKey: ComponentKey): Boolean =
        getEntryFromDBLocked(
            cacheKey,
            CacheEntry(),
            CacheLookupFlag.DEFAULT_LOOKUP_FLAG,
            LauncherActivityCachingLogic,
        )

    /** Interface for receiving itemInfo with high-res icon. */
    fun interface ItemInfoUpdateReceiver {
        fun reapplyItemInfo(info: ItemInfoWithIcon?)
    }

    /** Log persistently to FileLog.d for debugging. */
    override fun logPersistently(message: String, e: Exception?) {
        FileLog.d(TAG, message, e)
    }

    companion object {
        // Shortcut extra which can point to a packageName and can be used to indicate an alternate
        // badge info. Launcher only reads this if the shortcut comes from a system app.
        const val EXTRA_SHORTCUT_BADGE_OVERRIDE_PACKAGE: String =
            "extra_shortcut_badge_override_package"

        private const val TAG = "Launcher.IconCache"
    }
}
