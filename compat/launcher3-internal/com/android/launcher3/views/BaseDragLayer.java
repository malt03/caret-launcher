package com.android.launcher3.views;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.launcher3.Utilities;

/**
 * Stand-in for Launcher3's BaseDragLayer (android-17.0.0_r1): the layer that holds floating views
 * such as folders and dragged icons. Members are copied from BaseDragLayer.java.
 */
public class BaseDragLayer<T> extends FrameLayout {
    private final float[] mTmpRectPoints = new float[4];
    private final float[] mTmpXY = new float[2];

    /** Inverse of {@link #getDescendantCoordRelativeToSelf(View, float[])}. */
    public void mapCoordInSelfToDescendant(View descendant, float[] coord) {
        Utilities.mapCoordInSelfToDescendant(descendant, mappingRootFor(descendant), coord);
    }

    /** Inverse of {@link #getDescendantCoordRelativeToSelf(View, float[])}. */
    public void mapCoordInSelfToDescendant(View descendant, int[] coord) {
        mTmpXY[0] = coord[0];
        mTmpXY[1] = coord[1];
        Utilities.mapCoordInSelfToDescendant(descendant, mappingRootFor(descendant), mTmpXY);
        Utilities.roundArray(mTmpXY, coord);
    }

    /**
     * TextLauncher: in Launcher3 every view is inside the DragLayer. Here the drag layer is an
     * overlay over the home screen, at the origin of its parent and as large; views outside it are
     * mapped through the parent, whose coordinates are the same as this layer's.
     */
    private View mappingRootFor(View view) {
        for (android.view.ViewParent p = view.getParent(); p instanceof View; p = p.getParent()) {
            if (p == this) return this;
        }
        return view == this ? this : (View) getParent();
    }

    public BaseDragLayer(Context context) {
        super(context);
    }

    public float getDescendantRectRelativeToSelf(View descendant, Rect r) {
        mTmpRectPoints[0] = 0;
        mTmpRectPoints[1] = 0;
        mTmpRectPoints[2] = descendant.getWidth();
        mTmpRectPoints[3] = descendant.getHeight();
        float s = getDescendantCoordRelativeToSelf(descendant, mTmpRectPoints);
        r.left = Math.round(Math.min(mTmpRectPoints[0], mTmpRectPoints[2]));
        r.top = Math.round(Math.min(mTmpRectPoints[1], mTmpRectPoints[3]));
        r.right = Math.round(Math.max(mTmpRectPoints[0], mTmpRectPoints[2]));
        r.bottom = Math.round(Math.max(mTmpRectPoints[1], mTmpRectPoints[3]));
        return s;
    }

    public float getDescendantCoordRelativeToSelf(View descendant, float[] coord) {
        return Utilities.getDescendantCoordRelativeToAncestor(descendant, mappingRootFor(descendant), coord, false);
    }

    public float getLocationInDragLayer(View child, int[] loc) {
        loc[0] = 0;
        loc[1] = 0;
        return getDescendantCoordRelativeToSelf(child, loc);
    }

    public float getDescendantCoordRelativeToSelf(View descendant, int[] coord) {
        mTmpXY[0] = coord[0];
        mTmpXY[1] = coord[1];
        float scale = getDescendantCoordRelativeToSelf(descendant, mTmpXY);
        Utilities.roundArray(mTmpXY, coord);
        return scale;
    }

    public void getViewRectRelativeToSelf(View v, Rect r) {
        int[] loc = getViewLocationRelativeToSelf(v);
        r.set(loc[0], loc[1], loc[0] + v.getMeasuredWidth(), loc[1] + v.getMeasuredHeight());
    }

    protected int[] getViewLocationRelativeToSelf(View v) {
        int[] loc = new int[2];
        getLocationInWindow(loc);
        int x = loc[0];
        int y = loc[1];

        v.getLocationInWindow(loc);
        loc[0] -= x;
        loc[1] -= y;
        return loc;
    }

    // ---- From dragndrop/DragLayer.java (android-17.0.0_r1) ----

    public static final int ANIMATION_END_DISAPPEAR = 0;
    public static final int ANIMATION_END_REMAIN_VISIBLE = 2;

    private com.android.launcher3.dragndrop.DragController mDragController;
    private android.animation.Animator mDropAnim = null;
    private com.android.launcher3.dragndrop.DragView mDropView = null;

    public void setDragController(com.android.launcher3.dragndrop.DragController dragController) {
        mDragController = dragController;
    }

