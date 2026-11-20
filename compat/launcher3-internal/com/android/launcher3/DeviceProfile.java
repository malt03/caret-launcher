package com.android.launcher3;

/**
 * Stand-in for Launcher3's DeviceProfile (android-17.0.0_r1). Only the folder and workspace values
 * that the copied folder code reads. TextLauncher sets them from its own grid.
 */
public class DeviceProfile {
    private final FolderProfile mFolderProfile;
    private final WorkspaceProfile mWorkspaceProfile;

    public DeviceProfile(FolderProfile folderProfile, WorkspaceProfile workspaceProfile) {
        mFolderProfile = folderProfile;
        mWorkspaceProfile = workspaceProfile;
    }

    public WorkspaceProfile getWorkspaceProfile() {
        return mWorkspaceProfile;
    }

    /** Stand-in for DeviceProfile's workspace values. */
    public static class WorkspaceProfile {
        private final int mIconSizePx;

        public WorkspaceProfile(int iconSizePx) {
            mIconSizePx = iconSizePx;
        }

        public int getIconSizePx() {
            return mIconSizePx;
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

        public FolderProfile(int folderIconSizePx, int folderIconOffsetYPx, int numColumns, int numRows,
                int childIconSizePx) {
            mChildIconSizePx = childIconSizePx;
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
    }
}
