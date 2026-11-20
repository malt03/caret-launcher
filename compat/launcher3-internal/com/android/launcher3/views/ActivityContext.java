package com.android.launcher3.views;

import com.android.launcher3.DeviceProfile;

/** Stand-in for Launcher3's ActivityContext. Only what the copied folder code uses. */
public interface ActivityContext {
    DeviceProfile getDeviceProfile();

    BaseDragLayer getDragLayer();
}
