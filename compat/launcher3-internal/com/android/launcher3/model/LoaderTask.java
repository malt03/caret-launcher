/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */
package com.android.launcher3.model;

import static com.android.launcher3.icons.cache.CacheLookupFlag.DEFAULT_LOOKUP_FLAG;
import static com.android.launcher3.model.data.AppsListData.FLAG_HAS_SHORTCUT_PERMISSION;
import static com.android.launcher3.model.data.AppsListData.FLAG_QUIET_MODE_CHANGE_PERMISSION;
import static com.android.launcher3.model.data.AppsListData.FLAG_QUIET_MODE_ENABLED;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.os.Bundle;
import android.os.Process;
import android.os.Trace;
import android.os.UserHandle;
import android.util.Log;
import android.util.LongSparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.CacheableShortcutCachingLogic;
import com.android.launcher3.icons.CacheableShortcutInfo;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.cache.IconCacheUpdateHandler;
import com.android.launcher3.icons.cache.LauncherActivityCachingLogic;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.IconRequestInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.pm.UserCache.UserManagerState;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.shortcuts.ShortcutRequest.QueryResult;
import com.android.launcher3.util.IOUtils;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

/**
 * Runnable for the thread that loads the contents of the launcher:
 *   - workspace icons
 *   - widgets
 *   - all apps icons
 *   - deep shortcuts within apps
 *
 * <p>Stand-in for Launcher3's LoaderTask (android-17.0.0_r1), made from LoaderTask.java by deleting
 * Dagger, the restore, the install sessions, the safe mode, the default layout, the home screen
 * files, the first screen broadcast, the widgets model, the folder names, the extra items, the
 * work and private profiles and the bulk icon loading; changes are marked with "CaretLauncher:".
 * What the app does in Launcher3's place (the grid migration, the ghost widgets) comes through
 * {@link Hooks}.
 */
public class LoaderTask implements Runnable {
    private static final String TAG = "LoaderTask";

    private static final boolean DEBUG = true;

    public final String name;
    private final Context mContext;
    private final LauncherModel mModel;
    private final InvariantDeviceProfile mIDP;
    private final AllAppsList mBgAllAppsList;
    protected final BgDataModel mBgDataModel;

    @NonNull
    private final BaseLauncherBinder mLauncherBinder;

    private final LauncherApps mLauncherApps;
    private final UserCache mUserCache;

    private final IconCache mIconCache;

    private final UserManagerState mUserManagerState;
    private Map<ShortcutKey, ShortcutInfo> mShortcutKeyToPinnedShortcuts;

    private List<IconRequestInfo<WorkspaceItemInfo>> mWorkspaceIconRequestInfos = new ArrayList<>();

    private boolean mStopped;

    private final Set<PackageUserKey> mPendingPackages = new HashSet<>();
    private String mDbName;

    // CaretLauncher: what the app does in Launcher3's place.
    private final Hooks mHooks;

    // CaretLauncher: no Dagger.
    public LoaderTask(
            Context context,
            InvariantDeviceProfile idp,
            LauncherModel model,
            IconCache iconCache,
            AllAppsList bgAllAppsList,
            BgDataModel bgModel,
            @NonNull String callerName,
            @NonNull BaseLauncherBinder launcherBinder,
            Hooks hooks) {
        name = callerName;
        mContext = context;
        mIDP = idp;
        mModel = model;
        mBgAllAppsList = bgAllAppsList;
        mBgDataModel = bgModel;
        mLauncherBinder = launcherBinder;
        mLauncherApps = mContext.getSystemService(LauncherApps.class);
        mUserCache = UserCache.INSTANCE.get(context);
        mIconCache = iconCache;
        mUserManagerState = mUserCache.getUserManagerState();
        mHooks = hooks;
    }

    private synchronized void verifyNotStopped() throws CancellationException {
        if (mStopped) {
            throw new CancellationException("Loader stopped");
        }
    }

    // CaretLauncher: no install sessions (sendFirstScreenActiveInstallsBroadcast).

