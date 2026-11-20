package com.android.launcher3.model.data;

import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_GROUP;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER;

import java.util.ArrayList;
import java.util.List;

/**
 * Stand-in for Launcher3's FolderInfo. Only the contents, as FolderGridOrganizer and the folder
 * icon read them. Folders have no titles in TextLauncher (docs/spec.md 5.2).
 */
public class FolderInfo extends ItemInfo {
    private final List<ItemInfo> mContents = new ArrayList<>();

    public FolderInfo() {
        itemType = ITEM_TYPE_FOLDER;
    }

    public List<ItemInfo> getContents() {
        return mContents;
    }

    public static boolean willAcceptItemType(int itemType) {
        return itemType == ITEM_TYPE_APPLICATION
                || itemType == ITEM_TYPE_DEEP_SHORTCUT
                || itemType == ITEM_TYPE_APP_GROUP;
    }
}
