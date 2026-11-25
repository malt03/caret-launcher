/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import android.graphics.Point;

import com.android.launcher3.deviceprofile.DropTargetProfile;

/**
 * Stand-in for Launcher3's DeviceProfile (android-17.0.0_r1). Only the folder, workspace, all apps
 * and device values that the copied code reads. CaretLauncher sets them from its own grid.
 */
public class DeviceProfile {
    /** CaretLauncher: set by {@link InvariantDeviceProfile#setDeviceProfile}. */
    public InvariantDeviceProfile inv;

    private final FolderProfile mFolderProfile;
    private final WorkspaceProfile mWorkspaceProfile;
    private final DeviceProperties mDeviceProperties;
    private final AllAppsProfile mAllAppsProfile;
    private final DropTargetProfile mDropTargetProfile;

    public DeviceProfile(FolderProfile folderProfile, WorkspaceProfile workspaceProfile,
            DeviceProperties deviceProperties, AllAppsProfile allAppsProfile,
            DropTargetProfile dropTargetProfile) {
        mFolderProfile = folderProfile;
        mWorkspaceProfile = workspaceProfile;
        mDeviceProperties = deviceProperties;
        mAllAppsProfile = allAppsProfile;
        mDropTargetProfile = dropTargetProfile;
        mBottomSheetProfile = new BottomSheetProfile(deviceProperties, workspaceProfile);
    }

    /** The drop target bar's dimensions (DropTargetProfile.createDropTargetProfile). */
    public DropTargetProfile getDropTargetProfile() {
        return mDropTargetProfile;
    }

    /**
     * DeviceProfile.getAppWidgetScale. CaretLauncher: widgets are not scaled (the scale is 1 outside
     * the multi-window and preview profiles).
     */
    public android.graphics.PointF getAppWidgetScale(
            @androidx.annotation.Nullable com.android.launcher3.model.data.ItemInfo itemInfo) {
        return new android.graphics.PointF(1f, 1f);
    }

    /** CaretLauncher: no hotseat at the side, so never a vertical bar layout. */
    public boolean isVerticalBarLayout() {
        return false;
    }

    public AllAppsProfile getAllAppsProfile() {
        return mAllAppsProfile;
    }

    /** DeviceProfile.AllAppsProfile: the all apps grid's columns, cells and icons. */
    public static class AllAppsProfile {
        private final int mNumShownAllAppsColumns;
        private final int mCellHeightPx;
        private final int mIconSizePx;
        private final float mIconTextSizePx;

        private final int mOpenDuration;
        private final int mCloseDuration;

        public AllAppsProfile(int numShownAllAppsColumns, int cellHeightPx, int iconSizePx,
                float iconTextSizePx, int openDuration, int closeDuration) {
            mNumShownAllAppsColumns = numShownAllAppsColumns;
            mCellHeightPx = cellHeightPx;
            mIconSizePx = iconSizePx;
            mIconTextSizePx = iconTextSizePx;
            mOpenDuration = openDuration;
            mCloseDuration = closeDuration;
        }

        /** config_allAppsOpenDuration */
        public int getOpenDuration() {
            return mOpenDuration;
        }

        /** config_allAppsCloseDuration */
        public int getCloseDuration() {
            return mCloseDuration;
        }

        public int getIconSizePx() {
            return mIconSizePx;
        }

        public float getIconTextSizePx() {
            return mIconTextSizePx;
        }

        public int getNumShownAllAppsColumns() {
            return mNumShownAllAppsColumns;
        }

        public int getCellHeightPx() {
            return mCellHeightPx;
        }
    }

    public DeviceProperties getDeviceProperties() {
        return mDeviceProperties;
    }

    /** Stand-in for DeviceProfile's device properties. */
    public static class DeviceProperties {
        private final int mAvailableHeightPx;
        private final boolean mIsLargeScreen;
        private final boolean mIsTwoPanels;

        public DeviceProperties(int availableHeightPx, boolean isLargeScreen, boolean isTwoPanels) {
            mAvailableHeightPx = availableHeightPx;
            mIsLargeScreen = isLargeScreen;
            mIsTwoPanels = isTwoPanels;
        }

        public boolean isLargeScreen() {
            return mIsLargeScreen;
        }

