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

package com.android.launcher3.views;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.dagger.ActivityComponent;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.LauncherBindableItemsContainer;
import com.android.launcher3.util.RunnableList;

/** Stand-in for Launcher3's ActivityContext. Only what copied code uses. */
public interface ActivityContext {
    DeviceProfile getDeviceProfile();

    BaseDragLayer getDragLayer();

    Context asContext();

    /**
     * Hides the keyboard if it is visible
     */
    void hideKeyboard();

    /** ActivityContext.lookupContext: TextLauncher's activity holds the ActivityContext. */
    static ActivityContext lookupContext(Context context) {
        Context c = context;
        while (c instanceof ContextWrapper) {
            if (c instanceof Provider) return ((Provider) c).getActivityContext();
            c = ((ContextWrapper) c).getBaseContext();
        }
        throw new IllegalArgumentException("Cannot find ActivityContext in parent tree");
    }

    /** TextLauncher: implemented by the activity to give its ActivityContext. */
    interface Provider {
        ActivityContext getActivityContext();
    }

    /** Called when the drag layer sees a new touch (ActivityContext.finishAutoCancelActionMode). */
    default boolean finishAutoCancelActionMode() {
        return false;
    }

    default boolean canUseMultipleShadesForPopup() {
        return true;
    }

    /** Long click listener used for All Apps items. */
    default View.OnLongClickListener getAllAppsItemLongClickListener() {
        return v -> false;
    }

    /**
     * Safely starts an activity. TextLauncher: the home screen starts it (Launcher).
     */
    default RunnableList startActivitySafely(View v, Intent intent, @Nullable ItemInfo item) {
        return null;
    }

    default View.OnClickListener getItemOnClickListener() {
        return v -> {
            // No op.
        };
    }

    /** Launcher3's ActivityContext.getDragController. */
    default DragController getDragController() {
        return null;
    }

    default ActivityComponent getActivityComponent() {
        return new ActivityComponent();
    }

    /**
     * Returns the primary content of this context
     */
    @NonNull
    default LauncherBindableItemsContainer getContent() {
        return op -> null;
    }

    default DotInfo getDotInfoForItem(ItemInfo info) {
        return getActivityComponent().getPopupDataProvider().getDotInfoForItem(info);
    }
}
