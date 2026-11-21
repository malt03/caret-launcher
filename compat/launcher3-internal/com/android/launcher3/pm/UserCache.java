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
import android.os.UserHandle;
import android.os.UserManager;

/**
 * Class which manages a local cache of user handles to avoid system rpc
 *
 * <p>Stand-in for Launcher3's UserCache (android-17.0.0_r1): only getSerialNumberForUser, asked of
 * UserManager directly (no cache, no Dagger).
 */
public class UserCache {
    public static final Instance INSTANCE = new Instance();

    private final UserManager mUserManager;

    private UserCache(Context context) {
        mUserManager = context.getSystemService(UserManager.class);
    }

    /** @see UserManager#getSerialNumberForUser */
    public long getSerialNumberForUser(UserHandle user) {
        return mUserManager.getSerialNumberForUser(user);
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
