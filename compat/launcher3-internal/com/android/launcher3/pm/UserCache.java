/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.launcher3.pm;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherUserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.UserIconInfo;
import com.android.users.UserType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Class which manages a local cache of user handles to avoid system rpc
 *
 * <p>Stand-in for Launcher3's UserCache (android-17.0.0_r1): the serial numbers, profiles and
 * their types and quiet mode for the widget picker, asked of UserManager directly (no cache, no
 * Dagger), and the changes to the profiles, from the same broadcasts as UserCache.kt.
 */
public class UserCache {
    private static final String TAG = "UserCache";

    public static final Instance INSTANCE = new Instance();

    private final UserManager mUserManager;
    private final LauncherApps mLauncherApps;

    /**
     * CaretLauncher: the users as of the last change, to tell what a broadcast changed
     * (UserCache.kt's cached UserManagerState).
     */
    private Map<UserHandle, CachedUserInfo> mUserInfoMap;

    private UserCache(Context context) {
        mUserManager = context.getSystemService(UserManager.class);
        mLauncherApps = context.getSystemService(LauncherApps.class);

        // UserCache.kt: the receiver of the profile changes, on a lightweight background thread.
        // CaretLauncher: UI_HELPER_EXECUTOR's, and the cache is built there first.
        UI_HELPER_EXECUTOR.execute(() -> mUserInfoMap = rebuildUserCache());
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_ADDED);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED);
        filter.addAction(Intent.ACTION_PROFILE_ACCESSIBLE);
        filter.addAction(Intent.ACTION_PROFILE_ADDED);
        filter.addAction(Intent.ACTION_PROFILE_AVAILABLE);
        filter.addAction(Intent.ACTION_PROFILE_INACCESSIBLE);
        filter.addAction(Intent.ACTION_PROFILE_REMOVED);
        filter.addAction(Intent.ACTION_PROFILE_UNAVAILABLE);
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                onUsersChanged(intent);
            }
        }, filter, null, UI_HELPER_EXECUTOR.getHandler());
    }

    /** UserCache.onUsersChanged */
    @WorkerThread
    private void onUsersChanged(Intent intent) {
        Map<UserHandle, CachedUserInfo> oldState = mUserInfoMap;
        mUserInfoMap = rebuildUserCache();
        UserHandle user = intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class);
        if (user == null) return;
        UserChangeEvent change = new UserChangeEvent(
                oldState != null ? oldState.get(user) : null, mUserInfoMap.get(user));
        if (!Objects.equals(change.getOldUser(), change.getNewUser())) {
            mUserChanges.dispatchValue(change);
        }
    }

    /** UserCache.rebuildUserCache: the profiles and their state (buildCachedUserInfo). */
    @WorkerThread
    private Map<UserHandle, CachedUserInfo> rebuildUserCache() {
        Map<UserHandle, CachedUserInfo> map = new HashMap<>();
        try {
            for (UserHandle user : getUserProfiles()) {
                if (mLauncherApps.getLauncherUserInfo(user) == null) continue;
                map.put(user, new CachedUserInfo(getUserInfo(user),
                        mUserManager.isUserUnlocked(user),
                        mUserManager.isQuietModeEnabled(user)));
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception while fetching user property", e);
        }
        return map;
    }

    /** @see UserManager#getSerialNumberForUser */
    public long getSerialNumberForUser(UserHandle user) {
        return mUserManager.getSerialNumberForUser(user);
    }

    /** @see UserManager#getUserForSerialNumber */
    public UserHandle getUserForSerialNumber(long serialNumber) {
        return mUserManager.getUserForSerialNumber(serialNumber);
    }

    /** @see UserManager#getUserProfiles */
    public List<UserHandle> getUserProfiles() {
        return mUserManager.getUserProfiles();
    }

    /**
     * The user's type and serial number. CaretLauncher: only main and work profiles are told
     * apart (from LauncherApps.getLauncherUserInfo, as ApiWrapper.getUserInfo does).
     */
    public UserIconInfo getUserInfo(UserHandle user) {
        LauncherUserInfo info = mLauncherApps.getLauncherUserInfo(user);
        boolean isWork = info != null
                && UserManager.USER_TYPE_PROFILE_MANAGED.equals(info.getUserType());
        return new UserIconInfo(user, isWork ? UserType.WORK : UserType.MAIN,
                getSerialNumberForUser(user));
    }

    /** UserManagerState: whether a profile is paused (quiet mode). */
    public UserManagerState getUserManagerState() {
        return mUserManagerState;
    }

    /** Stream for listening to user manager changes */
    public UserChanges getUserChanges() {
        return mUserChanges;
    }

    private final UserManagerState mUserManagerState = new UserManagerState();
    private final UserChanges mUserChanges = new UserChanges();

    /** Stand-in for UserManagerState. */
    public class UserManagerState {
        public boolean isUserQuiet(UserHandle user) {
            return mUserManager.isQuietModeEnabled(user);
        }

        /** UserManagerState.getUser: the user of a serial number (LoaderCursor). */
        public UserHandle getUser(long serialNo) {
            return mUserManager.getUserForSerialNumber(serialNo);
        }

        /** UserManagerState.isUserUnlocked */
        public boolean isUserUnlocked(UserHandle user) {
            return mUserManager.isUserUnlocked(user);
        }
    }

    /**
     * Stand-in for the listenable stream of {@link UserChangeEvent} (MutableListenableStream):
     * each listener gets the changes on its executor until it is closed.
     */
    public static class UserChanges {
        private final CopyOnWriteArrayList<Consumer<UserChangeEvent>> mListeners =
                new CopyOnWriteArrayList<>();

        public SafeCloseable forEach(Executor executor, Consumer<UserChangeEvent> listener) {
            Consumer<UserChangeEvent> entry = event -> executor.execute(() -> listener.accept(event));
            mListeners.add(entry);
            return () -> mListeners.remove(entry);
        }

        void dispatchValue(UserChangeEvent event) {
            for (Consumer<UserChangeEvent> listener : mListeners) {
                listener.accept(event);
            }
        }
    }

    /**
     * UserCache.CachedUserInfo: information about a UserHandle cached in the platform.
     * CaretLauncher: no pre-installed apps.
     */
    public static class CachedUserInfo {
        private final UserIconInfo mIconInfo;
        private final boolean mIsUnlocked;
        private final boolean mIsQuietModeEnabled;

        public CachedUserInfo(UserIconInfo iconInfo, boolean isUnlocked,
                boolean isQuietModeEnabled) {
            mIconInfo = iconInfo;
            mIsUnlocked = isUnlocked;
            mIsQuietModeEnabled = isQuietModeEnabled;
        }

        public UserIconInfo getIconInfo() {
            return mIconInfo;
        }

        public boolean isUnlocked() {
            return mIsUnlocked;
        }

        public boolean isQuietModeEnabled() {
            return mIsQuietModeEnabled;
        }

        // CaretLauncher: a Kotlin data class upstream.
        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (!(o instanceof CachedUserInfo other)) return false;
            return mIsUnlocked == other.mIsUnlocked
                    && mIsQuietModeEnabled == other.mIsQuietModeEnabled
                    && Objects.equals(mIconInfo, other.mIconInfo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mIconInfo, mIsUnlocked, mIsQuietModeEnabled);
        }
    }

    /** UserCache.UserChangeEvent */
    public static class UserChangeEvent {
        private final CachedUserInfo mOldUser;
        private final CachedUserInfo mNewUser;

        public UserChangeEvent(CachedUserInfo oldUser, CachedUserInfo newUser) {
            mOldUser = oldUser;
            mNewUser = newUser;
        }

        public CachedUserInfo getOldUser() {
            return mOldUser;
        }

        public CachedUserInfo getNewUser() {
            return mNewUser;
        }
    }

    /** CaretLauncher: in place of Dagger, one UserCache for the app. */
    public static class Instance {
        private UserCache mCache;

        public synchronized UserCache get(Context context) {
            if (mCache == null) mCache = new UserCache(context.getApplicationContext());
            return mCache;
        }
    }
}
