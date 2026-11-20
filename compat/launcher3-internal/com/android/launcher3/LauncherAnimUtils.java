package com.android.launcher3;

import android.util.FloatProperty;
import android.util.Property;
import android.view.View;

/**
 * Stand-in for Launcher3's LauncherAnimUtils (android-17.0.0_r1). Only the members used by the
 * copied util/MultiTranslateDelegate.java, copied from LauncherAnimUtils.java.
 */
public class LauncherAnimUtils {
    public static final FloatProperty<View> VIEW_TRANSLATE_X = convert(View.TRANSLATION_X);
    public static final FloatProperty<View> VIEW_TRANSLATE_Y = convert(View.TRANSLATION_Y);

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
