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

import android.util.FloatProperty;
import android.util.Property;
import android.view.View;

import com.android.launcher3.util.MultiScalePropertyFactory;

/**
 * Stand-in for Launcher3's LauncherAnimUtils (android-17.0.0_r1), made from LauncherAnimUtils.java
 * by deleting what TextLauncher does not use.
 */
public class LauncherAnimUtils {
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

    public static final FloatProperty<View> VIEW_TRANSLATE_X = convert(View.TRANSLATION_X);
    public static final FloatProperty<View> VIEW_TRANSLATE_Y = convert(View.TRANSLATION_Y);
    public static final FloatProperty<View> VIEW_TRANSLATE_Z = convert(View.TRANSLATION_Z);

    public static final FloatProperty<View> VIEW_ALPHA = convert(View.ALPHA);

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
}
