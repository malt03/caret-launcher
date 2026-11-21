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

package com.android.launcher3.dragndrop;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.View;
import android.view.animation.Interpolator;

import com.android.app.animation.Interpolators;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.views.BaseDragLayer;

/**
 * Stand-in for Launcher3's DragLayer (android-17.0.0_r1): the drag layer of the home screen, with
 * the drop animations copied from DragLayer.java. TextLauncher's drag layer sits over the home
 * screen instead of holding it; see {@link BaseDragLayer}.
 */
public class DragLayer extends BaseDragLayer<Launcher> {

    public static final int ANIMATION_END_DISAPPEAR = 0;
    public static final int ANIMATION_END_REMAIN_VISIBLE = 2;

    private DragController mDragController;
    private Animator mDropAnim = null;
    private DragView mDropView = null;

    public void setDragController(DragController dragController) {
        mDragController = dragController;
    }

    /**
     * TextLauncher: DragLayer reads the child's position from its ShortcutAndWidgetContainer. Here
     * the child's parent (a {@link com.android.launcher3.CellLayout.Children}) lays it out at
     * paddingLeft + lp.x, paddingTop + lp.y.
     */
    public void animateViewIntoPosition(DragView dragView,
            final View child, int duration, View anchorView) {
        CellLayoutLayoutParams lp =
                (CellLayoutLayoutParams) child.getLayoutParams();
        View childParent = (View) child.getParent();
        if (childParent instanceof CellLayout.Children) {
            CellLayout.Children container =
                    (CellLayout.Children) childParent;
            container.measureChild(child);
            container.layoutChild(child);
        }

        float coord[] = new float[2];
        float childScale = child.getScaleX();

        coord[0] = childParent.getPaddingLeft() + lp.x + (child.getMeasuredWidth() * (1 - childScale) / 2);
        coord[1] = childParent.getPaddingTop() + lp.y + (child.getMeasuredHeight() * (1 - childScale) / 2);

        // Since the child hasn't necessarily been laid out, we force the lp to be updated with
        // the correct coordinates (above) and use these to determine the final location
        float scale = getDescendantCoordRelativeToSelf((View) child.getParent(), coord);

        // We need to account for the scale of the child itself, as the above only accounts for
        // the scale in parents.
        scale *= childScale;
        int toX = Math.round(coord[0]);
        int toY = Math.round(coord[1]);

        float toScale = scale;

        if (child instanceof DraggableView) {
            // This code is fairly subtle. Please verify drag and drop is pixel-perfect in a number
            // of scenarios before modifying (from all apps, from workspace, different grid-sizes,
            // shortcuts from in and out of Launcher etc).
            DraggableView d =
                    (DraggableView) child;
            Rect destRect = new Rect();
            d.getWorkspaceVisualDragBounds(destRect);

            // In most cases this additional scale factor should be a no-op (1). It mainly accounts
            // for alternate grids where the source and destination icon sizes are different
            toScale *= ((1f * destRect.width())
                    / (dragView.getMeasuredWidth() - dragView.getBlurSizeOutline()));

            // This accounts for the offset of the DragView created by scaling it about its
            // center as it animates into place.
            float scaleShiftX = dragView.getMeasuredWidth() * (1 - toScale) / 2;
            float scaleShiftY = dragView.getMeasuredHeight() * (1 - toScale) / 2;

            toX += scale * destRect.left - toScale * dragView.getBlurSizeOutline() / 2 - scaleShiftX;
            toY += scale * destRect.top - toScale * dragView.getBlurSizeOutline() / 2 - scaleShiftY;
        }

        child.setVisibility(INVISIBLE);
        Runnable onCompleteRunnable = () -> child.setVisibility(VISIBLE);
        animateViewIntoPosition(dragView, toX, toY, 1, toScale, toScale,
                onCompleteRunnable, ANIMATION_END_DISAPPEAR, duration, anchorView);
    }

    public void animateViewIntoPosition(final DragView view,
            final int toX, final int toY, float finalAlpha,
            float finalScaleX, float finalScaleY, Runnable onCompleteRunnable,
            int animationEndStyle, int duration, View anchorView) {
        Rect to = new Rect(toX, toY, toX + view.getMeasuredWidth(), toY + view.getMeasuredHeight());
        animateView(view, to, finalAlpha, finalScaleX, finalScaleY, duration,
                null, onCompleteRunnable, animationEndStyle, anchorView);
    }

    /**
     * TextLauncher: DragLayer builds this with PendingAnimation. Here the same animators (values,
     * interpolators, duration) are put in a plain AnimatorSet; the anchor scroll compensation is
     * left out since the home grid does not scroll horizontally.
     */
    public void animateView(final DragView view, final Rect to,
            final float finalAlpha, final float finalScaleX, final float finalScaleY, int duration,
            final Interpolator motionInterpolator,
            final Runnable onCompleteRunnable, final int animationEndStyle, View anchorView) {
        view.cancelAnimation();
        view.requestLayout();

        final int[] from = getViewLocationRelativeToSelf(view);

        // Calculate the duration of the animation based on the object's distance
        final float dist = (float) Math.hypot(to.left - from[0], to.top - from[1]);
        final Resources res = getResources();
        final float maxDist = (float) res.getInteger(R.integer.config_dropAnimMaxDist);

        // If duration < 0, this is a cue to compute the duration based on the distance
        if (duration < 0) {
            duration = res.getInteger(R.integer.config_dropAnimMaxDuration);
            if (dist < maxDist) {
                duration *= Interpolators.DECELERATE_1_5
                        .getInterpolation(dist / maxDist);
            }
            duration = Math.max(duration,
                    res.getInteger(R.integer.config_dropAnimMinDuration));
        }

        // Fall back to cubic ease out interpolator for the animation if none is specified
        TimeInterpolator interpolator = motionInterpolator == null
                ? Interpolators.DECELERATE_1_5 : motionInterpolator;

        AnimatorSet anim = new AnimatorSet();
        anim.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, finalScaleX),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, finalScaleY),
                ObjectAnimator.ofFloat(view, View.ALPHA, finalAlpha),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, to.top),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_X, to.left));
        anim.setDuration(duration);
        anim.setInterpolator(interpolator);
        if (onCompleteRunnable != null) {
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onCompleteRunnable.run();
                }
            });
        }
        playDropAnimation(view, anim, animationEndStyle);
    }

    public void playDropAnimation(final DragView view,
            Animator animator, int animationEndStyle) {
        // Clean up the previous animations
        if (mDropAnim != null) mDropAnim.cancel();

        // Show the drop view if it was previously hidden
        mDropView = view;
        // Create and start the animation
        mDropAnim = animator;
        mDropAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDropAnim = null;
            }
        });
        if (animationEndStyle == ANIMATION_END_DISAPPEAR) {
            mDropAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    clearAnimatedView();
                }
            });
        }
        mDropAnim.start();
    }

    public DragView clearAnimatedView() {
        if (mDropAnim != null) {
            mDropAnim.cancel();
        }
        mDropAnim = null;
        if (mDropView != null) {
            mDragController.onDeferredEndDrag(mDropView);
        }
        DragView ret = mDropView;
        mDropView = null;
        invalidate();
        return ret;
    }

    public DragLayer(Context context) {
        super(context);
    }
}
