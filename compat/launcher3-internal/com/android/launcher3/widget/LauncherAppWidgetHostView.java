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
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.dragndrop.DraggableView;

/**
 * Stand-in for Launcher3's LauncherAppWidgetHostView, with what it takes from
 * NavigableAppWidgetHostView: the view of a widget on the home screen. A long press anywhere on it
 * (CheckLongPressHelper, intercepting the widget's own touches) is its long click, which starts
 * the drag; while it is dragged, the view itself is in the DragView (DRAGGABLE_WIDGET).
 *
 * TextLauncher: no scroll handling for scrollable widgets (docs/spec.md 5.2), no auto advance,
 * no focus outline and no custom events; widgets are not scaled (getScaleToFit is always 1).
 */
public class LauncherAppWidgetHostView extends AppWidgetHostView
        implements DraggableView, View.OnLongClickListener {

    private final CheckLongPressHelper mLongPressHelper;

    public LauncherAppWidgetHostView(Context context) {
        super(context);
        mLongPressHelper = new CheckLongPressHelper(this, this);
    }

    @Override
    public boolean onLongClick(View view) {
        view.performLongClick();
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        mLongPressHelper.onTouchEvent(ev);
        return mLongPressHelper.hasPerformedLongPress();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        mLongPressHelper.onTouchEvent(ev);
        // We want to keep receiving though events to be able to cancel long press on ACTION_UP
        return true;
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mLongPressHelper.cancelLongPress();
    }

    /** NavigableAppWidgetHostView.getScaleToFit */
    public float getScaleToFit() {
        return 1f;
    }

    /** NavigableAppWidgetHostView.getViewType */
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
