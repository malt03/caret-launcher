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
 * Modified for TextLauncher: a stand-in that keeps only what TextLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

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
    public static final Interpolator EMPHASIZED_ACCELERATE = new PathInterpolator(
            0.3f, 0f, 0.8f, 0.15f);
    public static final Interpolator ACCELERATE = new android.view.animation.AccelerateInterpolator();
    public static final Interpolator DECELERATE_1_5 = new DecelerateInterpolator(1.5f);
    public static final Interpolator DECELERATE_2 = new DecelerateInterpolator(2);
    public static final Interpolator DECELERATE_QUINT = new DecelerateInterpolator(2.5f);
}
