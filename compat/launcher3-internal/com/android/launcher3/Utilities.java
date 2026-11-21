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

    private static final android.graphics.Matrix sMatrix = new android.graphics.Matrix();
    private static final android.graphics.Matrix sInverseMatrix = new android.graphics.Matrix();

    public static void mapCoordInSelfToDescendant(android.view.View descendant, android.view.View root, float[] coord) {
        sMatrix.reset();
        //TODO(b/307488755) when implemented this check should be removed
        if (!java.util.Objects.equals(descendant.getWindowId(), root.getWindowId())) {
            return;
        }
        android.view.View v = descendant;
        while (v != root) {
            sMatrix.postTranslate(-v.getScrollX(), -v.getScrollY());
            sMatrix.postConcat(v.getMatrix());
            sMatrix.postTranslate(v.getLeft(), v.getTop());
            v = (android.view.View) v.getParent();
        }
        sMatrix.postTranslate(-v.getScrollX(), -v.getScrollY());
        sMatrix.invert(sInverseMatrix);
        sInverseMatrix.mapPoints(coord);
    }

    /**
     * Sets {@param out} to be same as {@param in} by rounding individual values
     */
    public static void roundArray(float[] in, int[] out) {
        for (int i = 0; i < in.length; i++) {
            out[i] = Math.round(in[i]);
        }
    }

    public static void scaleRectAboutCenter(android.graphics.Rect r, float scale) {
        if (scale != 1.0f) {
            float cx = r.exactCenterX();
            float cy = r.exactCenterY();
            r.left = Math.round(cx + (r.left - cx) * scale);
            r.top = Math.round(cy + (r.top - cy) * scale);
            r.right = Math.round(cx + (r.right - cx) * scale);
            r.bottom = Math.round(cy + (r.bottom - cy) * scale);
        }
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

    /** Utilities.dpToPx */
    public static int dpToPx(float dp) {
        return (int) (dp * android.content.res.Resources.getSystem().getDisplayMetrics().density);
    }

    public static float squaredHypot(float x, float y) {
        return x * x + y * y;
    }

    public static float squaredTouchSlop(android.content.Context context) {
        float slop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
        return slop * slop;
    }

    public static android.graphics.Rect getViewBounds(android.view.View v) {
        int[] pos = new int[2];
        v.getLocationOnScreen(pos);
        return new android.graphics.Rect(pos[0], pos[1], pos[0] + v.getWidth(), pos[1] + v.getHeight());
    }
}