    private void loadAllSurfacesOrdered(@Nullable LoaderMemoryLogger memoryLogger) {

        IconCacheUpdateHandler updateHandler = mIconCache.getUpdateHandler();
        List<CacheableShortcutInfo> allShortcuts = new ArrayList<>();
        Trace.beginSection("LoadWorkspace");
        try {
            loadWorkspaceImpl(allShortcuts, memoryLogger);
        } finally {
            Trace.endSection();
        }
        logASplit("loadWorkspace finished");

        // Sanitize data re-syncs widgets/shortcuts based on the workspace loaded from db.
        // CaretLauncher: one database, which is always the main one (mIDP.dbFile).
        verifyNotStopped();
        sanitizeWidgetsShortcutsAndPackages();
        logASplit("sanitizeData finished");

        verifyNotStopped();
        mLauncherBinder.bindWorkspace(true /* incrementBindId */, /* isBindSync= */ false);
        logASplit("bindWorkspace finished");

        // CaretLauncher: no home screen files, model delegate or first screen broadcast.

        logASplit("step 1 loading workspace complete");
        verifyNotStopped();

        // second step
        Trace.beginSection("LoadAllApps");
        List<LauncherActivityInfo> allActivityList;
        try {
            allActivityList = loadAllApps();
        } finally {
            Trace.endSection();
        }
        logASplit("loadAllApps finished");

        verifyNotStopped();
        mLauncherBinder.bindAllApps();
        logASplit("bindAllApps finished");

        verifyNotStopped();
        // CaretLauncher: no promise icons or restored widgets to ignore (setIgnorePackages).
        updateHandler.updateIcons(allActivityList,
                LauncherActivityCachingLogic.INSTANCE,
                mModel::onPackageIconsUpdated);
        logASplit("update AllApps icon cache finished");

        verifyNotStopped();
        logASplit("saving all shortcuts in icon cache");
        updateHandler.updateIcons(allShortcuts, CacheableShortcutCachingLogic.INSTANCE,
                mModel::onPackageIconsUpdated);

        logASplit("step 2 loading AllApps complete");
        verifyNotStopped();

        // third step
        List<ShortcutInfo> allDeepShortcuts = loadDeepShortcuts();
        logASplit("loadDeepShortcuts finished");

        verifyNotStopped();
        logASplit("saving deep shortcuts in icon cache");
        updateHandler.updateIcons(
                CacheableShortcutInfo.convertShortcutsToCacheableShortcuts(
                        allDeepShortcuts, allActivityList),
                CacheableShortcutCachingLogic.INSTANCE,
                (pkgs, user) -> { });

        logASplit("step 3 loading all shortcuts complete");
        verifyNotStopped();

        // CaretLauncher: no widgets model (fourth step) or folder names (fifth step).

        verifyNotStopped();
        updateHandler.finish();
        logASplit("finish icon update");
    }

    public void run() {
        synchronized (this) {
            // Skip fast if we are already stopped.
            if (mStopped) {
                return;
            }
        }

        Trace.beginSection(TAG);
        // CaretLauncher: the model executor's priority is not raised (elevatePriority).
        LoaderMemoryLogger memoryLogger = new LoaderMemoryLogger();

        try (LauncherModel.LoaderTransaction transaction = mModel.beginLoader(this)) {
            loadAllSurfacesOrdered(memoryLogger);

            transaction.commit();
            memoryLogger.clearLogs();
            // CaretLauncher: no restore.
        } catch (CancellationException e) {
            // Loader stopped, ignore
            FileLog.w(TAG, "LoaderTask cancelled");
        } catch (Exception e) {
            memoryLogger.printLogs();
            throw e;
        }
        Trace.endSection();
    }

    public synchronized void stopLocked(String callerName) {
        mStopped = true;
        FileLog.w(TAG, "stopLocked: Loader [" + name + "] stopping be caller: " + callerName);
        this.notify();
    }

