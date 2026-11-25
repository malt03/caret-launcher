/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stand-in for Launcher3's ScreenOnTracker (android-17.0.0_r1): tells listeners when the screen
 * turns on or off. CaretLauncher: one per process, registered while it has listeners.
 */
public class ScreenOnTracker {

    public static final ScreenOnTrackerHolder INSTANCE = new ScreenOnTrackerHolder();

    private final Context mContext;
    private final CopyOnWriteArrayList<ScreenOnListener> mListeners = new CopyOnWriteArrayList<>();
    private boolean mIsScreenOn = true;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mIsScreenOn = true;
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mIsScreenOn = false;
            } else {
                return;
            }
            for (ScreenOnListener listener : mListeners) {
                listener.onScreenOnChanged(mIsScreenOn);
            }
        }
    };

    ScreenOnTracker(Context context) {
        mContext = context.getApplicationContext();
    }

    public void addListener(ScreenOnListener listener) {
        if (mListeners.isEmpty()) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            mContext.registerReceiver(mReceiver, filter);
        }
        mListeners.add(listener);
    }

    public void removeListener(ScreenOnListener listener) {
        mListeners.remove(listener);
        if (mListeners.isEmpty()) {
            mContext.unregisterReceiver(mReceiver);
        }
    }

    public boolean isScreenOn() {
        return mIsScreenOn;
    }

    /** Interface for listening to screen on changes */
    public interface ScreenOnListener {
        void onScreenOnChanged(boolean isOn);
    }

    /** CaretLauncher: stands in for DaggerSingletonObject. */
    public static final class ScreenOnTrackerHolder {
        private ScreenOnTracker mInstance;

        public synchronized ScreenOnTracker get(Context context) {
            if (mInstance == null) mInstance = new ScreenOnTracker(context);
            return mInstance;
        }
    }
}
