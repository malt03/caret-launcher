package com.android.launcher3;

/**
 * Stand-in for com.android.launcher3.Utilities.
 * Members are copied from Launcher3 android-17.0.0_r1 as code in third_party/ needs them.
 */
public final class Utilities {
    private Utilities() {}

    public static boolean isDarkTheme(android.content.Context context) {
        android.content.res.Configuration configuration = context.getResources().getConfiguration();
        int nightMode = configuration.uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public static boolean isRtl(android.content.res.Resources res) {
        return res.getConfiguration().getLayoutDirection() == android.view.View.LAYOUT_DIRECTION_RTL;
    }

    public static float boundToRange(float value, float lowerBound, float upperBound) {
        return Math.max(lowerBound, Math.min(value, upperBound));
    }

    public static float getDescendantCoordRelativeToAncestor(
            android.view.View descendant, android.view.View ancestor, float[] coord, boolean includeRootScroll) {
        return getDescendantCoordRelativeToAncestor(descendant, ancestor, coord, includeRootScroll,
                false);
    }

    public static float getDescendantCoordRelativeToAncestor(android.view.View descendant, android.view.View ancestor,
            float[] coord, boolean includeRootScroll, boolean ignoreTransform) {
        float scale = 1.0f;
        android.view.View v = descendant;
        while(v != ancestor && v != null) {
            // For TextViews, scroll has a meaning which relates to the text position
            // which is very strange... ignore the scroll.
            if (v != descendant || includeRootScroll) {
                offsetPoints(coord, -v.getScrollX(), -v.getScrollY());
            }

            if (!ignoreTransform) {
                v.getMatrix().mapPoints(coord);
            }
            offsetPoints(coord, v.getLeft(), v.getTop());
            scale *= v.getScaleX();

            v = v.getParent() instanceof android.view.View ? (android.view.View) v.getParent() : null;
        }
        return scale;
    }

    public static void offsetPoints(float[] points, float offsetX, float offsetY) {
        for (int i = 0; i < points.length; i += 2) {
            points[i] += offsetX;
            points[i + 1] += offsetY;
        }
    }

    public static float mapRange(float value, float min, float max) {
        return min + (value * (max - min));
    }
}
