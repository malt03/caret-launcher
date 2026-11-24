/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.launcher3.Utilities.dpiFromPx;
import static com.android.launcher3.deviceprofile.parser.DeviceTypedMap.COUNT_SIZES;
import static com.android.launcher3.testing.shared.ResourceUtils.INVALID_RESOURCE_HANDLE;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import androidx.annotation.DimenRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.annotation.XmlRes;

import com.android.launcher3.deviceprofile.parser.DisplayOption;
import com.android.launcher3.deviceprofile.parser.GridOption;
import com.android.launcher3.display.LauncherDisplayInfo;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.util.WindowBounds;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Stand-in for Launcher3's InvariantDeviceProfile (android-17.0.0_r1), made from
 * InvariantDeviceProfile.java by deleting Dagger, the preferences (the grid name, the fixed
 * landscape mode, the two line all apps labels), the change listeners, the partner overrides and
 * the device profiles of the supported bounds; changes are marked with "TextLauncher:". The app
 * works its grid out with {@link #initGrid} (GridSpec) and sets the home screen's one profile
 * ({@link #setDeviceProfile}).
 */
public class InvariantDeviceProfile {

    public static final String TAG = "IDP";
    // TextLauncher: no Dagger singleton (LauncherAppState.getIDP).

    public static final String GRID_NAME_PREFS_KEY = "idp_grid_name";
    public static final String NON_FIXED_LANDSCAPE_GRID_NAME_PREFS_KEY =
            "idp_non_fixed_landscape_grid_name";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_PHONE, TYPE_MULTI_DISPLAY, TYPE_TABLET, TYPE_DESKTOP})
    public @interface DeviceType {
    }

    public static final int TYPE_PHONE = 0;
    public static final int TYPE_MULTI_DISPLAY = 1;
    public static final int TYPE_TABLET = 2;
    public static final int TYPE_DESKTOP = 3;

    private static final float ICON_SIZE_DEFINED_IN_APP_DP = 48;

    // Constants that affects the interpolation curve between statically defined device profile
    // buckets.
    private static final float KNEARESTNEIGHBOR = 3;
    private static final float WEIGHT_POWER = 5;

    // used to offset float not being able to express extremely small weights in extreme cases.
    private static final float WEIGHT_EFFICIENT = 100000f;

    // TextLauncher: no partner overrides (RES_GRID_*), DisplayController, preferences or theme.

    /**
     * Number of icons per row and column in the workspace.
     */
    public int numRows;
    public int numColumns;
    public int numSearchContainerColumns;

    /**
     * Number of icons per row and column in the folder.
     */
    public int[] numFolderRows;
    public int[] numFolderColumns;
    public float[] iconSize;
    public float[] iconTextSize;
    public int iconBitmapSize;
    public int fillResIconDpi;
    public @DeviceType int deviceType;
    public LauncherDisplayInfo displayInfo;

    public PointF[] minCellSize;

    public PointF[] borderSpaces;
    public @DimenRes int inlineNavButtonsEndSpacing;

    public @StyleRes int folderStyle;

    public @StyleRes int cellStyle;

    public float[] horizontalMargin;

    public PointF[] allAppsCellSize;
    public float[] allAppsIconSize;
    public float[] allAppsIconTextSize;
    public PointF[] allAppsBorderSpaces;

    public float[] transientTaskbarIconSize;

    public boolean[] startAlignTaskbar;

    /**
     * Number of icons inside the hotseat area.
     */
    public int numShownHotseatIcons;

    /**
     * Number of icons inside the hotseat area that is stored in the database. This is greater than
     * or equal to numnShownHotseatIcons, allowing for a seamless transition between two hotseat
     * sizes that share the same DB.
     */
    public int numDatabaseHotseatIcons;

    public float[] hotseatBarBottomSpace;
    public float[] hotseatQsbSpace;

    /**
     * Number of columns in the all apps list.
     */
    public int numAllAppsColumns;
    public int numAllAppsRowsForCellHeightCalculation;
    public int numDatabaseAllAppsColumns;
    public @StyleRes int allAppsStyle;

    /**
     * Do not query directly. see {@link DeviceProfile#isScalableGrid}.
     */
    protected boolean isScalable;
    @XmlRes
    public int devicePaddingId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int workspaceSpecsId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int workspaceSpecsTwoPanelId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int allAppsSpecsId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int allAppsSpecsTwoPanelId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int folderSpecsId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int folderSpecsTwoPanelId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int hotseatSpecsId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int hotseatSpecsTwoPanelId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int workspaceCellSpecsId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int workspaceCellSpecsTwoPanelId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int allAppsCellSpecsId = INVALID_RESOURCE_HANDLE;
    @XmlRes
    public int allAppsCellSpecsTwoPanelId = INVALID_RESOURCE_HANDLE;

    private String mLocale = "";
    public boolean enableTwoLinesInAllApps = false;

    // If non-negative, the workspace row with which top of the all apps container is to be aligned
    // with.
    public int appListAlignedWithWorkspaceRow = -1;

    /**
     * Fixed landscape mode is the landscape on the phones.
     */
    public boolean isFixedLandscape = false;

    @GridType
    public int gridType;
    public String dbFile;
    public int defaultLayoutId;
    public boolean[] inlineQsb = new boolean[COUNT_SIZES];

    /**
     * An immutable list of supported profiles.
     */
    public List<DeviceProfile> supportedProfiles = Collections.emptyList();

    public Point defaultWallpaperSize;

    // TextLauncher: no change listeners (mChangeListeners) or taskbar (taskbarModeUtil).

    // TextLauncher: no Dagger; the app inits the grid (initGrid).
    public InvariantDeviceProfile() {
    }

    /**
     * TextLauncher: initGrid(String) with the display info and the columns of the grid the items
     * are in (DeviceGridState) from the app, and no grid name (GRID_NAME), fixed landscape mode or
     * logs.
     */
    public void initGrid(LauncherDisplayInfo displayInfo, int gridColumns) {
        List<DisplayOption> allOptions = getPredefinedDeviceProfiles(
                displayInfo,
                /* gridName= */ null,
                /* isFixedLandscapeMode= */ false
        );

        // Filter out options that don't have the same number of columns as the grid
        List<DisplayOption> allOptionsFilteredByColCount =
                filterByColumnCount(allOptions, gridColumns);

        DisplayOption displayOption =
                invDistWeightedInterpolate(displayInfo, allOptionsFilteredByColCount.isEmpty()
                                ? new ArrayList<>(allOptions)
                                : new ArrayList<>(allOptionsFilteredByColCount),
                        displayInfo.getDeviceType());

        initGridForDisplayOption(displayInfo, displayOption);
    }

    private List<DisplayOption> filterByColumnCount(
            List<DisplayOption> allOptions, int numColumns) {
        return allOptions.stream()
                .filter(option -> option.grid.numColumns == numColumns)
                .collect(Collectors.toList());
    }

    // TextLauncher: no backup and restore (reset).

    private void initGridForDisplayOption(
            LauncherDisplayInfo displayInfo, DisplayOption displayOption) {
        Context context = displayInfo.context;
        // TextLauncher: no ENABLE_TWOLINE_ALLAPPS_TOGGLE preference (enableTwoLinesInAllApps).
        mLocale = context.getResources().getConfiguration().locale.toString();

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        GridOption closestProfile = displayOption.grid;
        numRows = closestProfile.numRows;
        numColumns = closestProfile.numColumns;
        numSearchContainerColumns = closestProfile.numSearchContainerColumns;
        dbFile = closestProfile.dbFile;
        gridType = closestProfile.gridType;
        defaultLayoutId = closestProfile.defaultLayoutId;

        numFolderRows = closestProfile.numFolderRows;
        numFolderColumns = closestProfile.numFolderColumns;
        folderStyle = closestProfile.folderStyle;

        cellStyle = closestProfile.cellStyle;

        isScalable = closestProfile.isScalable;
        devicePaddingId = closestProfile.devicePaddingId;
        workspaceSpecsId = closestProfile.workspaceSpecsId;
        workspaceSpecsTwoPanelId = closestProfile.workspaceSpecsTwoPanelId;
        allAppsSpecsId = closestProfile.allAppsSpecsId;
        allAppsSpecsTwoPanelId = closestProfile.allAppsSpecsTwoPanelId;
        folderSpecsId = closestProfile.folderSpecsId;
        folderSpecsTwoPanelId = closestProfile.folderSpecsTwoPanelId;
        hotseatSpecsId = closestProfile.hotseatSpecsId;
        hotseatSpecsTwoPanelId = closestProfile.hotseatSpecsTwoPanelId;
        workspaceCellSpecsId = closestProfile.workspaceCellSpecsId;
        workspaceCellSpecsTwoPanelId = closestProfile.workspaceCellSpecsTwoPanelId;
        allAppsCellSpecsId = closestProfile.allAppsCellSpecsId;
        allAppsCellSpecsTwoPanelId = closestProfile.allAppsCellSpecsTwoPanelId;
        numAllAppsRowsForCellHeightCalculation =
                closestProfile.numAllAppsRowsForCellHeightCalculation;
        appListAlignedWithWorkspaceRow = closestProfile.allAppsAlignedWithWorkspaceRow;
        this.deviceType = displayInfo.getDeviceType();
        this.displayInfo = displayInfo;

        inlineNavButtonsEndSpacing = closestProfile.inlineNavButtonsEndSpacing;

        iconSize = displayOption.iconSizes;
        float maxIconSize = iconSize[0];
        for (int i = 1; i < iconSize.length; i++) {
            maxIconSize = Math.max(maxIconSize, iconSize[i]);
        }
        iconBitmapSize = ResourceUtils.pxFromDp(maxIconSize, metrics);

        fillResIconDpi = getLauncherIconDensity(iconBitmapSize);

        iconTextSize = displayOption.textSizes;

        minCellSize = displayOption.minCellSize;

        borderSpaces = displayOption.borderSpaces;

        horizontalMargin = displayOption.horizontalMargin;

        numShownHotseatIcons = closestProfile.numHotseatIcons;
        numDatabaseHotseatIcons = deviceType == TYPE_MULTI_DISPLAY || deviceType == TYPE_DESKTOP
                ? closestProfile.numDatabaseHotseatIcons : closestProfile.numHotseatIcons;

        hotseatBarBottomSpace = displayOption.hotseatBarBottomSpace;
        hotseatQsbSpace = displayOption.hotseatQsbSpace;

        allAppsStyle = closestProfile.allAppsStyle;

        numAllAppsColumns = closestProfile.numAllAppsColumns;

        numDatabaseAllAppsColumns = deviceType == TYPE_MULTI_DISPLAY
                ? closestProfile.numDatabaseAllAppsColumns : closestProfile.numAllAppsColumns;

        allAppsCellSize = displayOption.allAppsCellSize;
        allAppsBorderSpaces = displayOption.allAppsBorderSpaces;
        allAppsIconSize = displayOption.allAppsIconSizes;
        allAppsIconTextSize = displayOption.allAppsIconTextSizes;

        inlineQsb = closestProfile.inlineQsb;

        transientTaskbarIconSize = displayOption.transientTaskbarIconSize;

        startAlignTaskbar = displayOption.startAlignTaskbar;

        // Fixed Landscape mode
        isFixedLandscape = closestProfile.isFixedLandscape;

        // TextLauncher: no partner overrides (applyPartnerDeviceProfileOverrides).

        // TextLauncher: no device profiles of the supported bounds; the home screen sets its one
        // profile (setDeviceProfile).
        defaultWallpaperSize = new Point(displayInfo.currentSize);
        for (WindowBounds bounds : displayInfo.supportedBounds) {

            // Wallpaper size should be the maximum of the all possible sizes Launcher expects
            int displayWidth = bounds.bounds.width();
            int displayHeight = bounds.bounds.height();
            defaultWallpaperSize.y = Math.max(defaultWallpaperSize.y, displayHeight);

            // We need to ensure that there is enough extra space in the wallpaper
            // for the intended parallax effects
            float parallaxFactor =
                    dpiFromPx(Math.min(displayWidth, displayHeight), displayInfo.getDensityDpi())
                            < 720
                            ? 2
                            : wallpaperTravelToScreenWidthRatio(displayWidth, displayHeight);
            defaultWallpaperSize.x =
                    Math.max(defaultWallpaperSize.x, Math.round(parallaxFactor * displayWidth));
        }
    }

    // TextLauncher: no DeviceProfile.Builder (newDPBuilder), change listeners
    // (add/removeOnChangeListener, setCurrentGrid, toModelState, onConfigChanged).

    private static List<DisplayOption> getPredefinedDeviceProfiles(
            @NonNull LauncherDisplayInfo displayInfo,
            @Nullable String gridName,
            boolean isFixedLandscapeMode
    ) {
        List<DisplayOption> profiles = DisplayOption.getPredefinedDisplayOptions(
                displayInfo, isFixedLandscapeMode);

        ArrayList<DisplayOption> filteredProfiles = new ArrayList<>();
        if (!TextUtils.isEmpty(gridName)) {
            for (DisplayOption option : profiles) {
                if (gridName.equals(option.grid.name)
                        && (option.grid.isEnabled(displayInfo.getDeviceType()))) {
                    filteredProfiles.add(option);
                }
            }
        }
        if (filteredProfiles.isEmpty() && TextUtils.isEmpty(gridName)) {
            // Use the default options since gridName is empty and there's no valid grids.
            for (DisplayOption option : profiles) {
                if (option.canBeDefault) {
                    filteredProfiles.add(option);
                }
            }
        } else if (filteredProfiles.isEmpty()) {
            // In this case we had a grid selected but we couldn't find it.
            filteredProfiles.addAll(profiles);
        }
        if (filteredProfiles.isEmpty()) {
            throw new RuntimeException("No display option with canBeDefault=true");
        }
        return filteredProfiles;
    }

    /**
     * @return all the grid options that can be shown on the device
     */
    public List<GridOption> parseAllGridOptions(Context context) {
        return GridOption.parseAllValid(context, displayInfo, deviceType, isFixedLandscape);
    }

    private int getLauncherIconDensity(int requiredSize) {
        // Densities typically defined by an app.
        int[] densityBuckets = new int[]{
                DisplayMetrics.DENSITY_LOW,
                DisplayMetrics.DENSITY_MEDIUM,
                DisplayMetrics.DENSITY_TV,
                DisplayMetrics.DENSITY_HIGH,
                DisplayMetrics.DENSITY_XHIGH,
                DisplayMetrics.DENSITY_XXHIGH,
                DisplayMetrics.DENSITY_XXXHIGH
        };

        int density = DisplayMetrics.DENSITY_XXXHIGH;
        for (int i = densityBuckets.length - 1; i >= 0; i--) {
            float expectedSize = ICON_SIZE_DEFINED_IN_APP_DP * densityBuckets[i]
                    / DisplayMetrics.DENSITY_DEFAULT;
            if (expectedSize >= requiredSize) {
                density = densityBuckets[i];
            }
        }

        return density;
    }

    // TextLauncher: no partner overrides (applyPartnerDeviceProfileOverrides).

    private static float dist(float x0, float y0, float x1, float y1) {
        return (float) Math.hypot(x1 - x0, y1 - y0);
    }

    private static DisplayOption invDistWeightedInterpolate(
            LauncherDisplayInfo displayInfo, List<DisplayOption> points,
            @DeviceType int deviceType) {
        int minWidthPx = Integer.MAX_VALUE;
        int minHeightPx = Integer.MAX_VALUE;
        for (WindowBounds bounds : displayInfo.supportedBounds) {
            boolean isLargeScreen = displayInfo.isLargeScreen(bounds);
            if (isLargeScreen && deviceType == TYPE_MULTI_DISPLAY) {
                // For split displays, take half width per page
                minWidthPx = Math.min(minWidthPx, bounds.availableSize.x / 2);
                minHeightPx = Math.min(minHeightPx, bounds.availableSize.y);

            } else if (!isLargeScreen && bounds.isLandscape()) {
                // We will use transposed layout in this case
                minWidthPx = Math.min(minWidthPx, bounds.availableSize.y);
                minHeightPx = Math.min(minHeightPx, bounds.availableSize.x);
            } else {
                minWidthPx = Math.min(minWidthPx, bounds.availableSize.x);
                minHeightPx = Math.min(minHeightPx, bounds.availableSize.y);
            }
        }

        float width = dpiFromPx(minWidthPx, displayInfo.getDensityDpi());
        float height = dpiFromPx(minHeightPx, displayInfo.getDensityDpi());

        // Sort the profiles based on the closeness to the device size
        points.sort((a, b) ->
                Float.compare(dist(width, height, a.minWidthDps, a.minHeightDps),
                        dist(width, height, b.minWidthDps, b.minHeightDps)));

        DisplayOption closestPoint = points.get(0);
        GridOption closestOption = closestPoint.grid;
        float weights = 0;

        if (dist(width, height, closestPoint.minWidthDps, closestPoint.minHeightDps) == 0) {
            return closestPoint;
        }

        // Calculate the weighted average as
        // out = (w1 * p1 + w2 * p2 + w3 * p3...) / (w1 + w2 + w3...)
        DisplayOption out = DisplayOption.createEmpty(displayInfo.context, closestOption);
        for (int i = 0; i < points.size() && i < KNEARESTNEIGHBOR; ++i) {
            DisplayOption p = points.get(i);
            float w = weight(width, height, p.minWidthDps, p.minHeightDps, WEIGHT_POWER);
            weights += w;
            // out = out + w * p, for any boolean properties, we just take their union
            out.merge(p, (b1, b2) -> b1 || b2, (f1, f2) -> f1 + w * f2);
        }
        final float combinedWeight = weights;
        // out = out / combinedWeight
        out.merge(out, (b1, b2) -> b1, (f1, f2) -> f1 / combinedWeight);

        // Since the bitmaps are persisted, ensure that all bitmap sizes are not larger than
        // predefined size to avoid cache invalidation
        for (int i = 0; i < out.iconSizes.length; i++) {
            out.iconSizes[i] = Math.min(out.iconSizes[i], closestPoint.iconSizes[i]);
        }

        return out;
    }

    // TextLauncher: no secondary displays (createDeviceProfileForSecondaryDisplay), and one
    // device profile (getDeviceProfile and getBestMatch are at the end).

    private static float weight(float x0, float y0, float x1, float y1, float pow) {
        float d = dist(x0, y0, x1, y1);
        if (Float.compare(d, 0f) == 0) {
            return Float.POSITIVE_INFINITY;
        }
        return (float) (WEIGHT_EFFICIENT / Math.pow(d, pow));
    }

    /**
     * As a ratio of screen height, the total distance we want the parallax effect to span
     * horizontally
     */
    private static float wallpaperTravelToScreenWidthRatio(int width, int height) {
        float aspectRatio = width / (float) height;

        // At an aspect ratio of 16/10, the wallpaper parallax effect should span 1.5 * screen width
        // At an aspect ratio of 10/16, the wallpaper parallax effect should span 1.2 * screen width
        // We will use these two data points to extrapolate how much the wallpaper parallax effect
        // to span (ie travel) at any aspect ratio:

        final float ASPECT_RATIO_LANDSCAPE = 16 / 10f;
        final float ASPECT_RATIO_PORTRAIT = 10 / 16f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE = 1.5f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT = 1.2f;

        // To find out the desired width at different aspect ratios, we use the following two
        // formulas, where the coefficient on x is the aspect ratio (width/height):
        //   (16/10)x + y = 1.5
        //   (10/16)x + y = 1.2
        // We solve for x and y and end up with a final formula:
        final float x =
                (WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE
                        - WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT) /
                        (ASPECT_RATIO_LANDSCAPE - ASPECT_RATIO_PORTRAIT);
        final float y = WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT - x * ASPECT_RATIO_PORTRAIT;
        return x * aspectRatio + y;
    }

    public interface OnIDPChangeListener {

        /**
         * Called when the device provide changes
         */
        void onIdpChanged(boolean modelPropertiesChanged);
    }

    // TextLauncher: no DisplayOptionSpec (createDisplayOptionSpec), which only the external
    // displays and the DeviceProfile.Builder use.

    // ---- TextLauncher ----

    private DeviceProfile mDeviceProfile;

    /**
     * TextLauncher: getDeviceProfile with the home screen's one profile, or until the home screen
     * sets it (the widget picker can be made again before the home screen, after the process
     * ended), the profile the app's {@link GridInitializer} works out from the device.
     */
    public DeviceProfile getDeviceProfile(Context context) {
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
     * TextLauncher: the app's grid for the context's display, which it sets with
     * {@link #setDeviceProfile}.
     */
    public interface GridInitializer {
        void initGrid(Context context, InvariantDeviceProfile idp);
    }
}
