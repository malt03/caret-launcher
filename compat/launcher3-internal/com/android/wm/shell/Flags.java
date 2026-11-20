package com.android.wm.shell;

/**
 * Stand-in for the aconfig-generated com.android.wm.shell.Flags. Values follow AOSP build/release
 * at android-17.0.0_r1 (cp1a inherits bp4a, which inherits bp3a).
 */
public final class Flags {
    private Flags() {}

    /** bp3a: ENABLED */
    public static boolean enableGsf() {
        return true;
    }
}
