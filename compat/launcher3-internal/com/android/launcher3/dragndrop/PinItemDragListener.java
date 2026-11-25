/*
 * Copyright (C) 2017 The Android Open Source Project
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


import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_PIN_WIDGETS;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.content.pm.LauncherApps.PinItemRequest;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.DragEvent;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DragSource;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.PendingAddItemInfo;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.PendingAddShortcutInfo;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.PendingItemDragHelper;
import com.android.launcher3.widget.WidgetAddFlowHandler;

/**
 * {@link DragSource} for handling drop from a different window. This object is initialized
 * in the source window and is passed on to the Launcher activity as an Intent extra.
 *
 * <p>Stand-in for Launcher3's PinItemDragListener (android-17.0.0_r1), made from
 * PinItemDragListener.java; changes are marked with "TextLauncher:".
 */
@TargetApi(Build.VERSION_CODES.O)
public class PinItemDragListener extends BaseItemDragListener<Launcher> {

    private final PinItemRequest mRequest;
    private final CancellationSignal mCancelSignal;
    private final float mPreviewScale;
    @Nullable
    private String mMimeType = null;

    /**
     * {@link DragSource} for handling drop from a different window with the scale = 1 and a
     * default {@code mimeType}.
     */
    public PinItemDragListener(PinItemRequest request, Rect previewRect,
            int previewBitmapWidth, int previewViewWidth) {
        this(request, previewRect, previewBitmapWidth, previewViewWidth, /* previewScale= */ 1f);
    }

    /**
     * {@link DragSource} for handling drop from a different window with the specified
     * {@code mimeType}
     */
    public PinItemDragListener(PinItemRequest request, Rect previewRect,
            int previewBitmapWidth, int previewViewWidth,
            @NonNull String mimeType) {
        this(request, previewRect, previewBitmapWidth, previewViewWidth, /* previewScale= */ 1f);
        mMimeType = mimeType;
    }

    /**
     * {@link DragSource} for handling drop from a different window with the specified
     * {@code previewScale} and a default {@code mimeType}.
     */
    public PinItemDragListener(PinItemRequest request, Rect previewRect,
            int previewBitmapWidth, int previewViewWidth, float previewScale) {
        super(previewRect, previewBitmapWidth, previewViewWidth);
        mRequest = request;
        mCancelSignal = new CancellationSignal();
        mPreviewScale = previewScale;
    }

    @Override
    public String getMimeType() {
        return mMimeType != null ? mMimeType : super.getMimeType();
    }

    @Override
    public boolean init(Launcher context, boolean isHomeStarted) {
        final boolean result = super.init(context, isHomeStarted);
        context.getStateManager().goToState(LauncherState.NORMAL, /* animated= */ isHomeStarted);
        return result;
    }

    @Override
    protected boolean onDragStart(DragEvent event) {
        if (!mRequest.isValid()) {
            return false;
        }
        return super.onDragStart(event);
    }

    @Override
    protected void startDrag(Rect previewRect, int previewBitmapWidth, int previewViewWidth,
            Point screenPos, DragOptions options) {
        final PendingAddItemInfo item;
        if (mRequest.getRequestType() == PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            item = new PendingAddShortcutInfo(
                    // TextLauncher: the Launcher stand-in is not a Context.
                    new PinShortcutRequestActivityInfo(mRequest, mContext.asContext()));
        } else {
            // mRequest.getRequestType() == PinItemRequestCompat.REQUEST_TYPE_APPWIDGET
            LauncherAppWidgetProviderInfo providerInfo =
                    LauncherAppWidgetProviderInfo.fromProviderInfo(
                            // TextLauncher: the Launcher stand-in is not a Context.
                            mContext.asContext(),
                            mRequest.getAppWidgetProviderInfo(mContext.asContext()));
            final PinWidgetFlowHandler flowHandler =
                    new PinWidgetFlowHandler(providerInfo, mRequest);
            item = new PendingAddWidgetInfo(providerInfo, CONTAINER_PIN_WIDGETS) {
                @Override
                public WidgetAddFlowHandler getHandler() {
                    return flowHandler;
                }
            };
        }
        // TextLauncher: the Launcher stand-in is not a Context.
        View view = new View(mContext.asContext());
        view.setTag(item);

        PendingItemDragHelper dragHelper = new PendingItemDragHelper(view);
        if (mRequest.getRequestType() == PinItemRequest.REQUEST_TYPE_APPWIDGET) {
            dragHelper.setRemoteViewsPreview(getPreview(mRequest), mPreviewScale);
        }

        dragHelper.startDrag(previewRect, previewBitmapWidth, previewViewWidth,
                screenPos, /*source=*/ this, options);
    }

    @Override
    protected void postCleanup() {
        super.postCleanup();
        mCancelSignal.cancel();
    }

    public static RemoteViews getPreview(PinItemRequest request) {
        Bundle extras = request.getExtras();
        if (extras != null &&
                extras.get(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW) instanceof RemoteViews) {
            return (RemoteViews) extras.get(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW);
        }
        return null;
    }
}
