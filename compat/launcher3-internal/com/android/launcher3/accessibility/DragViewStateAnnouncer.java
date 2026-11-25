/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.accessibility;

import static com.android.launcher3.compat.AccessibilityManagerCompat.isAccessibilityEnabled;

import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import com.android.launcher3.Launcher;

/**
 * Periodically sends accessibility events to announce ongoing state changed. Based on the
 * implementation in ProgressBar.
 */
/*
 * CaretLauncher: derived from DragViewStateAnnouncer.java (android-17.0.0_r1); changes are marked
 * with "CaretLauncher:".
 */
public class DragViewStateAnnouncer implements Runnable {

    private static final int TIMEOUT_SEND_ACCESSIBILITY_EVENT = 200;

    private final View mTargetView;

    private DragViewStateAnnouncer(View view) {
        mTargetView = view;
    }

    public void announce(CharSequence msg) {
        mTargetView.setContentDescription(msg);
        mTargetView.removeCallbacks(this);
        mTargetView.postDelayed(this, TIMEOUT_SEND_ACCESSIBILITY_EVENT);
    }

    public void cancel() {
        mTargetView.removeCallbacks(this);
    }

    @Override
    public void run() {
        mTargetView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    public void completeAction(int announceResId) {
        cancel();
        Launcher launcher = Launcher.getLauncher(mTargetView.getContext());
        // CaretLauncher: the Launcher stand-in is not a Context.
        launcher.getDragLayer().announceForAccessibility(
                launcher.asContext().getText(announceResId));
    }

    public static DragViewStateAnnouncer createFor(View v) {
        return isAccessibilityEnabled(v.getContext()) ? new DragViewStateAnnouncer(v) : null;
    }
}
