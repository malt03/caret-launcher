/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.widget;

import static android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID;

import static com.android.launcher3.widget.util.WidgetSizes.getWidgetSizePx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Size;
import android.view.View;
import android.view.View.MeasureSpec;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DragSource;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.PendingAddItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.graphics.DragPreviewProvider;
import com.android.launcher3.icons.BaseIconFactory;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.icons.RoundDrawableWrapper;
import com.android.launcher3.icons.cache.CachedObjectCachingLogic;
import com.android.launcher3.widget.DatabaseWidgetPreviewLoader.WidgetPreviewInfo;

import java.util.Objects;

/**
 * Extension of {@link DragPreviewProvider} with logic specific to pending widgets/shortcuts
 * dragged from the widget tray.
 *
 * <p>Stand-in for Launcher3's PendingItemDragHelper (android-17.0.0_r1), made from
 * PendingItemDragHelper.java: the launcher is not a Context, the widget is not preloaded while it
 * is dragged (WidgetHostViewLoader) and the home grid's icons have no labels. Changes are marked
 * with "TextLauncher:".
 */
public class PendingItemDragHelper extends DragPreviewProvider {

    private static final float MAX_WIDGET_SCALE = 1.25f;

    private final PendingAddItemInfo mAddInfo;
    private int[] mEstimatedCellSize;

    @Nullable private RemoteViews mRemoteViewsPreview;
    @Nullable private WidgetPreviewInfo mWidgetPreviewInfo;
    private float mRemoteViewsPreviewScale = 1f;
    // TextLauncher: LauncherAppWidgetHostView, which has no NavigableAppWidgetHostView here.
    @Nullable private LauncherAppWidgetHostView mAppWidgetHostViewPreview;
    private final float mEnforcedRoundedCornersForWidget;

    public PendingItemDragHelper(View view) {
        super(view);
        mAddInfo = (PendingAddItemInfo) view.getTag();
        mEnforcedRoundedCornersForWidget = RoundedCornerEnforcement.computeEnforcedRadius(
                view.getContext());
    }

    /**
     * Set the necessary information about the preview, so a preview can be built for drag and drop.
     */
    public void setWidgetPreviewInfo(@NonNull WidgetPreviewInfo previewInfo) {
        mWidgetPreviewInfo = previewInfo;
    }

    /**
     * Sets a {@link RemoteViews} which shows an app widget preview provided by app developers in
     * the pin widget flow.
     */
    public void setRemoteViewsPreview(@Nullable RemoteViews remoteViewsPreview,
            float previewScale) {
        mRemoteViewsPreview = remoteViewsPreview;
        mRemoteViewsPreviewScale = previewScale;
    }

    /** Sets a {@link LauncherAppWidgetHostView} which shows a preview layout of an app widget. */
    public void setAppWidgetHostViewPreview(
            @Nullable LauncherAppWidgetHostView appWidgetHostViewPreview) {
        mAppWidgetHostViewPreview = appWidgetHostViewPreview;
    }

