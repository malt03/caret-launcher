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
 * Modified for TextLauncher: a stand-in that keeps only what TextLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */
package com.android.launcher3.pm;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherUserInfo;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.UserIconInfo;
import com.android.users.UserType;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Class which manages a local cache of user handles to avoid system rpc
 *
 * <p>Stand-in for Launcher3's UserCache (android-17.0.0_r1): the serial numbers, profiles and
 * their types and quiet mode for the widget picker, asked of UserManager directly (no cache, no
 * Dagger).
 */
public class UserCache {
    public static final Instance INSTANCE = new Instance();

    private final UserManager mUserManager;
    private final LauncherApps mLauncherApps;

    private UserCache(Context context) {
        mUserManager = context.getSystemService(UserManager.class);
        mLauncherApps = context.getSystemService(LauncherApps.class);
    }

    /** @see UserManager#getSerialNumberForUser */
    public long getSerialNumberForUser(UserHandle user) {
        return mUserManager.getSerialNumberForUser(user);
    }

    /** @see UserManager#getUserProfiles */
    public List<UserHandle> getUserProfiles() {
        return mUserManager.getUserProfiles();
    }

    /**
     * The user's type and serial number. TextLauncher: only main and work profiles are told
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

    /**
     * Changes to the users. TextLauncher: they are not tracked yet, so the listeners are not
     * called; the widget picker reads the profiles when it opens.
     */
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
    }

    /** Stand-in for the listenable stream of {@link UserChangeEvent}. */
    public static class UserChanges {
        public SafeCloseable forEach(Executor executor, Consumer<UserChangeEvent> listener) {
            return () -> { };
        }
    }

    /** UserCache.CachedUserInfo */
    public static class CachedUserInfo {
        private final UserIconInfo mIconInfo;

        public CachedUserInfo(UserIconInfo iconInfo) {
            mIconInfo = iconInfo;
        }

        public UserIconInfo getIconInfo() {
            return mIconInfo;
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

    /** TextLauncher: in place of Dagger, one UserCache for the app. */
    public static class Instance {
        private UserCache mCache;

        public synchronized UserCache get(Context context) {
            if (mCache == null) mCache = new UserCache(context.getApplicationContext());
            return mCache;
        }
    }
}
