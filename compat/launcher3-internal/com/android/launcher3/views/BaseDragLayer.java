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
public class BaseDragLayer extends FrameLayout {
    private final float[] mTmpRectPoints = new float[4];

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
        return Utilities.getDescendantCoordRelativeToAncestor(descendant, this, coord, false);
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
