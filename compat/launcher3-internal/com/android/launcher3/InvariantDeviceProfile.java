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
 * Modified for TextLauncher: a stand-in that keeps only what TextLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Stand-in for Launcher3's InvariantDeviceProfile (android-17.0.0_r1): the home grid's size and its
 * device profile. TextLauncher: the app works the grid out (GridSpec) and sets it with the home
 * screen's one profile; Launcher3 has a profile for each window size the device supports. Until
 * the home screen sets it (the widget picker can be made again before the home screen, after the
 * process ended), the app's {@link GridInitializer} works it out from the device, as Launcher3's
 * constructor does.
 */
public class InvariantDeviceProfile {

    public int numColumns;
    public int numRows;
    public boolean isFixedLandscape = false;

    /** The profiles for the window sizes the device supports; TextLauncher has one. */
    public List<DeviceProfile> supportedProfiles = Collections.emptyList();

    private DeviceProfile mDeviceProfile;

    public DeviceProfile getDeviceProfile(Context context) {
        // TextLauncher: the grid from the device until the home screen sets its own.
        if (mDeviceProfile == null && sGridInitializer != null) {
            sGridInitializer.initGrid(context, this);
        }
        return mDeviceProfile;
    }

    /** TextLauncher: set by the home screen with its grid. */
    public void setDeviceProfile(int numColumns, int numRows, DeviceProfile deviceProfile) {
        this.numColumns = numColumns;
        this.numRows = numRows;
        deviceProfile.inv = this;
        mDeviceProfile = deviceProfile;
        supportedProfiles = Collections.singletonList(deviceProfile);
    }

    @Nullable
    private static GridInitializer sGridInitializer;

    /** TextLauncher: set by the app as it starts. */
    public static void setGridInitializer(@Nullable GridInitializer initializer) {
        sGridInitializer = initializer;
    }

    /**
     * TextLauncher: InvariantDeviceProfile's initGrid; works the grid out for the context's
     * display and sets it ({@link #setDeviceProfile}).
     */
    public interface GridInitializer {
        void initGrid(Context context, InvariantDeviceProfile idp);
    }
}
