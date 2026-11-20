package com.android.launcher3;

/**
 * Stand-in for com.android.launcher3.Utilities.
 * Members are copied from Launcher3 android-17.0.0_r1 as code in third_party/ needs them.
 */
public final class Utilities {
    private Utilities() {}

    public static float mapRange(float value, float min, float max) {
        return min + (value * (max - min));
    }
}
