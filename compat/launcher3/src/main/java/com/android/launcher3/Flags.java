package com.android.launcher3;

/**
 * Stand-in for the aconfig-generated com.android.launcher3.Flags.
 *
 * Values follow AOSP build/release at android-17.0.0_r1: release config cp1a inherits bp4a.
 * Flags without a value there use the declaration default (disabled).
 * Add a method here only when code in third_party/ needs it.
 */
public final class Flags {
    private Flags() {}

    /** bp4a: ENABLED */
    public static boolean enableLauncherIconShapes() {
        return true;
    }

    /** bp4a: ENABLED */
    public static boolean forceMonochromeAppIcons() {
        return true;
    }

    /** Not set in bp4a / cp1a: declaration default (disabled). */
    public static boolean forceMonochromeAppIconsAdaptColors() {
        return false;
    }

    /** Not set in bp4a / cp1a: declaration default (disabled). */
    public static boolean restoreArchivedAppIconsFromDb() {
        return false;
    }
}
