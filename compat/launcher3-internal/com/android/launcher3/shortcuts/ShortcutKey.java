package com.android.launcher3.shortcuts;

import android.content.Intent;
import android.content.pm.ShortcutInfo;

/**
 * Stand-in for Launcher3's ShortcutKey (android-17.0.0_r1). Only the intent that identifies an app
 * shortcut, copied from ShortcutKey.java.
 */
public class ShortcutKey {

    public static final String EXTRA_SHORTCUT_ID = "shortcut_id";
    private static final String INTENT_CATEGORY = "com.android.launcher3.DEEP_SHORTCUT";

    public static Intent makeIntent(ShortcutInfo si) {
        return makeIntent(si.getId(), si.getPackage()).setComponent(si.getActivity());
    }

    /**
     * Creates an intent for shortcut id and package name.
     */
    public static Intent makeIntent(String shortcutId, String packageName) {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(INTENT_CATEGORY)
                .setPackage(packageName)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .putExtra(EXTRA_SHORTCUT_ID, shortcutId);
    }
}
