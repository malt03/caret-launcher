
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

import static android.animation.ObjectAnimator.ofFloat;

import static com.android.app.animation.Interpolators.DECELERATE_1_5;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.Utilities.mapRange;
import static com.android.launcher3.compat.AccessibilityManagerCompat.sendCustomAccessibilityEvent;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.TypeEvaluator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DropTargetBar;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

import java.util.ArrayList;

/**
 * A ViewGroup that coordinates dragging across its descendants
 *
 * <p>Stand-in for Launcher3's DragLayer (android-17.0.0_r1), made from DragLayer.java by deleting
 * what TextLauncher does not use (everything but the drop animations and accessibility: setup,
 * touch handling, child drawing order, the focus outline, the scrim and the overlay callbacks); changes are marked with "TextLauncher:". TextLauncher's drag layer sits over the
 * home screen instead of holding it; see {@link BaseDragLayer}.
 */
// TextLauncher: not a LauncherOverlayCallbacks (no -1 screen overlay).
public class DragLayer extends BaseDragLayer<Launcher> {

    public static final int ANIMATION_END_DISAPPEAR = 0;
    public static final int ANIMATION_END_REMAIN_VISIBLE = 2;

    private DragController mDragController;

    // Variables relating to animation of views after drop
    private Animator mDropAnim = null;

    private DragView mDropView = null;

    private int mTopViewIndex;
    private int mChildCountOnLastUpdate = -1;

    private boolean mHoverPointClosesFolder = false;

    /**
     * TextLauncher: created in code, over the home screen. No alpha channels, multitouch is not
     * kept from splitting, and the home grid draws the focus outline.
     *
     * @param context The application's context.
     */
    public DragLayer(Context context) {
        super(context);

        setChildrenDrawingOrderEnabled(true);
    }

    private boolean isEventOverAccessibleDropTargetBar(MotionEvent ev) {
        return isInAccessibleDrag() && isEventOverView(getLauncher().getDropTargetBar(), ev);
    }

    @Override
    public boolean onInterceptHoverEvent(MotionEvent ev) {
        // TextLauncher: no mContainer field (getLauncher).
        Launcher container = getLauncher();
        if (container == null || container.getWorkspace() == null) {
            return false;
        }
        AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(container);
        if (!(topView instanceof Folder)) {
            return false;
        } else {
            AccessibilityManager accessibilityManager = (AccessibilityManager)
                    getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (accessibilityManager.isTouchExplorationEnabled()) {
                final int action = ev.getAction();
                boolean isOverFolderOrSearchBar;
                switch (action) {
                    case MotionEvent.ACTION_HOVER_ENTER:
                        isOverFolderOrSearchBar = isEventOverView(topView, ev) ||
                                isEventOverAccessibleDropTargetBar(ev);
                        if (!isOverFolderOrSearchBar) {
                            sendTapOutsideFolderAccessibilityEvent();
                            mHoverPointClosesFolder = true;
                            return true;
                        }
                        mHoverPointClosesFolder = false;
                        break;
                    case MotionEvent.ACTION_HOVER_MOVE:
                        isOverFolderOrSearchBar = isEventOverView(topView, ev) ||
                                isEventOverAccessibleDropTargetBar(ev);
                        if (!isOverFolderOrSearchBar && !mHoverPointClosesFolder) {
                            sendTapOutsideFolderAccessibilityEvent();
                            mHoverPointClosesFolder = true;
                            return true;
                        } else if (!isOverFolderOrSearchBar) {
                            return true;
                        }
                        mHoverPointClosesFolder = false;
                }
            }
        }
        return false;
    }

    // TextLauncher: folders have no name to edit (folder_tap_to_rename).
    private void sendTapOutsideFolderAccessibilityEvent() {
        int stringId = R.string.folder_tap_to_close;
        sendCustomAccessibilityEvent(
                this, AccessibilityEvent.TYPE_VIEW_FOCUSED, getContext().getString(stringId));
    }

    @Override
    public boolean onHoverEvent(MotionEvent ev) {
        // If we've received this, we've already done the necessary handling
        // in onInterceptHoverEvent. Return true to consume the event.
        return false;
    }


    private boolean isInAccessibleDrag() {
        // TextLauncher: the launcher may not be made yet (getLauncher).
        Launcher launcher = getLauncher();
        return launcher != null && launcher.getAccessibilityDelegate().isInAccessibleDrag();
    }

