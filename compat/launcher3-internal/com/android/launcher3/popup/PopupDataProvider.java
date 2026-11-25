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

package com.android.launcher3.popup;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.notification.NotificationRepository;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.views.ActivityContext;

import kotlin.Unit;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Provides data for the popup menu that appears after long-clicking on apps.
 *
 * <p>Stand-in for Launcher3's PopupDataProvider (android-17.0.0_r1), made from
 * PopupDataProvider.java by deleting what CaretLauncher does not have (Dagger and BgDataModel);
 * changes are marked with "CaretLauncher:".
 *
 * <p>CaretLauncher: no Dagger activity component, so the app makes it with the notification
 * repository and the all apps store, and closes it ({@link #close}) where Launcher3's
 * ActivityContext.closeOnDestroy would.
 */
public class PopupDataProvider implements SafeCloseable {

    private final NotificationRepository mNotificationRepo;
    private final ActivityContext mContext;
    private final AllAppsStore mAppsStore;
    // CaretLauncher: the context for the shortcut queries (no BgDataModel).
    private final Context mAppContext;
    // CaretLauncher: the subscription that close() ends (ActivityContext.closeOnDestroy).
    private final SafeCloseable mUpdateSubscription;

    // CaretLauncher: an application context for the shortcut queries instead of BgDataModel.
    public PopupDataProvider(
            Context appContext,
            ActivityContext context,
            NotificationRepository notificationRepository,
            AllAppsStore appsStore) {
        mContext = context;
        mNotificationRepo = notificationRepository;
        mAppsStore = appsStore;
        mAppContext = appContext;

        // CaretLauncher: kept for close(); ActivityContext.getUiExecutor is the main executor.
        mUpdateSubscription = mNotificationRepo.getUpdateStream().forEach(
                MAIN_EXECUTOR, this::updateNotificationDots);
    }

    private Unit updateNotificationDots(Predicate<PackageUserKey> updatedDots) {
        final PackageUserKey packageUserKey = new PackageUserKey(null, null);
        Predicate<ItemInfo> matcher = info -> !packageUserKey.updateFromItemInfo(info)
                || updatedDots.test(packageUserKey);

        ItemOperator op = (info, v) -> {
            if (v instanceof BubbleTextView btv && info != null && matcher.test(info)) {
                btv.applyDotState(info, true /* animate */);
            } else if (v instanceof FolderIcon icon
                    && info instanceof FolderInfo fi && fi.anyMatch(matcher)) {
                icon.updateDotInfo();
            }

            // process all the shortcuts
            return false;
        };

        mContext.getContent().mapOverItems(op);
        Folder folder = Folder.getOpen(mContext);
        if (folder != null) {
            folder.mapOverItems(op);
        }
        mAppsStore.updateNotificationDots(updatedDots);
        return null;
    }

    public int getShortcutCountForItem(ItemInfo info) {
        if (!ShortcutUtil.supportsDeepShortcuts(info)) {
            return 0;
        }
        ComponentName component = info.getTargetComponent();
        if (component == null) {
            return 0;
        }

        // CaretLauncher: Launcher3 reads the count from the deep shortcut map that its model loads
        // in the background (published shortcuts per activity). There is no such model here, so
        // the same query runs when the menu opens.
        return new ShortcutRequest(mAppContext, info.user)
                .withContainer(component)
                .query(ShortcutRequest.PUBLISHED)
                .size();
    }

    public @Nullable DotInfo getDotInfoForItem(@NonNull ItemInfo info) {
        if (!ShortcutUtil.supportsShortcuts(info)) {
            return null;
        }
        DotInfo dotInfo = mNotificationRepo.getPackageUserToDotInfos()
                .get(PackageUserKey.fromItemInfo(info));
        if (dotInfo == null) {
            return null;
        }

        // If the item represents a pinned shortcut, ensure that there is a notification
        // for this shortcut
        String shortcutId = ShortcutUtil.getShortcutIdIfPinnedShortcut(info);
        if (shortcutId == null) {
            return dotInfo;
        }
        String[] personKeys = ShortcutUtil.getPersonKeysIfPinnedShortcut(info);
        return (dotInfo.getNotificationKeys().stream().anyMatch(notification -> {
            if (notification.shortcutId != null) {
                return notification.shortcutId.equals(shortcutId);
            }
            if (notification.personKeysFromNotification.length != 0) {
                return Arrays.equals(notification.personKeysFromNotification, personKeys);
            }
            return false;
        })) ? dotInfo : null;
    }

    // CaretLauncher: members CaretLauncher adds.

    /** ActivityContext.closeOnDestroy: stops listening for notification dot updates. */
    @Override
    public void close() {
        mUpdateSubscription.close();
    }
}
