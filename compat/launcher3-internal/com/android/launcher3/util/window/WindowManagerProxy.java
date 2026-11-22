/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.util.window;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.Utilities.dpiFromPx;
import static com.android.launcher3.testing.shared.ResourceUtils.INVALID_RESOURCE_HANDLE;
import static com.android.launcher3.testing.shared.ResourceUtils.NAVBAR_HEIGHT;
import static com.android.launcher3.testing.shared.ResourceUtils.NAVBAR_HEIGHT_LANDSCAPE;
import static com.android.launcher3.testing.shared.ResourceUtils.NAVBAR_LANDSCAPE_LEFT_RIGHT_SIZE;
import static com.android.launcher3.testing.shared.ResourceUtils.STATUS_BAR_HEIGHT;
import static com.android.launcher3.testing.shared.ResourceUtils.STATUS_BAR_HEIGHT_LANDSCAPE;
import static com.android.launcher3.testing.shared.ResourceUtils.STATUS_BAR_HEIGHT_PORTRAIT;
import static com.android.launcher3.util.RotationUtils.deltaRotation;
import static com.android.launcher3.util.RotationUtils.rotateRect;
import static com.android.launcher3.util.RotationUtils.rotateSize;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.util.ArrayMap;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.Surface;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.R;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.util.WindowBounds;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for mocking some window manager behaviours
 *
 * TextLauncher: cut down to the estimate of the displays' window bounds in each rotation
 * (InvariantDeviceProfile's supportedBounds), without Dagger, the taskbar and the window insets
 * normalization. The built-in displays come from DisplayManager, and a large screen's cutout is
 * taken at the top in every rotation (see estimateWindowBounds).
 */
public class WindowManagerProxy {

    public static final int MIN_TABLET_WIDTH = 600;

    // TextLauncher: one instance instead of the Dagger singleton.
    public static final WindowManagerProxy INSTANCE = new WindowManagerProxy();

    protected final boolean mTaskbarDrawnInProcess;

    public WindowManagerProxy() {
        this(false);
    }

    protected WindowManagerProxy(boolean taskbarDrawnInProcess) {
        mTaskbarDrawnInProcess = taskbarDrawnInProcess;
    }

    /**
     * Returns a map of normalized info of internal displays to estimated window bounds
     * for that display
     */
    public ArrayMap<CachedDisplayInfo, List<WindowBounds>> estimateInternalDisplayBounds(
            Context displayInfoContext) {
        CachedDisplayInfo info = getDisplayInfo(displayInfoContext).normalize(this);
        List<WindowBounds> bounds = estimateWindowBounds(displayInfoContext, info);
        ArrayMap<CachedDisplayInfo, List<WindowBounds>> result = new ArrayMap<>();
        result.put(info, bounds);
        // TextLauncher: SystemWindowManagerProxy takes every state of the internal display (both
        // panels of a foldable) from WindowManager.getPossibleMaximumWindowMetrics, which the
        // system answers only for the recents provider. An app gets the built-in displays,
        // including the ones not in use now, from DisplayManager (API 36.1).
        Display current = getDisplay(displayInfoContext);
        if (current.getDisplayId() == DEFAULT_DISPLAY) {
            DisplayManager displayManager =
                    displayInfoContext.getSystemService(DisplayManager.class);
            int densityDpi = displayInfoContext.getResources().getConfiguration().densityDpi;
            for (Display display : displayManager.getDisplays(
                    DisplayManager.DISPLAY_CATEGORY_BUILT_IN_DISPLAYS)) {
                if (display.getDisplayId() == current.getDisplayId()) continue;
                CachedDisplayInfo other = getDisplayInfo(display).normalize(this);
                if (result.containsKey(other) || !canShowHome(other, densityDpi)) continue;
                result.put(other, estimateWindowBounds(displayInfoContext, other));
            }
        }
        return result;
    }

    /**
     * TextLauncher: a built-in display too small for the smallest of Launcher3's display options
     * (device_profiles.xml, "Shorter Stubby": 255dp by 400dp), such as the cover screen of a
     * flip phone, is not a display the home screen is shown on, so it does not shrink the grid.
     */
    private static boolean canShowHome(CachedDisplayInfo info, int densityDpi) {
        float smallest = dpiFromPx(Math.min(info.size.x, info.size.y), densityDpi);
        float largest = dpiFromPx(Math.max(info.size.x, info.size.y), densityDpi);
        return smallest >= 255 && largest >= 400;
    }

    /**
     * TextLauncher: a CachedDisplayInfo for a display that may not be in use (its window metrics
     * are the ones of the display in use), from the display itself.
     */
    @SuppressWarnings("deprecation")
    private static CachedDisplayInfo getDisplayInfo(Display display) {
        Point size = new Point();
        display.getRealSize(size);
        return new CachedDisplayInfo(size, display.getRotation(), display.getCutout());
    }

    /**
     * @see doc at {@link #areBottomDisplayCutoutsSmallAndAtCorners(Rect, int, int)}
     */
    private static boolean areBottomDisplayCutoutsSmallAndAtCorners(
            @NonNull Rect cutoutRectBottom, int screenWidthPx, @NonNull Resources res) {
        return areBottomDisplayCutoutsSmallAndAtCorners(cutoutRectBottom, screenWidthPx,
                res.getDimensionPixelSize(R.dimen.max_width_and_height_of_small_display_cutout));
    }

    /**
     * Return true if bottom display cutouts are at bottom left/right corners, AND has width or
     * height <= maxWidthAndHeightOfSmallCutoutPx. Note that display cutout rect and screenWidthPx
     * passed to this method should be in the SAME screen rotation.
     *
     * @param cutoutRectBottom bottom display cutout rect, this is based on current screen rotation
     * @param screenWidthPx screen width in px based on current screen rotation
     * @param maxWidthAndHeightOfSmallCutoutPx maximum width and height pixels of cutout.
     */
    @VisibleForTesting
    static boolean areBottomDisplayCutoutsSmallAndAtCorners(
            @NonNull Rect cutoutRectBottom, int screenWidthPx,
            int maxWidthAndHeightOfSmallCutoutPx) {
        // Empty cutoutRectBottom means there is no display cutout at the bottom. We should ignore
        // it by returning false.
        if (cutoutRectBottom.isEmpty()) {
            return false;
        }
        return (cutoutRectBottom.right <= maxWidthAndHeightOfSmallCutoutPx)
                || cutoutRectBottom.left >= (screenWidthPx - maxWidthAndHeightOfSmallCutoutPx);
    }

    protected int getStatusBarHeight(Context context, boolean isPortrait, int statusBarInset) {
        Resources systemRes = context.getResources();
        int statusBarHeight = getDimenByName(systemRes,
                isPortrait ? STATUS_BAR_HEIGHT_PORTRAIT : STATUS_BAR_HEIGHT_LANDSCAPE,
                STATUS_BAR_HEIGHT);

        return Math.max(statusBarInset, statusBarHeight);
    }

    /**
     * Returns a list of possible WindowBounds for the display keyed on the 4 surface rotations
     */
    protected List<WindowBounds> estimateWindowBounds(Context context,
            final CachedDisplayInfo displayInfo) {
        int densityDpi = context.getResources().getConfiguration().densityDpi;
        final int rotation = displayInfo.rotation;

        int minSize = Math.min(displayInfo.size.x, displayInfo.size.y);
        int swDp = (int) dpiFromPx(minSize, densityDpi);

        Resources systemRes;
        {
            Configuration conf = new Configuration();
            conf.smallestScreenWidthDp = swDp;
            systemRes = context.createConfigurationContext(conf).getResources();
        }

        boolean isTablet = swDp >= MIN_TABLET_WIDTH;
        boolean isTabletOrGesture = isTablet || isGestureNav(context);

        // Use the status bar height resources because current system API to get the status bar
        // height doesn't allow to do this for an arbitrary display, it returns value only
        // for the current active display (see com.android.internal.policy.StatusBarUtils)
        int statusBarHeightPortrait = getDimenByName(systemRes,
                STATUS_BAR_HEIGHT_PORTRAIT, STATUS_BAR_HEIGHT);
        int statusBarHeightLandscape = getDimenByName(systemRes,
                STATUS_BAR_HEIGHT_LANDSCAPE, STATUS_BAR_HEIGHT);

        int navBarHeightPortrait, navBarHeightLandscape, navbarWidthLandscape;

        navBarHeightPortrait = isTablet
                ? (mTaskbarDrawnInProcess
                        ? 0 : context.getResources().getDimensionPixelSize(R.dimen.taskbar_size))
                : getDimenByName(systemRes, NAVBAR_HEIGHT);

        navBarHeightLandscape = isTablet
                ? (mTaskbarDrawnInProcess
                        ? 0 : context.getResources().getDimensionPixelSize(R.dimen.taskbar_size))
                : (isTabletOrGesture
                        ? getDimenByName(systemRes, NAVBAR_HEIGHT_LANDSCAPE) : 0);
        navbarWidthLandscape = isTabletOrGesture
                ? 0
                : getDimenByName(systemRes, NAVBAR_LANDSCAPE_LEFT_RIGHT_SIZE);

        List<WindowBounds> result = new ArrayList<>(4);
        Point tempSize = new Point();
        for (int i = 0; i < 4; i++) {
            int rotationChange = deltaRotation(rotation, i);
            tempSize.set(displayInfo.size.x, displayInfo.size.y);
            rotateSize(tempSize, rotationChange);
            Rect bounds = new Rect(0, 0, tempSize.x, tempSize.y);

            int navBarHeight, navbarWidth, statusBarHeight;
            if (tempSize.y > tempSize.x) {
                navBarHeight = navBarHeightPortrait;
                navbarWidth = 0;
                statusBarHeight = statusBarHeightPortrait;
            } else {
                navBarHeight = navBarHeightLandscape;
                navbarWidth = navbarWidthLandscape;
                statusBarHeight = statusBarHeightLandscape;
            }

            Rect insets;
            if (isTablet) {
                // TextLauncher: a large screen turns, so the display info may be from any
                // rotation, and rotating the cutout's safe insets (rotateCutout) does not always
                // give where the system puts them in another rotation: a cutout in a corner can
                // stay on the top edge. The estimate would then depend on the rotation the
                // launcher started in, and so would the grid. The cutout's largest safe inset is
                // taken at the top, under the status bar, in every rotation instead, which is the
                // same from any rotation and never larger than the real space.
                Rect safe = getSafeInsets(displayInfo.cutout);
                insets = new Rect(0, Math.max(Math.max(safe.left, safe.top),
                        Math.max(safe.right, safe.bottom)), 0, 0);
            } else {
                DisplayCutout rotatedCutout = rotateCutout(
                        displayInfo.cutout, displayInfo.size.x, displayInfo.size.y, rotation, i);
                insets = getSafeInsets(rotatedCutout);
                if (areBottomDisplayCutoutsSmallAndAtCorners(
                        rotatedCutout.getBoundingRectBottom(),
                        bounds.width(),
                        context.getResources())) {
                    insets.bottom = 0;
                }
            }
            insets.top = Math.max(insets.top, statusBarHeight);
            insets.bottom = Math.max(insets.bottom, navBarHeight);

            if (i == Surface.ROTATION_270 || i == Surface.ROTATION_180) {
                // On reverse landscape (and in rare-case when the natural orientation of the
                // device is landscape), navigation bar is on the right.
                insets.left = Math.max(insets.left, navbarWidth);
            } else {
                insets.right = Math.max(insets.right, navbarWidth);
            }
            result.add(new WindowBounds(bounds, insets, i));
        }
        return result;
    }

    /**
     * Wrapper around the utility method for easier emulation
     */
    protected int getDimenByName(Resources res, String resName) {
        return ResourceUtils.getDimenByName(resName, res, 0);
    }

    /**
     * Wrapper around the utility method for easier emulation
     */
    protected int getDimenByName(Resources res, String resName, String fallback) {
        int dimen = ResourceUtils.getDimenByName(resName, res, -1);
        return dimen > -1 ? dimen : getDimenByName(res, fallback);
    }

    protected boolean isGestureNav(Context context) {
        return ResourceUtils.getIntegerByName("config_navBarInteractionMode",
                context.getResources(), INVALID_RESOURCE_HANDLE) == 2;
    }

    /**
     * Returns a CachedDisplayInfo initialized for the current display
     */
    public CachedDisplayInfo getDisplayInfo(Context displayInfoContext) {
        int rotation = getRotation(displayInfoContext);
        WindowMetrics windowMetrics = displayInfoContext.getSystemService(WindowManager.class)
                .getMaximumWindowMetrics();
        return getDisplayInfo(windowMetrics, rotation);
    }

    /**
     * Returns a CachedDisplayInfo initialized for the current display
     */
    protected CachedDisplayInfo getDisplayInfo(WindowMetrics windowMetrics, int rotation) {
        Point size = new Point(windowMetrics.getBounds().right, windowMetrics.getBounds().bottom);
        return new CachedDisplayInfo(size, rotation,
                windowMetrics.getWindowInsets().getDisplayCutout());
    }

    /**
     * Returns rotation of the display associated with the context, or rotation of DEFAULT_DISPLAY
     * if the context isn't associated with a display.
     */
    public int getRotation(Context displayInfoContext) {
        return getDisplay(displayInfoContext).getRotation();
    }

    /**
     * Returns the display associated with the context, or DEFAULT_DISPLAY if the context isn't
     * associated with a display.
     */
    public Display getDisplay(Context displayInfoContext) {
        try {
            return displayInfoContext.getDisplay();
        } catch (UnsupportedOperationException e) {
            // Ignore
        }
        return displayInfoContext.getSystemService(DisplayManager.class).getDisplay(
                DEFAULT_DISPLAY);
    }

    /**
     * Returns a DisplayCutout which represents a rotated version of the original
     */
    protected DisplayCutout rotateCutout(DisplayCutout original, int startWidth, int startHeight,
            int fromRotation, int toRotation) {
        Rect safeCutout = getSafeInsets(original);
        rotateRect(safeCutout, deltaRotation(fromRotation, toRotation));
        return new DisplayCutout(Insets.of(safeCutout), null, null, null, null);
    }

    /**
     * @see DisplayCutout#getSafeInsets
     */
    public static Rect getSafeInsets(DisplayCutout cutout) {
        return new Rect(cutout.getSafeInsetLeft(), cutout.getSafeInsetTop(),
                cutout.getSafeInsetRight(), cutout.getSafeInsetBottom());
    }
}