    /**
     * TextLauncher: DragLayer reads the child's position from its ShortcutAndWidgetContainer. Here
     * the child's parent (a {@link com.android.launcher3.CellLayout.Children}) lays it out at
     * paddingLeft + lp.x, paddingTop + lp.y.
     */
    public void animateViewIntoPosition(com.android.launcher3.dragndrop.DragView dragView,
            final View child, int duration, View anchorView) {
        com.android.launcher3.celllayout.CellLayoutLayoutParams lp =
                (com.android.launcher3.celllayout.CellLayoutLayoutParams) child.getLayoutParams();
        View childParent = (View) child.getParent();
        if (childParent instanceof com.android.launcher3.CellLayout.Children) {
            com.android.launcher3.CellLayout.Children container =
                    (com.android.launcher3.CellLayout.Children) childParent;
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

        if (child instanceof com.android.launcher3.dragndrop.DraggableView) {
            // This code is fairly subtle. Please verify drag and drop is pixel-perfect in a number
            // of scenarios before modifying (from all apps, from workspace, different grid-sizes,
            // shortcuts from in and out of Launcher etc).
            com.android.launcher3.dragndrop.DraggableView d =
                    (com.android.launcher3.dragndrop.DraggableView) child;
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

    public void animateViewIntoPosition(final com.android.launcher3.dragndrop.DragView view,
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
    public void animateView(final com.android.launcher3.dragndrop.DragView view, final Rect to,
            final float finalAlpha, final float finalScaleX, final float finalScaleY, int duration,
            final android.view.animation.Interpolator motionInterpolator,
            final Runnable onCompleteRunnable, final int animationEndStyle, View anchorView) {
        view.cancelAnimation();
        view.requestLayout();

        final int[] from = getViewLocationRelativeToSelf(view);

        // Calculate the duration of the animation based on the object's distance
        final float dist = (float) Math.hypot(to.left - from[0], to.top - from[1]);
        final android.content.res.Resources res = getResources();
        final float maxDist = (float) res.getInteger(com.android.launcher3.R.integer.config_dropAnimMaxDist);

        // If duration < 0, this is a cue to compute the duration based on the distance
        if (duration < 0) {
            duration = res.getInteger(com.android.launcher3.R.integer.config_dropAnimMaxDuration);
            if (dist < maxDist) {
                duration *= com.android.app.animation.Interpolators.DECELERATE_1_5
                        .getInterpolation(dist / maxDist);
            }
            duration = Math.max(duration,
                    res.getInteger(com.android.launcher3.R.integer.config_dropAnimMinDuration));
        }

        // Fall back to cubic ease out interpolator for the animation if none is specified
        android.animation.TimeInterpolator interpolator = motionInterpolator == null
                ? com.android.app.animation.Interpolators.DECELERATE_1_5 : motionInterpolator;

        android.animation.AnimatorSet anim = new android.animation.AnimatorSet();
        anim.playTogether(
                android.animation.ObjectAnimator.ofFloat(view, View.SCALE_X, finalScaleX),
                android.animation.ObjectAnimator.ofFloat(view, View.SCALE_Y, finalScaleY),
                android.animation.ObjectAnimator.ofFloat(view, View.ALPHA, finalAlpha),
                android.animation.ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, to.top),
                android.animation.ObjectAnimator.ofFloat(view, View.TRANSLATION_X, to.left));
        anim.setDuration(duration);
        anim.setInterpolator(interpolator);
        if (onCompleteRunnable != null) {
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    onCompleteRunnable.run();
                }
            });
        }
        playDropAnimation(view, anim, animationEndStyle);
    }

    public void playDropAnimation(final com.android.launcher3.dragndrop.DragView view,
            android.animation.Animator animator, int animationEndStyle) {
        // Clean up the previous animations
        if (mDropAnim != null) mDropAnim.cancel();

        // Show the drop view if it was previously hidden
        mDropView = view;
        // Create and start the animation
        mDropAnim = animator;
        mDropAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                mDropAnim = null;
            }
        });
        if (animationEndStyle == ANIMATION_END_DISAPPEAR) {
            mDropAnim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    clearAnimatedView();
                }
            });
        }
        mDropAnim.start();
    }

    public com.android.launcher3.dragndrop.DragView clearAnimatedView() {
        if (mDropAnim != null) {
            mDropAnim.cancel();
        }
        mDropAnim = null;
        if (mDropView != null) {
            com.android.launcher3.dragndrop.DragLayerAccess.onDeferredEndDrag(mDragController, mDropView);
        }
        com.android.launcher3.dragndrop.DragView ret = mDropView;
        mDropView = null;
        invalidate();
        return ret;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            final FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) child.getLayoutParams();
            if (flp instanceof LayoutParams) {
                final LayoutParams lp = (LayoutParams) flp;
                if (lp.customPosition) {
                    child.layout(lp.x, lp.y, lp.x + lp.width, lp.y + lp.height);
                }
            }
        }
    }

    @Override
    public LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof FrameLayout.LayoutParams;
    }

    public static class LayoutParams extends FrameLayout.LayoutParams {
        public int x, y;
        public boolean customPosition = false;

        public LayoutParams(Context c, android.util.AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.LayoutParams lp) {
            super(lp);
        }
    }
}
