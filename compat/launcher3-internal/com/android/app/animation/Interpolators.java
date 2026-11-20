package com.android.app.animation;

import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * Stand-in for frameworks/libs/systemui animationlib's Interpolators (android-17.0.0_r1).
 * The original uses the hidden android.util.MathUtils, so only the members that the copied code
 * uses are defined here, with the same values.
 */
public class Interpolators {
    public static final Interpolator DECELERATE_1_5 = new DecelerateInterpolator(1.5f);
}
