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
 * Modified for TextLauncher: a stand-in that keeps only what TextLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3;

/**
 * Stand-in for Launcher3's DeviceProfile (android-17.0.0_r1). Only the folder, workspace, all apps
 * and device values that the copied code reads. TextLauncher sets them from its own grid.
 */
public class DeviceProfile {
    private final FolderProfile mFolderProfile;
    private final WorkspaceProfile mWorkspaceProfile;
    private final DeviceProperties mDeviceProperties;
    private final AllAppsProfile mAllAppsProfile;

    public DeviceProfile(FolderProfile folderProfile, WorkspaceProfile workspaceProfile,
            DeviceProperties deviceProperties, AllAppsProfile allAppsProfile) {
        mFolderProfile = folderProfile;
        mWorkspaceProfile = workspaceProfile;
        mDeviceProperties = deviceProperties;
        mAllAppsProfile = allAppsProfile;
    }

    public AllAppsProfile getAllAppsProfile() {
        return mAllAppsProfile;
    }

    /** DeviceProfile.AllAppsProfile: the all apps grid's columns and cell height. */
    public static class AllAppsProfile {
        private final int mNumShownAllAppsColumns;
        private final int mCellHeightPx;

        public AllAppsProfile(int numShownAllAppsColumns, int cellHeightPx) {
            mNumShownAllAppsColumns = numShownAllAppsColumns;
            mCellHeightPx = cellHeightPx;
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

        public DeviceProperties(int availableHeightPx) {
            mAvailableHeightPx = availableHeightPx;
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

        public WorkspaceProfile(int iconSizePx, boolean iconCenterVertically) {
            mIconSizePx = iconSizePx;
            mIconCenterVertically = iconCenterVertically;
        }

        public int getIconSizePx() {
            return mIconSizePx;
        }

        public boolean getIconCenterVertically() {
            return mIconCenterVertically;
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
    }
}