        public boolean isTwoPanels() {
            return mIsTwoPanels;
        }

        /** CaretLauncher: the home screen's profile is the same in every rotation. */
        public boolean isLandscape() {
            return false;
        }

        /** The height of the window minus the system bars. */
        public int getAvailableHeightPx() {
            return mAvailableHeightPx;
        }
    }

    public WorkspaceProfile getWorkspaceProfile() {
        return mWorkspaceProfile;
    }

    /** Stand-in for DeviceProfile's workspace values. */
    public static class WorkspaceProfile {
        private final int mIconSizePx;
        private final boolean mIconCenterVertically;

        private final int mGridVisualizationPaddingX;
        private final int mGridVisualizationPaddingY;
        private final float mWorkspaceContentScale;

        public WorkspaceProfile(int iconSizePx, boolean iconCenterVertically,
                int gridVisualizationPaddingX, int gridVisualizationPaddingY,
                float workspaceContentScale) {
            mIconSizePx = iconSizePx;
            mIconCenterVertically = iconCenterVertically;
            mGridVisualizationPaddingX = gridVisualizationPaddingX;
            mGridVisualizationPaddingY = gridVisualizationPaddingY;
            mWorkspaceContentScale = workspaceContentScale;
        }

        /** workspace_content_scale: the home screen's scale behind the all apps list. */
        public float getWorkspaceContentScale() {
            return mWorkspaceContentScale;
        }

        /**
         * WorkspaceProfileNonResponsiveFactory: grid_visualization_horizontal_cell_spacing, for
         * CellLayout's drop location outline.
         */
        public int getGridVisualizationPaddingX() {
            return mGridVisualizationPaddingX;
        }

        /** WorkspaceProfileNonResponsiveFactory: grid_visualization_vertical_cell_spacing. */
        public int getGridVisualizationPaddingY() {
            return mGridVisualizationPaddingY;
        }

        public int getIconSizePx() {
            return mIconSizePx;
        }

        public boolean getIconCenterVertically() {
            return mIconCenterVertically;
        }

        /** CaretLauncher: the home grid's cells have no space between them. */
        public Point getCellLayoutBorderSpacePx() {
            return new Point(0, 0);
        }

        private final Point mCellSize = new Point();
        private final android.graphics.Rect mWidgetPadding = new android.graphics.Rect();

        /** The home grid's cell size, which the grid sets as it is measured. */
        public Point getCellSize() {
            return new Point(mCellSize);
        }

        /** The space around a widget in its cells (ShortcutAndWidgetContainer.getWidgetPadding). */
        public android.graphics.Rect getWidgetPadding() {
            return new android.graphics.Rect(mWidgetPadding);
        }

        /** CaretLauncher: DeviceProfile works these out; the home grid sets them as it measures. */
        public void setCellSize(int width, int height, android.graphics.Rect widgetPadding) {
            mCellSize.set(width, height);
            mWidgetPadding.set(widgetPadding);
        }
    }

    private java.util.function.Supplier<Float> mWorkspaceSpringLoadScale = () -> 1f;

    /**
     * The scale of the workspace in the spring-loaded state (WidgetDragScaleUtils). CaretLauncher:
     * the home screen works it out from its own layout (the drop target bar, the search field with
     * the keyboard down) and sets it with {@link #setWorkspaceSpringLoadScale}.
     */
    public float getWorkspaceSpringLoadScale(android.content.Context context) {
        return mWorkspaceSpringLoadScale.get();
    }

    /** CaretLauncher: set by the home screen; see {@link #getWorkspaceSpringLoadScale}. */
    public void setWorkspaceSpringLoadScale(java.util.function.Supplier<Float> scale) {
        mWorkspaceSpringLoadScale = scale;
    }

    private java.util.function.Supplier<Integer> mCellLayoutHeight = () -> 0;

    /**
     * The page's height. CaretLauncher: the home screen's page with the keyboard down, which it has
     * while editing (docs/spec.md 5.4), set with {@link #setCellLayoutHeight}.
     */
    public int getCellLayoutHeight() {
        return mCellLayoutHeight.get();
    }

    /** CaretLauncher: set by the home screen; see {@link #getCellLayoutHeight}. */
    public void setCellLayoutHeight(java.util.function.Supplier<Integer> height) {
        mCellLayoutHeight = height;
    }

