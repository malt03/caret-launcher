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

    /** bp1a: ENABLED (cp1a inherits it through bp4a, bp3a and bp2a) */
    public static boolean enableFocusOutline() {
        return true;
    }

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

    /** Enabled in bp3a (cp1a inherits it). */
    public static boolean restoreArchivedAppIconsFromDb() {
        return true;
    }

    /** Not set in bp4a / cp1a: declaration default (disabled). */
    public static boolean enableDragStartEndMultiDispatch() {
        return false;
    }

    /** Not set in bp4a / cp1a: declaration default (disabled). */
    public static boolean enableSystemDrag() {
        return false;
    }

    /** cp1a: ENABLED */
    public static boolean enableExpressiveFolderExpansion() {
        return true;
    }

    /** Not set in bp4a / cp1a (cp2a enables it): declaration default (disabled). */
    public static boolean enableAppAutomationIndicator() {
        return false;
    }

    /** bp3a: ENABLED (cp1a inherits bp4a, which inherits bp3a) */
    public static boolean msdlFeedback() {
        return true;
    }

    /** Not set in bp4a / cp1a: declaration default (disabled). */
    public static boolean blurOnMoreSurfaces() {
        return false;
    }

    /** Not set in bp4a / cp1a: declaration default (disabled). */
    public static boolean letterFastScroller() {
        return false;
    }

    /** Not set in bp4a / cp1a: declaration default (disabled). */
    public static boolean enableWidgetPickerBlur() {
        return false;
    }

    /** Not set in bp4a / cp1a: declaration default (disabled). */
    public static boolean reduceWorkspaceBlurUsage() {
        return false;
    }

    /** Not set in bp4a / cp1a: declaration default (disabled). */
    public static boolean oneGridRotationHandling() {
        return false;
    }
}