    @Override
    public boolean onRequestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        if (isInAccessibleDrag() && child instanceof DropTargetBar) {
            return true;
        }
        return super.onRequestSendAccessibilityEvent(child, event);
    }

    @Override
    public void addChildrenForAccessibility(ArrayList<View> childrenForAccessibility) {
        // TextLauncher: the launcher may not be made yet (getLauncher).
        if (getLauncher() == null) {
            super.addChildrenForAccessibility(childrenForAccessibility);
            return;
        }
        View topView = AbstractFloatingView.getTopOpenViewWithType(getLauncher(),
                AbstractFloatingView.TYPE_ACCESSIBLE);
        if (topView != null) {
            addAccessibleChildToList(topView, childrenForAccessibility);
            if (isInAccessibleDrag()) {
                addAccessibleChildToList(getLauncher().getDropTargetBar(),
                        childrenForAccessibility);
            }
        } else {
            super.addChildrenForAccessibility(childrenForAccessibility);
        }
    }

    public void animateViewIntoPosition(DragView dragView, final int[] pos, float alpha,
            float scaleX, float scaleY, int animationEndStyle, Runnable onFinishRunnable,
            int duration) {
        animateViewIntoPosition(dragView, pos[0], pos[1], alpha, scaleX, scaleY,
                onFinishRunnable, animationEndStyle, duration, null);
    }

    public void animateViewIntoPosition(DragView dragView, final View child, View anchorView) {
        animateViewIntoPosition(dragView, child, -1, anchorView);
    }

    public void animateViewIntoPosition(DragView dragView, final View child, int duration,
            View anchorView) {

        ShortcutAndWidgetContainer childParent = (ShortcutAndWidgetContainer) child.getParent();
        CellLayoutLayoutParams lp =  (CellLayoutLayoutParams) child.getLayoutParams();
        childParent.measureChild(child);
        childParent.layoutChild(child);

        float coord[] = new float[2];
        float childScale = child.getScaleX();

        coord[0] = lp.x + (child.getMeasuredWidth() * (1 - childScale) / 2);
        coord[1] = lp.y + (child.getMeasuredHeight() * (1 - childScale) / 2);
        // TextLauncher: no translation provider. The home grid's padding is CellLayout's, so its
        // cells start inside it.
        coord[0] += childParent.getPaddingLeft();
        coord[1] += childParent.getPaddingTop();

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
            DraggableView d = (DraggableView) child;
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

    /**
     * This method animates a view at the end of a drag and drop animation.
     */
    public void animateViewIntoPosition(final DragView view,
            final int toX, final int toY, float finalAlpha,
            float finalScaleX, float finalScaleY, Runnable onCompleteRunnable,
            int animationEndStyle, int duration, View anchorView) {
        Rect to = new Rect(toX, toY, toX + view.getMeasuredWidth(), toY + view.getMeasuredHeight());
        animateView(view, to, finalAlpha, finalScaleX, finalScaleY, duration,
                null, onCompleteRunnable, animationEndStyle, anchorView);
    }

    /**
     * This method animates a view at the end of a drag and drop animation.
     * @param view The view to be animated. This view is drawn directly into DragLayer, and so
     *        doesn't need to be a child of DragLayer.
     * @param to The final location of the view. Only the left and top parameters are used. This
*        location doesn't account for scaling, and so should be centered about the desired
*        final location (including scaling).
     * @param finalAlpha The final alpha of the view, in case we want it to fade as it animates.
     * @param finalScaleX The final scale of the view. The view is scaled about its center.
     * @param finalScaleY The final scale of the view. The view is scaled about its center.
     * @param duration The duration of the animation.
     * @param motionInterpolator The interpolator to use for the location of the view.
     * @param onCompleteRunnable Optional runnable to run on animation completion.
     * @param animationEndStyle Whether or not to fade out the view once the animation completes.
*        {@link #ANIMATION_END_DISAPPEAR} or {@link #ANIMATION_END_REMAIN_VISIBLE}.
     * @param anchorView If not null, this represents the view which the animated view stays
     */
    public void animateView(final DragView view, final Rect to,
            final float finalAlpha, final float finalScaleX, final float finalScaleY, int duration,
            final Interpolator motionInterpolator, final Runnable onCompleteRunnable,
            final int animationEndStyle, View anchorView) {
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
                duration *= DECELERATE_1_5.getInterpolation(dist / maxDist);
            }
            duration = Math.max(duration, res.getInteger(R.integer.config_dropAnimMinDuration));
        }

        // Fall back to cubic ease out interpolator for the animation if none is specified
        TimeInterpolator interpolator =
                motionInterpolator == null ? DECELERATE_1_5 : motionInterpolator;

        // Animate the view
        // TextLauncher: no PendingAnimation. The same animators are played together in an
        // AnimatorSet, which gives each of them the duration and the interpolator; the alpha is
        // animated even when it does not change, and the view's visibility is not updated.
        AnimatorSet anim = new AnimatorSet();
        anim.play(ofFloat(view, View.SCALE_X, finalScaleX));
        anim.play(ofFloat(view, View.SCALE_Y, finalScaleY));
        anim.play(ofFloat(view, View.ALPHA, finalAlpha));
        anim.play(ofFloat(view, VIEW_TRANSLATE_Y, to.top));

        ObjectAnimator xMotion = ofFloat(view, VIEW_TRANSLATE_X, to.left);
        if (anchorView != null) {
            final int startScroll = anchorView.getScrollX();
            TypeEvaluator<Float> evaluator = (f, s, e) -> mapRange(f, s, e)
                    + (anchorView.getScaleX() * (startScroll - anchorView.getScrollX()));
            xMotion.setEvaluator(evaluator);
        }
        // TextLauncher: an AnimatorSet (see above).
        anim.play(xMotion);
        anim.setDuration(duration);
        anim.setInterpolator(interpolator);
        if (onCompleteRunnable != null) {
            anim.addListener(forEndCallback(onCompleteRunnable));
        }
        playDropAnimation(view, anim, animationEndStyle);
    }

    /**
     * Runs a previously constructed drop animation
     */
    public void playDropAnimation(final DragView view, Animator animator, int animationEndStyle) {
        // Clean up the previous animations
        if (mDropAnim != null) mDropAnim.cancel();

        // Show the drop view if it was previously hidden
        mDropView = view;
        // Create and start the animation
        mDropAnim = animator;
        mDropAnim.addListener(forEndCallback(() -> mDropAnim = null));
        if (animationEndStyle == ANIMATION_END_DISAPPEAR) {
            mDropAnim.addListener(forEndCallback(this::clearAnimatedView));
        }
        mDropAnim.start();
    }

    /**
     * Remove the drop view and end the drag animation.
     *
     * @return {@link DragView} that is removed.
     */
    @Nullable
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

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        updateChildIndices();
        // TextLauncher: no ActivityContext.onDragLayerHierarchyChanged.
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        updateChildIndices();
        // TextLauncher: no ActivityContext.onDragLayerHierarchyChanged.
    }

    @Override
    public void bringChildToFront(View child) {
        super.bringChildToFront(child);
        updateChildIndices();
    }

    private void updateChildIndices() {
        mTopViewIndex = -1;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (getChildAt(i) instanceof DragView) {
                mTopViewIndex = i;
            }
        }
        mChildCountOnLastUpdate = childCount;
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        if (mChildCountOnLastUpdate != childCount) {
            // between platform versions 17 and 18, behavior for onChildViewRemoved / Added changed.
            // Pre-18, the child was not added / removed by the time of those callbacks. We need to
            // force update our representation of things here to avoid crashing on pre-18 devices
            // in certain instances.
            updateChildIndices();
        }

        // i represents the current draw iteration
        if (mTopViewIndex == -1) {
            // in general we do nothing
            return i;
        } else if (i == childCount - 1) {
            // if we have a top index, we return it when drawing last item (highest z-order)
            return mTopViewIndex;
        } else if (i < mTopViewIndex) {
            return i;
        } else {
            // for indexes greater than the top index, we fetch one item above to shift for the
            // displacement of the top index
            return i + 1;
        }
    }

    // TextLauncher: members TextLauncher adds.

    /**
     * Sets the drag controller, which Launcher3 passes to {@code setup} together with the
     * workspace; TextLauncher's drag layer has no touch controllers or scrim to set up.
     */
    public void setDragController(DragController dragController) {
        mDragController = dragController;
    }

    /**
     * AnimatorListeners.forEndCallback(Runnable), which TextLauncher does not have: runs the
     * callback when the animation ends, also after a cancel.
     */
    private static Animator.AnimatorListener forEndCallback(Runnable callback) {
        return new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                callback.run();
            }
        };
    }

    /**
     * mContainer: the home screen's Launcher, looked up when needed, as this layer is made before
     * it; null until then.
     */
    @Nullable
    private Launcher getLauncher() {
        return (Launcher) ActivityContext.lookupContextNoThrow(getContext());
    }

    private View mWorkspaceDragScrim;

    /**
     * DragLayer.getWorkspaceDragScrim: the view behind the home screen that darkens the wallpaper
     * while editing (Launcher3 draws a Scrim on this layer, which is the home screen's parent
     * there).
     */
    public View getWorkspaceDragScrim() {
        return mWorkspaceDragScrim;
    }

    /** Set by the home screen's root layout, which holds the scrim's view. */
    public void setWorkspaceDragScrim(View scrim) {
        mWorkspaceDragScrim = scrim;
    }
}
