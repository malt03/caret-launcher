package com.android.app.animation;

import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

/**
 * Stand-in for frameworks/libs/systemui animationlib's Interpolators (android-17.0.0_r1).
 * The original uses the hidden android.util.MathUtils, so only the members that the copied code
 * uses are defined here, with the same values.
 */
public class Interpolators {
    public static final Interpolator LINEAR = new LinearInterpolator();
    public static final Interpolator EMPHASIZED = createEmphasizedInterpolator();

    private static PathInterpolator createEmphasizedInterpolator() {
        android.graphics.Path path = new android.graphics.Path();
        // Doing the same as fast_out_extra_slow_in
        path.moveTo(0f, 0f);
        path.cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f);
        path.cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f);
        return new PathInterpolator(path);
    }
    public static final Interpolator EMPHASIZED_DECELERATE = new PathInterpolator(
            0.05f, 0.7f, 0.1f, 1f);
    public static final Interpolator ACCELERATE_DECELERATE = new AccelerateDecelerateInterpolator();
    public static final Interpolator ACCELERATE = new android.view.animation.AccelerateInterpolator();
    public static final Interpolator DECELERATE_1_5 = new DecelerateInterpolator(1.5f);
    public static final Interpolator DECELERATE_2 = new DecelerateInterpolator(2);
    public static final Interpolator DECELERATE_QUINT = new DecelerateInterpolator(2.5f);
}
