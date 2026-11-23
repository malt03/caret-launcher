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
package com.android.launcher3.touch;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.Utilities.shouldEnableMouseInteractionChanges;

import android.graphics.PointF;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.util.TouchUtil;

/**
 * Helper class to handle touch on empty space in workspace and show options popup on long press
 *
 * <p>Stand-in for Launcher3's WorkspaceTouchListener (android-17.0.0_r1), made from
 * WorkspaceTouchListener.java by deleting what TextLauncher does not use (box selection, split
 * selection, logging); changes are marked with "TextLauncher:". The home screen scrolls
 * vertically, so the listener is on the scroll view, which stands in for the paged Workspace.
 */
public class WorkspaceTouchListener extends GestureDetector.SimpleOnGestureListener
        implements OnTouchListener {

    /**
     * STATE_PENDING_PARENT_INFORM is the state between longPress performed & the next motionEvent.
     * This next event is used to send an ACTION_CANCEL to Workspace, to that it clears any
     * temporary scroll state. After that, the state is set to COMPLETED, and we just eat up all
     * subsequent motion events.
     */
    private static final int STATE_CANCELLED = 0;
    private static final int STATE_REQUESTED = 1;
    private static final int STATE_PENDING_PARENT_INFORM = 2;
    private static final int STATE_COMPLETED = 3;

    private final Launcher mLauncher;
    private final View mWorkspace; // TextLauncher: the scroll view of the home screen.
    private final PointF mTouchDownPoint = new PointF();
    private final float mTouchSlop;

    private int mLongPressState = STATE_CANCELLED;

    private final GestureDetector mGestureDetector;

    public WorkspaceTouchListener(Launcher launcher, View workspace) { // TextLauncher: see above.
        mLauncher = launcher;
        mWorkspace = workspace;
        // Use twice the touch slop as we are looking for long press which is more
        // likely to cause movement.
        // TextLauncher: the launcher is not a Context; its context is (asContext).
        mTouchSlop = 2 * ViewConfiguration.get(launcher.asContext()).getScaledTouchSlop();
        mGestureDetector = new GestureDetector(workspace.getContext(), this);
    }

    @Override
    public boolean onTouch(View view, MotionEvent ev) {
        mGestureDetector.onTouchEvent(ev);

        int action = ev.getActionMasked();
        if (action == ACTION_DOWN) {
            // Check if we can handle long press.
            boolean handleLongPress = canHandleLongPress();

            // TextLauncher: the home screen is laid out between the status bar and the search
            // field, so a long press is not kept off the insets and the screen's edges.

            if (handleLongPress) {
                mLongPressState = STATE_REQUESTED;
                mTouchDownPoint.set(ev.getX(), ev.getY());
                // Mouse right button's ACTION_DOWN should immediately show menu
                if (TouchUtil.isMouseRightClickDownOrMove(ev)) {
                    maybeShowMenu();
                    return true;
                }
            }

            mWorkspace.onTouchEvent(ev);
            mDownScrollY = mWorkspace.getScrollY(); // TextLauncher: see isHandlingTouch.
            // Return true to keep receiving touch events
            return true;
        }

        if (mLongPressState == STATE_PENDING_PARENT_INFORM) {
            // Inform the workspace to cancel touch handling
            ev.setAction(ACTION_CANCEL);
            mWorkspace.onTouchEvent(ev);

            ev.setAction(action);
            mLongPressState = STATE_COMPLETED;
        }

        final boolean result;
        if (mLongPressState == STATE_COMPLETED) {
            // We have handled the touch, so workspace does not need to know anything anymore.
            result = true;
        } else if (mLongPressState == STATE_REQUESTED) {
            mWorkspace.onTouchEvent(ev);
            if (isHandlingTouch()) {
                cancelLongPress();
            } else if (action == ACTION_MOVE && PointF.length(
                    mTouchDownPoint.x - ev.getX(), mTouchDownPoint.y - ev.getY()) > mTouchSlop) {
                cancelLongPress();
            }

            result = true;
        } else {
            // TextLauncher: the all apps sheet covers the home screen, so no tap outside it comes
            // here; let workspace handle the touch as usual.
            result = false;
        }

        if (action == ACTION_UP || action == ACTION_POINTER_UP) {
            if (!isHandlingTouch()) {
                // TextLauncher: one page, which is always there.
                mLauncher.getWorkspace().onWallpaperTap(ev);
            }
        }

        if (action == ACTION_UP || action == ACTION_CANCEL) {
            cancelLongPress();
        }

        return result;
    }

    private boolean canHandleLongPress() {
        return AbstractFloatingView.getTopOpenView(mLauncher) == null
                && mLauncher.isInNormalState(); // TextLauncher: isInState(NORMAL).
    }

    private void cancelLongPress() {
        mLongPressState = STATE_CANCELLED;
    }

    @Override
    public void onLongPress(MotionEvent event) {
        if (event.getSource() == InputDevice.SOURCE_MOUSE && shouldEnableMouseInteractionChanges(
                mWorkspace.getContext())) {
            // Stop mouse long press events from showing the menu.
            return;
        }
        maybeShowMenu();
    }

    private void maybeShowMenu() {
        if (mLongPressState == STATE_REQUESTED) {
            if (canHandleLongPress()) {
                mLongPressState = STATE_PENDING_PARENT_INFORM;
                mWorkspace.getParent().requestDisallowInterceptTouchEvent(true);

                mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                // TextLauncher: the point in the drag layer's coordinates, as the scroll view is
                // not at the drag layer's origin.
                float[] point = {mTouchDownPoint.x, mTouchDownPoint.y};
                mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(mWorkspace, point);
                mLauncher.showDefaultOptions(point[0], point[1]);
            } else {
                cancelLongPress();
            }
        }
    }

    // TextLauncher: members TextLauncher adds.

    /** The scroll view's scroll position when the touch went down. */
    private int mDownScrollY;

    /**
     * PagedView.isHandlingTouch for the scroll view: it has scrolled since the touch went down.
     */
    private boolean isHandlingTouch() {
        return mWorkspace.getScrollY() != mDownScrollY;
    }
}
