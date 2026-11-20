package com.android.launcher3.dagger;

import android.content.Context;

import com.android.launcher3.folder.FolderNameSuggestionLoader;

/** Stand-in for Launcher3's Dagger component provider. Only what DropTarget.DragObject reads. */
public class LauncherComponentProvider {
    private static final LauncherComponentProvider INSTANCE = new LauncherComponentProvider();

    public static LauncherComponentProvider get(Context context) {
        return INSTANCE;
    }

    /** Folders have no names in TextLauncher (docs/spec.md 5.2). */
    public FolderNameSuggestionLoader getFolderNameSuggestionLoader() {
        return null;
    }
}
