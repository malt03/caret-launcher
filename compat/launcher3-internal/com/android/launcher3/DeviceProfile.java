package com.android.launcher3;

/**
 * Stand-in for Launcher3's DeviceProfile (android-17.0.0_r1). Only the folder profile values that
 * the copied folder code reads. TextLauncher sets them from its own grid.
 */
public class DeviceProfile {
    private final FolderProfile mFolderProfile;

    public DeviceProfile(FolderProfile folderProfile) {
        mFolderProfile = folderProfile;
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

        public FolderProfile(int folderIconSizePx, int folderIconOffsetYPx, int numColumns, int numRows) {
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
    }
}
