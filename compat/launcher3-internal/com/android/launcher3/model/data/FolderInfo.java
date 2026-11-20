package com.android.launcher3.model.data;

import java.util.ArrayList;
import java.util.List;

/** Stand-in for Launcher3's FolderInfo. Only the contents, as FolderGridOrganizer reads them. */
public class FolderInfo extends ItemInfo {
    private final List<ItemInfo> mContents = new ArrayList<>();

    public List<ItemInfo> getContents() {
        return mContents;
    }
}
