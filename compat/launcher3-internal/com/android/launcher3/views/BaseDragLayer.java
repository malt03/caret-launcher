/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3.views;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_OUTSIDE;
import static android.view.MotionEvent.ACTION_UP;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.TouchController;

/**
 * Stand-in for Launcher3's BaseDragLayer (android-17.0.0_r1): the layer that holds floating views
 * such as folders and dragged icons. Members are copied from BaseDragLayer.java.
 */
public class BaseDragLayer<T> extends InsettableFrameLayout {
    protected final Rect mHitRect = new Rect();
    protected final RectF mSystemGestureRegion = new RectF();
    protected TouchController[] mControllers = new TouchController[0];
    protected TouchController mActiveController;

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
        for (ViewParent p = view.getParent(); p instanceof View; p = p.getParent()) {
            if (p == this) return this;
        }
        return view == this ? this : (View) getParent();
    }

    public BaseDragLayer(Context context) {
        super(context, null);
    }

    public BaseDragLayer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** TextLauncher: set where Launcher3's DragLayer.recreateControllers builds its controllers. */
    public void setControllers(TouchController... controllers) {
        mControllers = controllers;
    }

    /**
     * Same as {@link #isEventOverView(View, MotionEvent, View)} where evView == this drag layer.
     */
    public boolean isEventOverView(View view, MotionEvent ev) {
        getDescendantRectRelativeToSelf(view, mHitRect);
        return mHitRect.contains((int) ev.getX(), (int) ev.getY());
    }

    /**
     * Given a motion event in evView's coordinates, return whether the event is within another
     * view's bounds.
     */
    public boolean isEventOverView(View view, MotionEvent ev, View evView) {
        int[] xy = new int[] {(int) ev.getX(), (int) ev.getY()};
        getDescendantCoordRelativeToSelf(evView, xy);
        getDescendantRectRelativeToSelf(view, mHitRect);
        return mHitRect.contains(xy[0], xy[1]);
    }

    // ---- Touch controllers (BaseDragLayer.onInterceptTouchEvent / onTouchEvent) ----
    //
    // TextLauncher: this layer is an overlay over the home screen, not the parent of everything,
    // so it does not see touches that go to the views below it. The parent passes every touch here
    // first, with ViewGroup's interception rules (interceptTouchEventFromParent until it returns
    // true, then touchEventFromParent); touches on this layer's own children still reach them as
    // usual.

    private boolean mInterceptingFromParent;

    /** TextLauncher: called by the parent for every touch; returns true while it takes them. */
    public boolean dispatchTouchEventFromParent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mInterceptingFromParent = false;
        }
        if (!mInterceptingFromParent) {
            mInterceptingFromParent = interceptTouchEventFromParent(ev);
        } else {
            touchEventFromParent(ev);
        }
        return mInterceptingFromParent;
    }

    /** BaseDragLayer.onInterceptTouchEvent */
    private boolean interceptTouchEventFromParent(MotionEvent ev) {
        int action = ev.getAction();

        if (action == MotionEvent.ACTION_DOWN) {
            ActivityContext.lookupContext(getContext()).finishAutoCancelActionMode();
        }
        // TextLauncher: no touch complete listener.
        return findActiveController(ev);
    }

    protected boolean isEventWithinSystemGestureRegion(MotionEvent ev) {
        final float x = ev.getX();
        final float y = ev.getY();

        return x >= mSystemGestureRegion.left && x < getWidth() - mSystemGestureRegion.right
                && y >= mSystemGestureRegion.top && y < getHeight() - mSystemGestureRegion.bottom;
    }

    private TouchController findControllerToHandleTouch(MotionEvent ev) {
        AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(
                ActivityContext.lookupContext(getContext()));
        if (topView != null
                && (isEventWithinSystemGestureRegion(ev)
                || topView.canInterceptEventsInSystemGestureRegion())
                && topView.onControllerInterceptTouchEvent(ev)) {
            return topView;
        }

        for (TouchController controller : mControllers) {
            if (controller.onControllerInterceptTouchEvent(ev)) {
                return controller;
            }
        }
        return null;
    }

    protected boolean findActiveController(MotionEvent ev) {
        mActiveController = null;
        // TextLauncher: no gesture region or proxy dispatching, so a controller is always looked for.
        mActiveController = findControllerToHandleTouch(ev);
        return mActiveController != null;
    }

    /** BaseDragLayer.onTouchEvent */
    private boolean touchEventFromParent(MotionEvent ev) {
        if (mActiveController != null && ev.getAction() != ACTION_OUTSIDE) {
            // For some reason, once we intercept touches and have an mActiveController, we won't
            // get onInterceptTouchEvent() for ACTION_OUTSIDE. Thus, we must recalculate a new
            // TouchController (if any) to handle the ACTION_OUTSIDE here in onTouchEvent() as well.
            return mActiveController.onControllerTouchEvent(ev);
        } else {
            // In case no child view handled the touch event, we may not get onIntercept anymore
            return findActiveController(ev);
        }
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        if (child instanceof AbstractFloatingView) {
            // Handles the case where the view is removed without being properly closed.
            // This can happen if something goes wrong during a state change/transition.
            AbstractFloatingView floatingView = (AbstractFloatingView) child;
            if (floatingView.isOpen()) {
                postDelayed(() -> floatingView.close(false),
                        com.android.launcher3.util.window.RefreshRateTracker.getSingleFrameMs(getContext()));
            }
        }
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
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    // Override to allow type-checking of LayoutParams.
    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    public static class LayoutParams extends InsettableFrameLayout.LayoutParams {
        public int x, y;
        public boolean customPosition = false;

        public LayoutParams(Context c, AttributeSet attrs) {
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
