/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.launcher3;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.util.FloatProperty;
import android.util.Property;
import android.view.View;

import com.android.launcher3.util.MultiScalePropertyFactory;
import com.android.launcher3.views.ScrimColors;
import com.android.launcher3.views.ScrimView;

/**
 * Stand-in for Launcher3's LauncherAnimUtils (android-17.0.0_r1), made from LauncherAnimUtils.java
 * by deleting what TextLauncher does not use.
 */
public class LauncherAnimUtils {
    /**
     * Durations for various state animations. These are not defined in resources to allow
     * easier access from static classes and enums
     */
    public static final int SPRING_LOADED_EXIT_DELAY = 500;

    // Progress after which the transition is assumed to be a success
    public static final float SUCCESS_TRANSITION_PROGRESS = 0.5f;
    public static final float TABLET_BOTTOM_SHEET_SUCCESS_TRANSITION_PROGRESS = 0.3f;

    public static final FloatProperty<View> SCALE_PROPERTY = getScaleProperty();

    /** Used to debug b/489475544, after the crash is fixed, caller can just use SCALE_PROPERTY t*/
    public static FloatProperty<View> getScaleProperty() {
        return new ScaleProperty();
    }

    /**
     * FloatProperty that saves the creation call stacktrace, so that during animation frame if
     * {@link android.view.ViewRootImpl$CalledFromWrongThreadException} is detected, the crash
     * will contain creation call stacktrace.
     */
    private static class ScaleProperty extends FloatProperty<View> {

        private final Exception mException;

        ScaleProperty() {
            super("scale");
            mException = new Exception();
        }

        @Override
        public Float get(View view) {
            return view.getScaleX();
        }

        @Override
        public void setValue(View view, float scale) {
            try {
                if (view.getHandler() != null
                        && view.getHandler().getLooper() != android.os.Looper.myLooper()) {
                    view.post(() -> {
                        view.setScaleX(scale);
                        view.setScaleY(scale);
                    });
                } else {
                    view.setScaleX(scale);
                    view.setScaleY(scale);
                }
            } catch (RuntimeException e) {
                // Check if the class name matches the hidden Android exception
                if (e.getClass().getName().contains("CalledFromWrongThreadException")) {
                    RuntimeException diagnostic = new RuntimeException(
                            "Thread Mismatch! Creator trace attached as cause.", e);
                    diagnostic.initCause(mException);
                    throw diagnostic;
                }
                throw e;
            }
        }
    }

    /**
     * Property to set the scale of workspace. The value is based on a combination
     * of all the ones set, to have a smooth experience even in the case of overlapping scaling
     * animation.
     */
    public static final MultiScalePropertyFactory<Workspace<?>> WORKSPACE_SCALE_PROPERTY_FACTORY =
            new MultiScalePropertyFactory<Workspace<?>>("workspace_scale_property");

    /** Property to set the scale of hotseat. */
    public static final MultiScalePropertyFactory<Hotseat> HOTSEAT_SCALE_PROPERTY_FACTORY =
            new MultiScalePropertyFactory<Hotseat>("hotseat_scale_property");

    public static final int SCALE_INDEX_WORKSPACE_STATE = 2;
    public static final int SCALE_INDEX_FOLDER_ANIM = 5;

    /** Increase the duration if we prevented the fling, as we are going against a high velocity. */
    public static int blockedFlingDurationFactor(float velocity) {
        return (int) Utilities.boundToRange(Math.abs(velocity) / 2, 2f, 6f);
    }

    public static final FloatProperty<View> VIEW_TRANSLATE_X = convert(View.TRANSLATION_X);
    public static final FloatProperty<View> VIEW_TRANSLATE_Y = convert(View.TRANSLATION_Y);
    public static final FloatProperty<View> VIEW_TRANSLATE_Z = convert(View.TRANSLATION_Z);

    public static final FloatProperty<View> VIEW_ALPHA = convert(View.ALPHA);

    public static final Property<ScrimView, ScrimColors> SCRIM_COLORS =
            new Property<ScrimView, ScrimColors>(ScrimColors.class, "scrimColors") {
                @Override
                public void set(ScrimView scrimView, ScrimColors scrimColors) {
                    scrimView.setScrimColors(scrimColors);
                }

                @Override
                public ScrimColors get(ScrimView scrimView) {
                    return scrimView.getScrimColors();
                }
            };

    private static FloatProperty<View> convert(Property<View, Float> property) {
        if (property instanceof FloatProperty<View> f) return f;
        return new FloatProperty<>(property.getName()) {
            @Override
            public void setValue(View view, float v) {
                property.set(view, v);
            }

            @Override
            public Float get(View view) {
                return property.get(view);
            }
        };
    }

    /**
     * Utility method to create an {@link AnimatorListener} which executes a callback on animation
     * cancel. Once the cancel has been dispatched, this listener will no longer be called.
     */
    public static AnimatorListener newSingleUseCancelListener(Runnable callback) {
        return newCancelListener(callback, true);
    }

    /**
     * Utility method to create an {@link AnimatorListener} which executes a callback on animation
     * cancel.
     *
     * @param isSingleUse {@code true} means the callback will be called at most once
     */
    public static AnimatorListener newCancelListener(Runnable callback, boolean isSingleUse) {
        return new AnimatorListenerAdapter() {
            boolean mDispatched = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                if (!mDispatched) {
                    if (isSingleUse) {
                        mDispatched = true;
                    }
                    callback.run();
                }
            }
        };
    }
}
