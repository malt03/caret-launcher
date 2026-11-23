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

/**
 * An interface to be used along with a context for various activities in Launcher. This allows a
 * generic class to depend on Context subclass instead of an Activity.
 *
 * <p>Stand-in for Launcher3's ActivityContext (android-17.0.0_r1), made from ActivityContext.java
 * by deleting what TextLauncher does not use; changes are marked with "TextLauncher:".
 */
// TextLauncher: not a SavedStateRegistryOwner; the home screen's Launcher is not an activity.
public interface ActivityContext {

    /** Returns the dagger graph for this UI context */
    // TextLauncher: the ActivityComponent stand-in, not Dagger's; an empty one by default.
    default ActivityComponent getActivityComponent() {
        return new ActivityComponent();
    }

    default boolean finishAutoCancelActionMode() {
        return false;
    }

    /**
     * The root view to support drag-and-drop and popup support.
     */
    BaseDragLayer getDragLayer();

    /**
     * Returns the primary content of this context
     */
    @NonNull
    default LauncherBindableItemsContainer getContent() {
        return op -> null;
    }

    DeviceProfile getDeviceProfile();

    /**
     * Controller for supporting item drag-and-drop
     */
    default DragController getDragController() {
        return null;
    }

    /**
     * Returns {@code true} if popups can use a range of color shades instead of a singular color.
     */
    default boolean canUseMultipleShadesForPopup() {
        return true;
    }

    default View.OnClickListener getItemOnClickListener() {
        return v -> {
            // No op.
        };
    }

    /** Long-click callback used for All Apps items. */
    default View.OnLongClickListener getAllAppsItemLongClickListener() {
        return v -> false;
    }

    default DotInfo getDotInfoForItem(ItemInfo info) {
        return getActivityComponent().getPopupDataProvider().getDotInfoForItem(info);
    }

    /**
     * Hides the keyboard if it is visible
     */
    default void hideKeyboard() {
        // TextLauncher: no KeyboardStateManager; a context with a keyboard to hide overrides this.
    }

    /**
     * Safely starts an activity.
     *
     * @param v View starting the activity.
     * @param intent Base intent being launched.
     * @param item Item associated with the view.
     * @return RunnableList for listening for animation finish if the activity was properly
     *         or started, {@code null} if the launch finished
     */
    default RunnableList startActivitySafely(
            View v, Intent intent, @Nullable ItemInfo item) {
        // TextLauncher: the home screen (Launcher) starts activities; nothing else does.
        return null;
    }

    /** Returns the current ActivityContext as context */
    default Context asContext() {
        return (Context) this;
    }

    /**
     * Returns the ActivityContext associated with the given Context, or throws an exception if
     * the Context is not associated with any ActivityContext.
     */
    // TextLauncher: the home screen's ActivityContext is not a Context; the activity holding it
    // gives it (Provider).
    static ActivityContext lookupContext(Context context) {
        Context c = context;
        while (c instanceof ContextWrapper) {
            if (c instanceof Provider) return ((Provider) c).getActivityContext();
            c = ((ContextWrapper) c).getBaseContext();
        }
        throw new IllegalArgumentException("Cannot find ActivityContext in parent tree");
    }

    // TextLauncher: members TextLauncher adds.

    /**
     * Implemented by the activity that holds an ActivityContext which is not itself a Context
     * (the home screen's Launcher), so {@link #lookupContext} can find it.
     */
    interface Provider {
        ActivityContext getActivityContext();
    }
}