    /**
     * Starts the drag for the pending item associated with the view.
     *
     * @param previewBounds The bounds where the image was displayed
     * @param previewBitmapWidth The actual width of the bitmap displayed in the view.
     * @param previewViewWidth The width of the view displaying the preview
     * @param screenPos Position of the view on the screen
     */
    public void startDrag(Rect previewBounds, int previewBitmapWidth, int previewViewWidth,
            Point screenPos, DragSource source, DragOptions options) {
        final Launcher launcher = Launcher.getLauncher(mView.getContext());
        // TextLauncher: the launcher is not a Context.
        final Context context = launcher.asContext();
        LauncherAppState app = LauncherAppState.getInstance(context);

        Drawable preview = null;
        final int previewWidth;
        final int previewHeight;
        final float scale;
        final Rect dragRegion;

        mEstimatedCellSize = launcher.getWorkspace().estimateItemSize(mAddInfo);

        DraggableView draggableView;

        if (mAddInfo instanceof PendingAddWidgetInfo) {
            PendingAddWidgetInfo createWidgetInfo = (PendingAddWidgetInfo) mAddInfo;

            int maxWidth = Math.min((int) (previewBitmapWidth * MAX_WIDGET_SCALE), mEstimatedCellSize[0]);

            int[] previewSizeBeforeScale = new int[1];

            if (mWidgetPreviewInfo != null) {
                if (mWidgetPreviewInfo.previewBitmap != null) {
                    Drawable drawable = new FastBitmapDrawable(mWidgetPreviewInfo.previewBitmap);
                    drawable = new RoundDrawableWrapper(drawable, mEnforcedRoundedCornersForWidget);
                    preview = drawable;
                    if (drawable.getIntrinsicWidth() > 0
                            && drawable.getIntrinsicHeight() > 0) {
                        previewSizeBeforeScale[0] = drawable.getIntrinsicWidth();
                    }
                } else {
                    // TextLauncher: the launcher is not a Context.
                    mAppWidgetHostViewPreview = new LauncherAppWidgetHostView(context);
                    mAppWidgetHostViewPreview.setAppWidget(/* appWidgetId= */ INVALID_APPWIDGET_ID,
                            mWidgetPreviewInfo.providerInfo);
                    mAppWidgetHostViewPreview.setClipChildren(false);
                    mAppWidgetHostViewPreview.setClipToPadding(false);
                    mAppWidgetHostViewPreview.updateAppWidget(/* remoteViews= */
                            mWidgetPreviewInfo.remoteViews);

                    DeviceProfile deviceProfile = launcher.getDeviceProfile();
                    Size widgetSizes = getWidgetSizePx(deviceProfile, mAddInfo.spanX,
                            mAddInfo.spanY);
                    measureAndUpdateAppWidgetHostViewScale(widgetSizes);
                }
            } else if (mRemoteViewsPreview != null) {
                // TextLauncher: the launcher is not a Context.
                mAppWidgetHostViewPreview = new LauncherAppWidgetHostView(context);
                mAppWidgetHostViewPreview.setAppWidget(/* appWidgetId= */ INVALID_APPWIDGET_ID,
                        ((PendingAddWidgetInfo) mAddInfo).info);
                DeviceProfile deviceProfile = launcher.getDeviceProfile();
                mAppWidgetHostViewPreview.updateAppWidget(/* remoteViews= */ mRemoteViewsPreview);
                Size widgetSizes = getWidgetSizePx(deviceProfile, mAddInfo.spanX, mAddInfo.spanY);
                mAppWidgetHostViewPreview.measure(
                        MeasureSpec.makeMeasureSpec(widgetSizes.getWidth(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(widgetSizes.getHeight(), MeasureSpec.EXACTLY));
                mAppWidgetHostViewPreview.setClipChildren(false);
                mAppWidgetHostViewPreview.setClipToPadding(false);
                // TextLauncher: NavigableAppWidgetHostView.setScaleToFit; the preview is only drawn
                // in the DragView.
                mAppWidgetHostViewPreview.setScaleX(mRemoteViewsPreviewScale);
                mAppWidgetHostViewPreview.setScaleY(mRemoteViewsPreviewScale);
            }
            if (mAppWidgetHostViewPreview != null) {
                previewSizeBeforeScale[0] = mAppWidgetHostViewPreview.getMeasuredWidth();
            }
            if (preview == null && mAppWidgetHostViewPreview == null) {
                // TextLauncher: the launcher is not a Context.
                Drawable p = new FastBitmapDrawable(new DatabaseWidgetPreviewLoader(context,
                        launcher.getDeviceProfile()).generateWidgetPreview(createWidgetInfo.info,
                        maxWidth, previewSizeBeforeScale));
                p = new RoundDrawableWrapper(p, mEnforcedRoundedCornersForWidget);
                preview = p;
            }

            if (previewSizeBeforeScale[0] < previewBitmapWidth) {
                // The icon has extra padding around it.
                int padding = (previewBitmapWidth - previewSizeBeforeScale[0]) / 2;
                if (previewBitmapWidth > previewViewWidth) {
                    padding = padding * previewViewWidth / previewBitmapWidth;
                }

                previewBounds.left += padding;
                previewBounds.right -= padding;
            }
            if (mAppWidgetHostViewPreview != null) {
                float previewScale = mAppWidgetHostViewPreview.getScaleX();
                int widgetWidth = mAppWidgetHostViewPreview.getMeasuredWidth();
                int widgetHeight = mAppWidgetHostViewPreview.getMeasuredHeight();
                previewWidth = Math.round(widgetWidth * previewScale);
                previewHeight = Math.round(widgetHeight * previewScale);

                previewBounds.offset(
                        Math.round(widgetWidth * (previewScale - 1) / 2),
                        Math.round(widgetHeight * (previewScale - 1) / 2));
            } else {
                previewWidth = preview.getIntrinsicWidth();
                previewHeight = preview.getIntrinsicHeight();
            }
            scale = previewBounds.width() / (float) previewWidth;
            // TextLauncher: no WidgetHostViewLoader; the widget is bound once it is dropped
            // (Launcher.addAppWidgetFromDrop).

            dragRegion = null;
            draggableView = DraggableView.ofType(DraggableView.DRAGGABLE_WIDGET);
        } else {
            PendingAddShortcutInfo createShortcutInfo = (PendingAddShortcutInfo) mAddInfo;
            // TextLauncher: the launcher is not a Context.
            Drawable icon = CachedObjectCachingLogic.loadFullResIcon(
                    app.getIconCache(), createShortcutInfo.getActivityInfo(context));
            LauncherIcons li = LauncherIcons.obtain(context);
            preview = new FastBitmapDrawable(
                    li.createScaledBitmap(icon, BaseIconFactory.MODE_DEFAULT));
            previewWidth = preview.getIntrinsicWidth();
            previewHeight = preview.getIntrinsicHeight();
            li.recycle();
            scale = ((float) launcher.getDeviceProfile().getWorkspaceProfile().getIconSizePx())
                    / previewWidth;

            // Create a preview same as the workspace cell size and draw the icon at the
            // appropriate position.
            DeviceProfile dp = launcher.getDeviceProfile();
            int iconSize = dp.getWorkspaceProfile().getIconSizePx();

            // TextLauncher: the launcher is not a Context.
            int padding = context.getResources()
                    .getDimensionPixelSize(R.dimen.widget_preview_shortcut_padding);
            previewBounds.left += padding;
            previewBounds.top += padding;

            dragRegion = new Rect();
            dragRegion.left = (mEstimatedCellSize[0] - iconSize) / 2;
            dragRegion.right = dragRegion.left + iconSize;
            // TextLauncher: the home grid's icons have no labels, so they are centered.
            dragRegion.top = (mEstimatedCellSize[1] - iconSize) / 2;
            dragRegion.bottom = dragRegion.top + iconSize;
            draggableView = DraggableView.ofType(DraggableView.DRAGGABLE_ICON);
        }

        int dragLayerX = screenPos.x + previewBounds.left
                + (int) ((scale * previewWidth - previewWidth) / 2);
        int dragLayerY = screenPos.y + previewBounds.top
                + (int) ((scale * previewHeight - previewHeight) / 2);

        // Start the drag
        if (mAppWidgetHostViewPreview != null) {
            launcher.getDragController().startDrag(mAppWidgetHostViewPreview, draggableView,
                    dragLayerX, dragLayerY, source, mAddInfo, dragRegion, scale, scale, options);
        } else {
            launcher.getDragController().startDrag(preview, draggableView, dragLayerX, dragLayerY,
                    source, mAddInfo, dragRegion, scale, scale, options);
        }
    }

    private void measureAndUpdateAppWidgetHostViewScale(Size widgetSizes) {
        Objects.requireNonNull(mAppWidgetHostViewPreview).measure(
                MeasureSpec.makeMeasureSpec(widgetSizes.getWidth(),
                        MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(widgetSizes.getHeight(),
                        MeasureSpec.EXACTLY));

        // Scale the preview to fit the widget's size. Not all widgets fill bounds, so we need to
        // scale them.
        if (mAppWidgetHostViewPreview.getChildCount() == 1) {
            View content = mAppWidgetHostViewPreview.getChildAt(0);
            float contentWidth = content.getMeasuredWidth();
            float contentHeight = content.getMeasuredHeight();
            if (contentWidth > 0 && contentHeight > 0) {

                // Take the content width based on the edge furthest from the center, so that when
                // scaling the hostView, the farthest edge is still visible.
                contentWidth = 2 * Math.max(contentWidth / 2 - content.getLeft(),
                        content.getRight() - contentWidth / 2);
                contentHeight = 2 * Math.max(contentHeight / 2 - content.getTop(),
                        content.getBottom() - contentHeight / 2);

                if (contentWidth > 0 && contentHeight > 0) {
                    float pWidth = widgetSizes.getWidth();
                    float pHeight = widgetSizes.getHeight();
                    // TextLauncher: NavigableAppWidgetHostView.setScaleToFit; the preview is only
                    // drawn in the DragView.
                    float scaleToFit = Math.min(pWidth / contentWidth, pHeight / contentHeight);
                    mAppWidgetHostViewPreview.setScaleX(scaleToFit);
                    mAppWidgetHostViewPreview.setScaleY(scaleToFit);
                }
            }
        }
    }

    @Override
    protected Bitmap convertPreviewToAlphaBitmap(Bitmap preview) {
        if (mAddInfo instanceof PendingAddShortcutInfo || mEstimatedCellSize == null) {
            return super.convertPreviewToAlphaBitmap(preview);
        }

        int w = mEstimatedCellSize[0];
        int h = mEstimatedCellSize[1];
        final Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        Rect src = new Rect(0, 0, preview.getWidth(), preview.getHeight());

        float scaleFactor = Math.min((w - blurSizeOutline) / (float) preview.getWidth(),
                (h - blurSizeOutline) / (float) preview.getHeight());
        int scaledWidth = (int) (scaleFactor * preview.getWidth());
        int scaledHeight = (int) (scaleFactor * preview.getHeight());
        Rect dst = new Rect(0, 0, scaledWidth, scaledHeight);

        // center the image
        dst.offset((w - scaledWidth) / 2, (h - scaledHeight) / 2);
        new Canvas(b).drawBitmap(preview, src, dst, new Paint(Paint.FILTER_BITMAP_FLAG));
        return b;
    }
}