    private void loadWorkspaceImpl(
            List<CacheableShortcutInfo> allDeepShortcuts,
            @Nullable LoaderMemoryLogger memoryLogger) {
        final boolean isSdCardReady = Utilities.isBootCompleted();

        ModelDbController dbController = mModel.getModelDbController();

        try {
            // CaretLauncher: the app's grid migration in the one database (attemptMigrateDb).
            mHooks.migrateGridIfNeeded(dbController);
        } catch (Exception e) {
            FileLog.e(TAG, "Failed to migrate grid", e);
        }

        // CaretLauncher: no default layout (loadDefaultFavoritesIfNecessary).

        synchronized (mBgDataModel) {
            mBgDataModel.clear();
            mPendingPackages.clear();

            // CaretLauncher: no install sessions.

            mShortcutKeyToPinnedShortcuts = new HashMap<>();
            final LoaderCursor c = new LoaderCursor(
                    dbController.query(null, null, null, null),
                    mUserManagerState, mContext, mIconCache, mIDP, mModel);
            final Bundle extras = c.getExtras();
            mDbName = extras == null ? null : extras.getString(ModelDbController.EXTRA_DB_NAME);
            WorkspaceItemProcessor itemProcessor;
            try {
                final LongSparseArray<Boolean> unlockedUsers = new LongSparseArray<>();
                queryPinnedShortcutsForUnlockedUsers(mContext, unlockedUsers);

                mWorkspaceIconRequestInfos = new ArrayList<>();
                itemProcessor = new WorkspaceItemProcessor(c, memoryLogger,
                        mUserManagerState, mLauncherApps, mPendingPackages,
                        mShortcutKeyToPinnedShortcuts, mContext, mIDP, mIconCache,
                        isSdCardReady, mWorkspaceIconRequestInfos, unlockedUsers);

                if (mStopped) {
                    Log.w(TAG, "loadWorkspaceImpl: Loader stopped, skipping item processing");
                } else {
                    // CaretLauncher: no preloaded items (processPreloadedItems).
                    while (!mStopped && c.moveToNext()) {
                        itemProcessor.processItem();
                    }
                }
                tryLoadWorkspaceIconsInBulk(mWorkspaceIconRequestInfos);
            } finally {
                IOUtils.closeSilently(c);
            }

            // Break early if we've stopped loading
            if (mStopped) {
                mBgDataModel.clear();
                return;
            }

            // CaretLauncher: no string cache (updateStringCache).

            var loadedItems = itemProcessor.finalizeData(mModel.getModelDbController());
            // CaretLauncher: no restore (browser icon migration).

            mBgDataModel.dataLoadComplete(loadedItems, name /* reason */);
        }
    }

    /**
     * Initialized the UserManagerState, and determines which users are unlocked. Additionally, if
     * the user is unlocked, it queries LauncherAppsService for pinned shortcuts and stores the
     * result in a class variable to be used in other methods while processing workspace items.
     *
     * @param context used to query LauncherAppsService
     * @param unlockedUsers this param is changed, and the updated value is used outside this method
     */
    @WorkerThread
    private void queryPinnedShortcutsForUnlockedUsers(Context context,
            LongSparseArray<Boolean> unlockedUsers) {
        // CaretLauncher: the personal profile only (docs/spec.md 2.1), in place of
        // mUserManagerState.getAllCachedInfos().
        UserHandle user = Process.myUserHandle();
        long serialNo = mUserCache.getSerialNumberForUser(user);
        boolean userUnlocked = mUserManagerState.isUserUnlocked(user);

        // We can only query for shortcuts when the user is unlocked.
        if (userUnlocked) {
            QueryResult pinnedShortcuts = new ShortcutRequest(context, user)
                    .query(ShortcutRequest.PINNED);
            if (pinnedShortcuts.wasSuccess()) {
                for (ShortcutInfo shortcut : pinnedShortcuts) {
                    mShortcutKeyToPinnedShortcuts.put(ShortcutKey.fromInfo(shortcut),
                            shortcut);
                }
                if (pinnedShortcuts.isEmpty()) {
                    FileLog.d(TAG, "No pinned shortcuts found for user " + user);
                }
            } else {
                // Shortcut manager can fail due to some race condition when the
                // lock state changes too frequently. For the purpose of the loading
                // shortcuts, consider the user is still locked.
                FileLog.d(TAG, "Shortcut request failed for user "
                        + user + ", user may still be locked.");
                userUnlocked = false;
            }
        }
        unlockedUsers.put(serialNo, userUnlocked);
    }

    private void tryLoadWorkspaceIconsInBulk(
            List<IconRequestInfo<WorkspaceItemInfo>> iconRequestInfos) {
        Trace.beginSection("LoadWorkspaceIconsInBulk");
        try {
            // CaretLauncher: one by one, as IconCache has no bulk loading (getTitlesAndIconsInBulk).
            for (IconRequestInfo<WorkspaceItemInfo> iconRequestInfo : iconRequestInfos) {
                mIconCache.getTitleAndIcon(iconRequestInfo.itemInfo,
                        iconRequestInfo.launcherActivityInfo, iconRequestInfo.lookupFlag);
            }
            for (IconRequestInfo<WorkspaceItemInfo> iconRequestInfo : iconRequestInfos) {
                WorkspaceItemInfo wai = iconRequestInfo.itemInfo;
                if (mIconCache.isDefaultIcon(wai.bitmap, wai.user)) {
                    logASplit("tryLoadWorkspaceIconsInBulk: default icon found for "
                            + wai.getTargetComponent() + ", will attempt to load from iconBlob");
                    iconRequestInfo.loadIconFromDbBlob(mContext);
                }
            }
        } finally {
            Trace.endSection();
        }
    }

    // CaretLauncher: no promise icons or restored widgets (setIgnorePackages).

