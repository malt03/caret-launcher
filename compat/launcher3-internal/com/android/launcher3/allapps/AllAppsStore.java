/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.allapps;

import static com.android.launcher3.model.data.AppInfo.COMPONENT_KEY_COMPARATOR;
import static com.android.launcher3.model.data.AppInfo.EMPTY_ARRAY;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.ComponentKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A utility class to maintain the collection of all apps.
 *
 * <p>Stand-in for Launcher3's AllAppsStore (android-17.0.0_r1): the apps and their update
 * listeners. TextLauncher: the app sets the apps (AppsListRepository in Launcher3); no deferred
 * updates, uids, model flags, notification dots or download progress.
 */
public class AllAppsStore {

    private static final String TAG = "AllAppsStore";

    private AppInfo mTempInfo = new AppInfo();

    private @NonNull AppInfo[] mApps = EMPTY_ARRAY;

    private final List<OnUpdateListener> mUpdateListeners = new CopyOnWriteArrayList<>();
    private final ArrayList<ViewGroup> mIconContainers = new ArrayList<>();
    private PackageUserKey mTempKey = new PackageUserKey(null, null);

    public AppInfo[] getApps() {
        return mApps;
    }

    /**
     * Sets the current set of apps.
     *
     * <p>Param: apps are required to be sorted using the comparator COMPONENT_KEY_COMPARATOR
     * in order to enable binary search on the mApps store
     */
    public void setApps(@Nullable AppInfo[] apps) {
        mApps = apps == null ? EMPTY_ARRAY : apps;
        Log.d(TAG, "setApps: apps.length=" + mApps.length);
        notifyUpdate();
    }

    /**
     * Returns {@link AppInfo} if any apps matches with provided {@link ComponentKey}, otherwise
     * null.
     *
     * Uses {@link AppInfo#COMPONENT_KEY_COMPARATOR} as a default comparator.
     */
    @Nullable
    public AppInfo getApp(ComponentKey key) {
        return getApp(key, COMPONENT_KEY_COMPARATOR);
    }

    /**
     * Generic version of {@link #getApp(ComponentKey)} that allows comparator to be specified.
     */
    @Nullable
    public AppInfo getApp(ComponentKey key, Comparator<AppInfo> comparator) {
        mTempInfo.componentName = key.componentName;
        mTempInfo.user = key.user;
        int index = Arrays.binarySearch(mApps, mTempInfo, comparator);
        return index < 0 ? null : mApps[index];
    }

    private void notifyUpdate() {
        Log.d(TAG, "notifyUpdate: notifying listeners");
        for (OnUpdateListener listener : mUpdateListeners) {
            listener.onAppsUpdated();
        }
    }

    public void registerIconContainer(ViewGroup container) {
        if (container != null && !mIconContainers.contains(container)) {
            mIconContainers.add(container);
        }
    }

    public void unregisterIconContainer(ViewGroup container) {
        mIconContainers.remove(container);
    }

    public void updateNotificationDots(Predicate<PackageUserKey> updatedDots) {
        updateAllIcons((child) -> {
            if (child.getTag() instanceof ItemInfo) {
                ItemInfo info = (ItemInfo) child.getTag();
                if (mTempKey.updateFromItemInfo(info) && updatedDots.test(mTempKey)) {
                    child.applyDotState(info, true /* animate */);
                }
            }
        });
    }

    private void updateAllIcons(Consumer<BubbleTextView> action) {
        for (int i = mIconContainers.size() - 1; i >= 0; i--) {
            ViewGroup parent = mIconContainers.get(i);
            int childCount = parent.getChildCount();

            for (int j = 0; j < childCount; j++) {
                View child = parent.getChildAt(j);
                if (child instanceof BubbleTextView) {
                    action.accept((BubbleTextView) child);
                }
            }
        }
    }

    public void addUpdateListener(OnUpdateListener listener) {
        mUpdateListeners.add(listener);
    }

    public void removeUpdateListener(OnUpdateListener listener) {
        mUpdateListeners.remove(listener);
    }

    public interface OnUpdateListener {
        void onAppsUpdated();
    }
}
