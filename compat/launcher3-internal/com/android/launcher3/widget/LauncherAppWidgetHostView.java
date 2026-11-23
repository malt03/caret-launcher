/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.popup.Poppable;
import com.android.launcher3.popup.PoppableType;
import com.android.launcher3.util.Themes;

/**
 * {@inheritDoc}
 *
 * <p>Stand-in for Launcher3's LauncherAppWidgetHostView (android-17.0.0_r1): the view of a widget
 * on the home screen. A long press anywhere on it (CheckLongPressHelper, intercepting the widget's
 * own touches) is its long click, which starts the drag. Made from LauncherAppWidgetHostView.java
 * by deleting what TextLauncher does not have (scroll handling for scrollable widgets
 * (docs/spec.md 5.2), deferred updates, auto advance, the focus outline, custom events, the
 * pre-layout listener, tracing); changes are marked with "TextLauncher:". What it needs from
 * NavigableAppWidgetHostView, its superclass in Launcher3, is at the end: no default padding, and
 * the drag type and bounds (widgets are not scaled, so getScaleToFit is always 1).
 */
// TextLauncher: extends AppWidgetHostView instead of BaseLauncherAppWidgetHostView and
// NavigableAppWidgetHostView, and has no touch complete listener, deferred updates or custom
// events.
public class LauncherAppWidgetHostView extends AppWidgetHostView
        implements DraggableView, View.OnLongClickListener, Poppable {

    private final CheckLongPressHelper mLongPressHelper;

    public LauncherAppWidgetHostView(Context context) {
        super(context);
        mLongPressHelper = new CheckLongPressHelper(this, this);
        // TextLauncher: no custom events (Flags.enableCursorDrivenWorkflows is disabled in the
        // release), accessibility actions or focus outline.

        if (Themes.getAttrBoolean(context, R.attr.isWorkspaceDarkText)) {
            setOnLightBackground(true);
        }
    }

    @Override
    public boolean onLongClick(View view) {
        // TextLauncher: no scroll handling for scrollable widgets, so nothing to undo first.
        view.performLongClick();
        return true;
    }

    @Override
    public void setAppWidget(int appWidgetId, AppWidgetProviderInfo info) {
        // TextLauncher: NavigableAppWidgetHostView.setAppWidget, which this view takes the place
        // of; no tracing.
        // Prevent default padding being set on the view based on provider info. Launcher manages
        // its own widget spacing
        mDisableSetPadding = true;
        super.setAppWidget(appWidgetId, info);
        mDisableSetPadding = false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // TextLauncher: no scroll handling for scrollable widgets (docs/spec.md 5.2) and no touch
        // complete listener; Flags.enableCursorDrivenWorkflows is disabled in the release.
        mLongPressHelper.onTouchEvent(ev);
        return mLongPressHelper.hasPerformedLongPress();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // TextLauncher: Flags.enableCursorDrivenWorkflows is disabled in the release.
        mLongPressHelper.onTouchEvent(ev);
        // We want to keep receiving though events to be able to cancel long press on ACTION_UP
        return true;
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        // TextLauncher: Flags.enableCursorDrivenWorkflows is disabled in the release.
        mLongPressHelper.cancelLongPress();
    }

    @NonNull
    @Override
    public PoppableType getPoppableType() {
        return PoppableType.WIDGET;
    }

    // TextLauncher: members TextLauncher adds.

    /** NavigableAppWidgetHostView.mDisableSetPadding: set while the widget is being set. */
    private boolean mDisableSetPadding = false;

    /**
     * NavigableAppWidgetHostView.setPadding: AppWidgetHostView's default padding is not set, since
     * the launcher spaces the widgets itself.
     */
    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        if (!mDisableSetPadding) {
            super.setPadding(left, top, right, bottom);
        }
    }

    /**
     * NavigableAppWidgetHostView.getScaleToFit: always 1, since TextLauncher does not scale
     * widgets to fit their cells (DeviceProfile.getAppWidgetScale is 1 outside multi-window).
     */
    public float getScaleToFit() {
        return 1f;
    }

    /** NavigableAppWidgetHostView.getViewType: while it is dragged, the view is in the DragView. */
    @Override
    public int getViewType() {
        return DRAGGABLE_WIDGET;
    }

    /** NavigableAppWidgetHostView.getWorkspaceVisualDragBounds */
    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        int width = (int) (getMeasuredWidth() * getScaleToFit());
        int height = (int) (getMeasuredHeight() * getScaleToFit());
        bounds.set(0, 0, width, height);
    }
}
