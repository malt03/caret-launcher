package com.android.launcher3.views;

import android.content.Context;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.dagger.ActivityComponent;

/** Stand-in for Launcher3's ActivityContext. Only what copied code uses. */
public interface ActivityContext {
    DeviceProfile getDeviceProfile();

    BaseDragLayer getDragLayer();

    Context asContext();

    /** Hides the keyboard when a drag starts (docs/spec.md 5.4: edit mode). */
    void hideKeyboard();

    /** ActivityContext.lookupContext: TextLauncher's activity holds the ActivityContext. */
    static ActivityContext lookupContext(Context context) {
        Context c = context;
        while (c instanceof android.content.ContextWrapper) {
            if (c instanceof Provider) return ((Provider) c).getActivityContext();
            c = ((android.content.ContextWrapper) c).getBaseContext();
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

    default android.view.View.OnClickListener getItemOnClickListener() {
        return v -> {
            // No op.
        };
    }

    /** Launcher3's ActivityContext.getDragController. */
    default com.android.launcher3.dragndrop.DragController getDragController() {
        return null;
    }

    default ActivityComponent getActivityComponent() {
        return new ActivityComponent();
    }
}
