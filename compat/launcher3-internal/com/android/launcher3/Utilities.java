/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static com.android.launcher3.Flags.enableMouseInteractionChanges;
import static com.android.launcher3.Flags.reduceWorkspaceBlurUsage;

import android.app.ActivityOptions;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.Message;
import android.os.TransactionTooLargeException;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Various utilities shared amongst the Launcher's classes.
 *
 * <p>Stand-in for Launcher3's Utilities (android-17.0.0_r1), made from Utilities.java by deleting
 * what TextLauncher does not use. Nothing else is changed; a change would be marked with
 * "TextLauncher:".
 */
public final class Utilities {

    private static final String TRIM_PATTERN = "(^\\h+|\\h+$)";

    private static final Matrix sMatrix = new Matrix();
    private static final Matrix sInverseMatrix = new Matrix();

    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    @ChecksSdkIntAtLeast(api = VERSION_CODES.TIRAMISU, codename = "T")
    public static final boolean ATLEAST_T = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;

    @ChecksSdkIntAtLeast(api = VERSION_CODES.UPSIDE_DOWN_CAKE, codename = "U")
    public static final boolean ATLEAST_U = Build.VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE;

    /**
     * Returns true if theme is dark.
     */
    public static boolean isDarkTheme(Context context) {
        Configuration configuration = context.getResources().getConfiguration();
        int nightMode = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Given a coordinate relative to the descendant, find the coordinate in a parent view's
     * coordinates.
     *
     * @param descendant The descendant to which the passed coordinate is relative.
     * @param ancestor The root view to make the coordinates relative to.
     * @param coord The coordinate that we want mapped.
     * @param includeRootScroll Whether or not to account for the scroll of the descendant:
     *          sometimes this is relevant as in a child's coordinates within the descendant.
     * @return The factor by which this descendant is scaled relative to this DragLayer. Caution
     *         this scale factor is assumed to be equal in X and Y, and so if at any point this
     *         assumption fails, we will need to return a pair of scale factors.
     */
    public static float getDescendantCoordRelativeToAncestor(
            View descendant, View ancestor, float[] coord, boolean includeRootScroll) {
        return getDescendantCoordRelativeToAncestor(descendant, ancestor, coord, includeRootScroll,
                false);
    }

    /**
     * Given a coordinate relative to the descendant, find the coordinate in a parent view's
     * coordinates.
     *
     * @param descendant The descendant to which the passed coordinate is relative.
     * @param ancestor The root view to make the coordinates relative to.
     * @param coord The coordinate that we want mapped.
     * @param includeRootScroll Whether or not to account for the scroll of the descendant:
     *          sometimes this is relevant as in a child's coordinates within the descendant.
     * @param ignoreTransform If true, view transform is ignored
     * @return The factor by which this descendant is scaled relative to this DragLayer. Caution
     *         this scale factor is assumed to be equal in X and Y, and so if at any point this
     *         assumption fails, we will need to return a pair of scale factors.
     */
    public static float getDescendantCoordRelativeToAncestor(View descendant, View ancestor,
            float[] coord, boolean includeRootScroll, boolean ignoreTransform) {
        float scale = 1.0f;
        View v = descendant;
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

            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return scale;
    }

    /**
     * Inverse of {@link #getDescendantCoordRelativeToAncestor(View, View, float[], boolean)}.
     */
    public static void mapCoordInSelfToDescendant(View descendant, View root, float[] coord) {
        sMatrix.reset();
        //TODO(b/307488755) when implemented this check should be removed
        if (!Objects.equals(descendant.getWindowId(), root.getWindowId())) {
            return;
        }
        View v = descendant;
        while (v != root) {
            sMatrix.postTranslate(-v.getScrollX(), -v.getScrollY());
            sMatrix.postConcat(v.getMatrix());
            sMatrix.postTranslate(v.getLeft(), v.getTop());
            v = (View) v.getParent();
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

    public static void offsetPoints(float[] points, float offsetX, float offsetY) {
        for (int i = 0; i < points.length; i += 2) {
            points[i] += offsetX;
            points[i + 1] += offsetY;
        }
    }

    /**
     * Utility method to determine whether the given point, in local coordinates,
     * is inside the view, where the area of the view is expanded by the slop factor.
     * This method is called while processing touch-move events to determine if the event
     * is still within the view.
     */
    public static boolean pointInView(View v, float localX, float localY, float slop) {
        return localX >= -slop && localY >= -slop && localX < (v.getWidth() + slop) &&
                localY < (v.getHeight() + slop);
    }

    public static void scaleRectAboutCenter(Rect r, float scale) {
        if (scale != 1.0f) {
            float cx = r.exactCenterX();
            float cy = r.exactCenterY();
            r.left = Math.round(cx + (r.left - cx) * scale);
            r.top = Math.round(cy + (r.top - cy) * scale);
            r.right = Math.round(cx + (r.right - cx) * scale);
            r.bottom = Math.round(cy + (r.bottom - cy) * scale);
        }
    }

    public static float mapRange(float value, float min, float max) {
        return min + (value * (max - min));
    }

    /**
     * Trims the string, removing all whitespace at the beginning and end of the string.
     * Non-breaking whitespaces are also removed.
     */
    @NonNull
    public static String trim(CharSequence s) {
        if (s == null) {
            return "";
        }
        return s.toString().replaceAll(TRIM_PATTERN, "").trim();
    }

    public static boolean isRtl(Resources res) {
        return res.getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    /** Converts a pixel value (px) to scale pixel value (SP) for the current device. */
    public static float pxToSp(float size, Context context) {
        return size / context.getResources().getDisplayMetrics().scaledDensity;
    }

    public static float dpiFromPx(float size, int densityDpi) {
        float densityRatio = (float) densityDpi / DisplayMetrics.DENSITY_DEFAULT;
        return (size / densityRatio);
    }

    /** Converts a dp value to pixels for the current device. */
    public static int dpToPx(float dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    /**
     * Ensures that a value is within given bounds. Specifically:
     * If value is less than lowerBound, return lowerBound; else if value is greater than upperBound,
     * return upperBound; else return value unchanged.
     */
    public static int boundToRange(int value, int lowerBound, int upperBound) {
        return Math.max(lowerBound, Math.min(value, upperBound));
    }

    /**
     * @see #boundToRange(int, int, int).
     */
    public static float boundToRange(float value, float lowerBound, float upperBound) {
        return Math.max(lowerBound, Math.min(value, upperBound));
    }

    public static boolean isWallpaperAllowed(Context context) {
        return context.getSystemService(WallpaperManager.class).isSetWallpaperAllowed();
    }

    public static boolean isBinderSizeError(Exception e) {
        return e.getCause() instanceof TransactionTooLargeException
                || e.getCause() instanceof DeadObjectException;
    }

    /**
     * Utility method to post a runnable on the handler, skipping the synchronization barriers.
     */
    public static void postAsyncCallback(Handler handler, Runnable callback) {
        Message msg = Message.obtain(handler, callback);
        msg.setAsynchronous(true);
        handler.sendMessage(msg);
    }

    /**
     * Utility method to allow background activity launch for the provided activity options
     */
    public static ActivityOptions allowBGLaunch(ActivityOptions options) {
        if (ATLEAST_U) {
            options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        }
        return options;
    }

    public static float squaredHypot(float x, float y) {
        return x * x + y * y;
    }

    public static float squaredTouchSlop(Context context) {
        float slop = ViewConfiguration.get(context).getScaledTouchSlop();
        return slop * slop;
    }

    public static Rect getViewBounds(@NonNull View v) {
        int[] pos = new int[2];
        v.getLocationOnScreen(pos);
        return new Rect(pos[0], pos[1], pos[0] + v.getWidth(), pos[1] + v.getHeight());
    }

    /**
     * Returns whether mouse interaction changes intended for the desktop form factor should be
     * enabled.
     */
    public static boolean shouldEnableMouseInteractionChanges(Context context) {
        return enableMouseInteractionChanges() && context.getResources().getBoolean(
                R.bool.desktop_form_factor);
    }

    /**
     * Checks whether the full workspace blur and zoom effects are disabled behind surfaces like the
     * "All apps" panel or the widget picker.
     */
    public static boolean shouldReduceWorkspaceBlurUsage(Context context) {
        return reduceWorkspaceBlurUsage() && context.getResources().getBoolean(
                R.bool.reduce_workspace_blur_usage);
    }
}