    private java.util.function.Supplier<Float> mCellLayoutSpringLoadShrunkTop = () -> 0f;

    /**
     * The top of the page in the spring-loaded state, below the drop target bar, in the coordinates
     * of the workspace's parent (SpringLoadedState). CaretLauncher: the home screen works it out from
     * its own layout and sets it with {@link #setCellLayoutSpringLoadShrunkTop}.
     */
    public float getCellLayoutSpringLoadShrunkTop() {
        return mCellLayoutSpringLoadShrunkTop.get();
    }

    /** CaretLauncher: set by the home screen; see {@link #getCellLayoutSpringLoadShrunkTop}. */
    public void setCellLayoutSpringLoadShrunkTop(java.util.function.Supplier<Float> top) {
        mCellLayoutSpringLoadShrunkTop = top;
    }

    private final BottomSheetProfile mBottomSheetProfile;

    public BottomSheetProfile getBottomSheetProfile() {
        return mBottomSheetProfile;
    }

    /** Stand-in for DeviceProfile's bottom sheet values: the depth behind the all apps list. */
    public static class BottomSheetProfile {
        /** config_wallpaperMaxScale */
        private static final float MAX_WALLPAPER_SCALE = 1.1f;

        private final float mBottomSheetDepth;

        BottomSheetProfile(DeviceProperties deviceProperties, WorkspaceProfile workspaceProfile) {
            // BottomSheetProfile.Factory: 0.3 on a device with several displays (two panels);
            // otherwise the depth that zooms the wallpaper to config_wallpaperMaxScale times
            // workspace_content_scale.
            mBottomSheetDepth = deviceProperties.isTwoPanels()
                    ? 0.3f
                    : Utilities.mapToRange(
                            MAX_WALLPAPER_SCALE * workspaceProfile.getWorkspaceContentScale(),
                            MAX_WALLPAPER_SCALE, 1f, 0f, 1f,
                            com.android.app.animation.Interpolators.LINEAR);
        }

        public float getBottomSheetDepth() {
            return mBottomSheetDepth;
        }
    }

    public FolderProfile getFolderProfile() {
        return mFolderProfile;
    }

    /** Stand-in for DeviceProfile's folder values. */
    public static class FolderProfile {
        private final int mFolderIconSizePx;
        private final int mFolderIconOffsetYPx;
        private final int mNumColumns;
        private final int mNumRows;
        private final int mChildIconSizePx;
        private final int mCellWidthPx;
        private final int mCellHeightPx;

        public FolderProfile(int folderIconSizePx, int folderIconOffsetYPx, int numColumns, int numRows,
                int childIconSizePx, int cellWidthPx, int cellHeightPx) {
            mChildIconSizePx = childIconSizePx;
            mCellWidthPx = cellWidthPx;
            mCellHeightPx = cellHeightPx;
            mFolderIconSizePx = folderIconSizePx;
            mFolderIconOffsetYPx = folderIconOffsetYPx;
            mNumColumns = numColumns;
            mNumRows = numRows;
        }

        public int getFolderIconSizePx() {
            return mFolderIconSizePx;
        }

        public int getFolderIconOffsetYPx() {
            return mFolderIconOffsetYPx;
        }

        public int getNumColumns() {
            return mNumColumns;
        }

        public int getNumRows() {
            return mNumRows;
        }

        /** The size of the app icons in an open folder. */
        public int getChildIconSizePx() {
            return mChildIconSizePx;
        }

        /** The size of a cell in an open folder. */
        public int getCellWidthPx() {
            return mCellWidthPx;
        }

        public int getCellHeightPx() {
            return mCellHeightPx;
        }

        /** CaretLauncher: an open folder's cells have no space between them. */
        public Point getCellLayoutBorderSpacePx() {
            return new Point(0, 0);
        }
    }

    /** DeviceProfile.calculateCellWidth */
    public static int calculateCellWidth(int width, int borderSpacing, int countX) {
        return (width - ((countX - 1) * borderSpacing)) / countX;
    }

    /** DeviceProfile.calculateCellHeight */
    public static int calculateCellHeight(int height, int borderSpacing, int countY) {
        return (height - ((countY - 1) * borderSpacing)) / countY;
    }
}
