package com.android.window.flags;

/**
 * Stand-in for the aconfig-generated com.android.window.flags.Flags. Values follow AOSP
 * build/release at android-17.0.0_r1 (cp1a inherits bp4a, which inherits bp3a).
 */
public final class Flags {
    private Flags() {}

    /** Not set in bp3a, bp4a or cp1a: the declaration's default, DISABLED. */
    public static boolean showHomeBehindDesktop() {
        return false;
    }
}