    private void sanitizeWidgetsShortcutsAndPackages() {
        // Remove any ghost widgets
        // CaretLauncher: the app's widget host (ModelDbController.removeGhostWidgets).
        IntSet validWidgets = new IntSet();
        for (ItemInfo info : mBgDataModel.itemsIdMap) {
            if (info instanceof LauncherAppWidgetInfo widget) {
                validWidgets.add(widget.appWidgetId);
            }
        }
        mHooks.removeGhostWidgets(validWidgets);

        // Update pinned state of model shortcuts
        mBgDataModel.updateShortcutPinnedState(mContext);

        // CaretLauncher: no SD card receiver (SdCardAvailableReceiver).
    }

    private List<LauncherActivityInfo> loadAllApps() {
        List<LauncherActivityInfo> allActivityList = new ArrayList<>();
        // Clear the list of apps
        mBgAllAppsList.clear();

        // CaretLauncher: the personal profile only (docs/spec.md 2.1), in place of
        // mUserManagerState.getAllCachedInfos(), and no bulk icon requests.
        UserHandle user = Process.myUserHandle();

        // Query for the set of apps
        final List<LauncherActivityInfo> apps = mLauncherApps.getActivityList(null, user);
        // Fail if we don't have any apps
        // TODO: Fix this. Only fail for the current user.
        if (apps == null || apps.isEmpty()) {
            return allActivityList;
        }
        boolean quietMode = mUserManagerState.isUserQuiet(user);

        // Create the ApplicationInfos
        for (int i = 0; i < apps.size(); i++) {
            LauncherActivityInfo app = apps.get(i);
            // CaretLauncher: AppInfo(LauncherActivityInfo, UserHandle); no archived apps. The icon
            // is loaded as the app is added (no getTitlesAndIconsInBulk).
            AppInfo appInfo = new AppInfo(app, user);
            mBgAllAppsList.add(appInfo, app);
        }
        allActivityList.addAll(apps);

        // CaretLauncher: no private space.
        mBgAllAppsList.setFlags(FLAG_QUIET_MODE_ENABLED, quietMode);
        mBgAllAppsList.setFlags(FLAG_HAS_SHORTCUT_PERMISSION,
                hasShortcutsPermission(mContext));
        mBgAllAppsList.setFlags(FLAG_QUIET_MODE_CHANGE_PERMISSION,
                mContext.checkSelfPermission("android.permission.MODIFY_QUIET_MODE")
                        == PackageManager.PERMISSION_GRANTED);

        mBgAllAppsList.getAndResetChangeFlag();
        return allActivityList;
    }

    // CaretLauncher: no archived apps (getAppInfoIconRequestInfo).

    private List<ShortcutInfo> loadDeepShortcuts() {
        List<ShortcutInfo> allShortcuts = new ArrayList<>();

        if (mBgAllAppsList.hasShortcutHostPermission()) {
            // CaretLauncher: the personal profile only; no deep shortcut counts.
            UserHandle user = Process.myUserHandle();
            if (mUserManagerState.isUserUnlocked(user)) {
                List<ShortcutInfo> shortcuts =
                        new ShortcutRequest(mContext, user).query(ShortcutRequest.ALL);
                allShortcuts.addAll(shortcuts);
            }
        }
        return allShortcuts;
    }

    // CaretLauncher: no folder names (loadFolderNames) or widgets (isValidProvider).

    private static void logASplit(String label) {
        if (DEBUG) {
            Log.d(TAG, label);
        }
    }

    /**
     * CaretLauncher: PackageManagerHelper.hasShortcutsPermission, as there is no
     * PackageManagerHelper.
     */
    private static boolean hasShortcutsPermission(Context context) {
        try {
            return context.getSystemService(LauncherApps.class).hasShortcutHostPermission();
        } catch (SecurityException | IllegalStateException e) {
            Log.e(TAG, "Failed to make shortcut manager call", e);
        }
        return false;
    }

    /** CaretLauncher: no Dagger. */
    public interface LoaderTaskFactory {
        LoaderTask newLoaderTask(String callerName, BaseLauncherBinder binder);
    }

    // CaretLauncher: members CaretLauncher adds.

    /** CaretLauncher: what the app does in Launcher3's place, on the model thread. */
    public interface Hooks {
        /**
         * ModelDbController.attemptMigrateDb: the app's grid migration (GridSizeMigration), in the
         * one database.
         */
        void migrateGridIfNeeded(ModelDbController dbController);

        /**
         * ModelDbController.removeGhostWidgets: removes the widgets of the app's widget host that
         * are not in [validWidgets].
         */
        void removeGhostWidgets(IntSet validWidgets);
    }
}
