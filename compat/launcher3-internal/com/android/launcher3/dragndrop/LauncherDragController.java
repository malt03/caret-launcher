/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_EXIT_DELAY;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.widget.util.WidgetDragScaleUtils;

import java.util.function.Consumer;

/**
 * Drag controller for Launcher activity
 */
/**
 * Stand-in for Launcher3's LauncherDragController (android-17.0.0_r1), made from
 * LauncherDragController.java by deleting fling to delete (FlingToDeleteHelper); changes are
 * marked with "TextLauncher:".
 */
public class LauncherDragController extends DragController {

    public static final String TAG = "LauncherDragController";

    // TextLauncher: no fling to delete (FlingToDeleteHelper).
    private final Launcher mLauncher;

    public LauncherDragController(Launcher launcher) {
        super(launcher);
        mLauncher = launcher;
    }

    @Override
    protected DragView createDragView(@Nullable Drawable drawable, @Nullable View view,
            DraggableView originalView, ItemInfo dragInfo, int dragLayerX, int dragLayerY,
            Rect dragRegion, float initialDragViewScale, float dragViewScaleOnDrop,
            boolean allowSpringDrawable) {
        final int registrationX = mMotionDown.x - dragLayerX;
        final int registrationY = mMotionDown.y - dragLayerY;

        // TextLauncher: the Launcher stand-in is not a Context.
        final Resources res = mLauncher.asContext().getResources();
        final float scalePx;
        if (originalView.getViewType() == DraggableView.DRAGGABLE_WIDGET) {
            scalePx = mIsInPreDrag ? 0f : getWidgetDragScalePx(drawable, view, dragInfo);
        } else {
            scalePx = mIsInPreDrag ? res.getDimensionPixelSize(R.dimen.pre_drag_view_scale) : 0f;
        }
        // TextLauncher: DragView in place of LauncherDragView, which hides the drag view in the
        // launcher states that TextLauncher does not have while dragging.
        return drawable != null
                ? new DragView(
                mLauncher,
                drawable,
                registrationX,
                registrationY,
                initialDragViewScale,
                dragViewScaleOnDrop,
                scalePx,
                allowSpringDrawable)
                : new DragView(
                        mLauncher,
                        view,
                        view.getMeasuredWidth(),
                        view.getMeasuredHeight(),
                        registrationX,
                        registrationY,
                        initialDragViewScale,
                        dragViewScaleOnDrop,
                        scalePx,
                        allowSpringDrawable);
    }

    @Override
    protected void onDragViewInitialized() {
        mLauncher.getDragLayer().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (!isItemPinnable() || (!mIsInPreDrag && !mLauncher.isTouchInProgress()
                && mOptions.simulatedDndStartPoint == null)) {
            // If it is an internal drag and the touch is already complete, cancel immediately
            MAIN_EXECUTOR.post(this::cancelDrag);
        }
    }

    /**
     * Returns the scale in terms of pixels (to be applied on width) to scale the preview
     * during drag and drop.
     */
    public float getWidgetDragScalePx(@Nullable Drawable drawable, @Nullable View view,
            ItemInfo dragInfo) {
        float draggedViewWidthPx = 0;
        float draggedViewHeightPx = 0;

        if (view != null) {
            draggedViewWidthPx = view.getMeasuredWidth();
            draggedViewHeightPx = view.getMeasuredHeight();
        } else if (drawable != null) {
            draggedViewWidthPx = drawable.getIntrinsicWidth();
            draggedViewHeightPx = drawable.getIntrinsicHeight();
        }

        // TextLauncher: the Launcher stand-in is not a Context.
        return WidgetDragScaleUtils.getWidgetDragScalePx(mLauncher.asContext(),
                mLauncher.getDeviceProfile(),
                draggedViewWidthPx, draggedViewHeightPx, dragInfo);
    }

    @Override
    public String dump() {
        return TAG;
    }

    @Override
    protected void exitDrag() {
        // TextLauncher: no edit mode state (EDIT_MODE).
        if (!mIsInPreDrag) {
            mLauncher.getStateManager().goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
        }
    }

    // TextLauncher: no fling to delete (endWithFlingAnimation, endDrag).

    @Override
    protected DropTarget getDefaultDropTarget(int[] dropCoordinates) {
        mLauncher.getDragLayer().mapCoordInSelfToDescendant(mLauncher.getWorkspace(),
                dropCoordinates);
        return mLauncher.getWorkspace();
    }
}
