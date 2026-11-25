/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.util;

import android.content.Context;
import android.os.Vibrator;

import com.google.android.msdl.data.model.MSDLToken;
import com.google.android.msdl.domain.InteractionProperties;
import com.google.android.msdl.domain.MSDLPlayer;

/**
 * Wrapper around {@link com.google.android.msdl.domain.MSDLPlayer} to perform MSDL feedback.
 *
 * <p>Stand-in for Launcher3's MSDLPlayerWrapper (android-17.0.0_r1): one player for the
 * application, without Dagger and without the dump of its history.
 */
public class MSDLPlayerWrapper {

    /** DaggerSingletonObject: {@code INSTANCE.get(context)} as in Launcher3. */
    public static final Singleton INSTANCE = new Singleton();

    /** Internal player */
    private final MSDLPlayer mMSDLPlayer;

    public MSDLPlayerWrapper(Context context) {
        Vibrator vibrator = context.getSystemService(Vibrator.class);
        mMSDLPlayer = MSDLPlayer.Companion.createPlayer(vibrator,
                java.util.concurrent.Executors.newSingleThreadExecutor(),
                null /* useHapticFeedbackForToken */);
    }

    /** Perform MSDL feedback for a token with interaction properties */
    public void playToken(MSDLToken token, InteractionProperties properties) {
        mMSDLPlayer.playToken(token, properties);
    }

    /** Perform MSDL feedback for a token without properties */
    public void playToken(MSDLToken token) {
        mMSDLPlayer.playToken(token, null);
    }

    /** Holds the application's one player (LauncherAppSingleton). */
    public static final class Singleton {
        private MSDLPlayerWrapper mInstance;

        public MSDLPlayerWrapper get(Context context) {
            if (mInstance == null) {
                mInstance = new MSDLPlayerWrapper(context.getApplicationContext());
            }
            return mInstance;
        }
    }
}
