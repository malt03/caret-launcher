/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3

import android.content.Context
import android.os.Trace
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.resize.AppWidgetResizeFrameCompose

abstract class AppWidgetResizeFrameBase(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractFloatingView(context, attrs, defStyleAttr) {
    override fun isOfType(type: Int): Boolean = (type and TYPE_WIDGET_RESIZE_FRAME) != 0

    /** Retrieves the view where accessibility actions happen. */
    abstract fun getViewForAccessibility(): View

    /** When true, resize frame can be dismissed. */
    protected fun shouldCloseResizeFrame(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_MOVE_HOME,
            KeyEvent.KEYCODE_MOVE_END,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN -> true

            else -> false
        }
    }
}

/**
 * Stand-in for Launcher3's AppWidgetResizeFrame (android-17.0.0_r1).
 *
 * TextLauncher: only [IntRange] and [showForWidget]'s Compose path are kept. The View frame is the
 * one Flags.fixWidgetSinglePtrResize replaces, and the flag is enabled in the release config, so
 * Launcher3 shows [AppWidgetResizeFrameCompose].
 */
class AppWidgetResizeFrame private constructor() {

    class IntRange {
        var start: Int = 0
        var end: Int = 0

        fun clamp(value: Int) = Utilities.boundToRange(value, start, end)

        /** Resets the range to 0, 0 */
        fun reset() = set(start = 0, end = 0)

        fun set(start: Int, end: Int) {
            this.start = start
            this.end = end
        }

        fun size(): Int = end - start

        /**
         * Moves either the start or end edge (but never both) by {@param delta} and sets the result
         * in {@param out}
         */
        fun applyDelta(moveStart: Boolean, moveEnd: Boolean, delta: Int, outputRange: IntRange) {
            outputRange.start =
                if (moveStart) {
                    start + delta
                } else {
                    start
                }

            outputRange.end =
                if (moveEnd) {
                    end + delta
                } else {
                    end
                }
        }

        /**
         * Applies delta similar to [applyDelta], with extra conditions.
         *
         * @param minSize minimum size after with the moving edge should not be shifted any further.
         *   For eg, if delta = -3 when moving the endEdge brings the size to less than minSize,
         *   only delta = -2 will applied
         * @param maxSize maximum size after with the moving edge should not be shifted any further.
         *   For eg, if delta = -3 when moving the endEdge brings the size to greater than maxSize,
         *   only delta = -2 will applied
         * @param maxEnd The maximum value to the end edge (start edge is always restricted to 0)
         * @return the amount of increase when endEdge was moves and the amount of decrease when the
         *   start edge was moved.
         */
        fun applyDeltaAndBound(
            moveStart: Boolean,
            moveEnd: Boolean,
            delta: Int,
            minSize: Int,
            maxSize: Int,
            maxEnd: Int,
            outputRange: IntRange,
        ): Int {
            applyDelta(moveStart, moveEnd, delta, outputRange)
            outputRange.start = outputRange.start.coerceAtLeast(0)
            outputRange.end = outputRange.end.coerceAtMost(maxEnd)

            if (outputRange.size() < minSize) {
                if (moveStart) {
                    outputRange.start = outputRange.end - minSize
                } else if (moveEnd) {
                    outputRange.end = outputRange.start + minSize
                }
            }

            if (outputRange.size() > maxSize) {
                if (moveStart) {
                    outputRange.start = outputRange.end - maxSize
                } else if (moveEnd) {
                    outputRange.end = outputRange.start + maxSize
                }
            }

            return if (moveEnd) {
                outputRange.size() - size()
            } else {
                size() - outputRange.size()
            }
        }
    }

    companion object {
        /**
         * Shows the resize frame for a widget
         *
         * @param widget is the widget view for which we want to show the resize frame.
         * @param cellLayout is the cellLayout in which the widget is placed.
         */
        @JvmStatic
        fun showForWidget(widget: LauncherAppWidgetHostView?, cellLayout: CellLayout) {
            try {
                Trace.beginSection("showAppWidgetResizeFrame")
                // If widget is not added to view hierarchy, we cannot show resize frame at correct
                // location
                if (widget == null || widget.parent == null) return
                // TextLauncher: CellLayout.mActivity
                val activityContext = ActivityContext.lookupContext(cellLayout.context)

                // TextLauncher: Flags.fixWidgetSinglePtrResize() is enabled in the release.
                AppWidgetResizeFrameCompose.show(activityContext, widget, cellLayout)
            } finally {
                Trace.endSection()
            }
        }
    }
}
