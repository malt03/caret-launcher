/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.app.animation;

import android.graphics.Path;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

/**
 * Stand-in for frameworks/libs/systemui animationlib's Interpolators (android-17.0.0_r1).
 * The original uses the hidden android.util.MathUtils, so only the members that the copied code
 * uses are defined here, with the same values.
 */
public class Interpolators {
    public static final Interpolator LINEAR = new LinearInterpolator();
    public static final Interpolator EMPHASIZED = createEmphasizedInterpolator();

    private static PathInterpolator createEmphasizedInterpolator() {
        Path path = new Path();
        // Doing the same as fast_out_extra_slow_in
        path.moveTo(0f, 0f);
        path.cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f);
        path.cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f);
        return new PathInterpolator(path);
    }
    public static final Interpolator EMPHASIZED_DECELERATE = new PathInterpolator(
            0.05f, 0.7f, 0.1f, 1f);
    public static final Interpolator ACCELERATE_DECELERATE = new AccelerateDecelerateInterpolator();
    public static final Interpolator EMPHASIZED_ACCELERATE = new PathInterpolator(
            0.3f, 0f, 0.8f, 0.15f);
    public static final Interpolator ACCELERATE = new AccelerateInterpolator();
    public static final Interpolator ACCELERATE_2 = new AccelerateInterpolator(2);
    public static final Interpolator DECELERATED_EASE = new PathInterpolator(0, 0, .2f, 1f);
    public static final Interpolator DECELERATE_1_5 = new DecelerateInterpolator(1.5f);
    public static final Interpolator DECELERATE_1_7 = new DecelerateInterpolator(1.7f);
    public static final Interpolator DECELERATE_2 = new DecelerateInterpolator(2);
    public static final Interpolator DECELERATE_QUINT = new DecelerateInterpolator(2.5f);

    public static final Interpolator INSTANT = t -> 1;
    /**
     * All values of t map to 0 until t == 1. This is primarily useful for setting view visibility,
     * which should only happen at the very end of the animation (when it's already hidden).
     */
    public static final Interpolator FINAL_FRAME = t -> t < 1 ? 0 : 1;

    public static final Interpolator SCROLL = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t*t*t*t*t + 1;
        }
    };

    public static final Interpolator SCROLL_CUBIC = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t*t*t + 1;
        }
    };

    private static final float FAST_FLING_PX_MS = 10;

    public static Interpolator scrollInterpolatorForVelocity(float velocity) {
        return Math.abs(velocity) > FAST_FLING_PX_MS ? SCROLL : SCROLL_CUBIC;
    }

    /**
     * Returns a function that runs the given interpolator such that the entire progress is set
     * between the given bounds. That is, we set the interpolation to 0 until lowerBound and reach
     * 1 by upperBound.
     */
    public static Interpolator clampToProgress(Interpolator interpolator, float lowerBound,
            float upperBound) {
        if (upperBound < lowerBound) {
            throw new IllegalArgumentException(
                    String.format("upperBound (%f) must be greater than lowerBound (%f)",
                            upperBound, lowerBound));
        }
        return t -> clampToProgress(interpolator, t, lowerBound, upperBound);
    }

    /**
     * Returns the progress value's progress between the lower and upper bounds. That is, the
     * progress will be 0f from 0f to lowerBound, and reach 1f by upperBound.
     *
     * Between lowerBound and upperBound, the progress value will be interpolated using the provided
     * interpolator.
     */
    public static float clampToProgress(
            Interpolator interpolator, float progress, float lowerBound, float upperBound) {
        if (upperBound < lowerBound) {
            throw new IllegalArgumentException(
                    String.format("upperBound (%f) must be greater than lowerBound (%f)",
                            upperBound, lowerBound));
        }

        if (progress == lowerBound && progress == upperBound) {
            return progress == 0f ? 0 : 1;
        }
        if (progress < lowerBound) {
            return 0;
        }
        if (progress > upperBound) {
            return 1;
        }
        return interpolator.getInterpolation((progress - lowerBound) / (upperBound - lowerBound));
    }

    /**
     * Returns the reverse of the provided interpolator, following the formula: g(x) = 1 - f(1 - x).
     * In practice, this means that if f is an interpolator used to model a value animating between
     * m and n, g is the interpolator to use to obtain the specular behavior when animating from n
     * to m.
     */
    public static Interpolator reverse(Interpolator interpolator) {
        return t -> 1 - interpolator.getInterpolation(1 - t);
    }

    public static final Interpolator ZOOM_OUT = new Interpolator() {

        private static final float FOCAL_LENGTH = 0.35f;

        @Override
        public float getInterpolation(float v) {
            return zInterpolate(v);
        }

        /**
         * This interpolator emulates the rate at which the perceived scale of an object changes
         * as its distance from a camera increases. When this interpolator is applied to a scale
         * animation on a view, it evokes the sense that the object is shrinking due to moving away
         * from the camera.
         */
        private float zInterpolate(float input) {
            return (1.0f - FOCAL_LENGTH / (FOCAL_LENGTH + input)) /
                    (1.0f - FOCAL_LENGTH / (FOCAL_LENGTH + 1.0f));
        }
    };
}
