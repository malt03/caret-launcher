package com.malt03.textlauncher.ui.home

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.PointF
import android.graphics.Rect
import android.text.TextUtils
import android.util.Log
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Toast
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.R
import com.android.launcher3.popup.PopupCategory
import com.android.launcher3.popup.PopupContainer
import com.android.launcher3.popup.PopupData

/**
 * WorkspaceTouchListener: a long press on the home screen's empty space shows the launcher's
 * options (Launcher.showDefaultOptions with WorkspaceLongPressOptions, docs/spec.md 5.3). It is the
 * scroll view's touch listener, so it sees the touches that no item took; the scroll view stands
 * in for the paged Workspace.
 */
internal class HomeOptionsMenu(private val home: HomeController) :
    GestureDetector.SimpleOnGestureListener(), View.OnTouchListener {
    private val scrollView = home.scrollView

    // Use twice the touch slop as we are looking for long press which is more
    // likely to cause movement.
    private val touchSlop = 2f * ViewConfiguration.get(home.context).scaledTouchSlop
    private val gestureDetector = GestureDetector(home.context, this)
    private val touchDownPoint = PointF()
    private var longPressState = STATE_CANCELLED

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)

        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            // Check if we can handle long press.
            if (canHandleLongPress()) {
                longPressState = STATE_REQUESTED
                touchDownPoint.set(ev.x, ev.y)
            }
            scrollView.onTouchEvent(ev)
            // Return true to keep receiving touch events
            return true
        }

        if (longPressState == STATE_PENDING_PARENT_INFORM) {
            // Inform the workspace to cancel touch handling
            ev.action = MotionEvent.ACTION_CANCEL
            scrollView.onTouchEvent(ev)

            ev.action = action
            longPressState = STATE_COMPLETED
        }

        val result: Boolean
        if (longPressState == STATE_COMPLETED) {
            // We have handled the touch, so workspace does not need to know anything anymore.
            result = true
        } else if (longPressState == STATE_REQUESTED) {
            val scrollY = scrollView.scrollY
            scrollView.onTouchEvent(ev)
            // TextLauncher: the scroll view is handling the touch once it scrolls.
            if (scrollView.scrollY != scrollY) {
                cancelLongPress()
            } else if (action == MotionEvent.ACTION_MOVE &&
                PointF.length(touchDownPoint.x - ev.x, touchDownPoint.y - ev.y) > touchSlop
            ) {
                cancelLongPress()
            }
            result = true
        } else {
            // TextLauncher: no all apps sheet on top of the home screen here; the scroll view
            // handles the touch as usual.
            result = scrollView.onTouchEvent(ev)
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            cancelLongPress()
        }
        return result
    }

    private fun canHandleLongPress(): Boolean =
        AbstractFloatingView.getTopOpenView(home.activityContext) == null &&
            !home.isDragging && home.host.canShowOptions()

    private fun cancelLongPress() {
        longPressState = STATE_CANCELLED
    }

    override fun onLongPress(e: MotionEvent) {
        if (longPressState == STATE_REQUESTED) {
            if (canHandleLongPress()) {
                longPressState = STATE_PENDING_PARENT_INFORM
                scrollView.parent.requestDisallowInterceptTouchEvent(true)

                scrollView.performHapticFeedback(
                    HapticFeedbackConstants.LONG_PRESS,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
                )
                // Launcher.showDefaultOptions, in the drag layer's coordinates.
                val point = floatArrayOf(touchDownPoint.x, touchDownPoint.y)
                home.root.overlay.getDescendantCoordRelativeToSelf(scrollView, point)
                showDefaultOptions(point[0], point[1])
            } else {
                cancelLongPress()
            }
        }
    }

    /** Launcher.showDefaultOptions */
    private fun showDefaultOptions(x: Float, y: Float) {
        val pos = Rect()
        pos.offsetTo(x.toInt(), y.toInt())
        pos.inset(-20, -20)
        PopupContainer.showForMenuItems(home.activityContext, home.root, getAll(), pos)
    }

    /**
     * WorkspaceLongPressOptions.getAll: "Wallpaper & style", "Widgets" and "Apps list".
     * TextLauncher: no "Home settings" (docs/spec.md 8), and no "Apps list" with two panels,
     * where the list is always shown (docs/spec.md 5.5).
     */
    private fun getAll(): List<PopupData> = buildList {
        add(
            PopupData(R.drawable.ic_palette, R.string.styles_wallpaper_button_text, PopupCategory.SYSTEM_SHORTCUT) { _, _, v ->
                startWallpaperPicker(v)
            },
        )
        add(
            PopupData(R.drawable.ic_widget, R.string.widget_button_text, PopupCategory.SYSTEM_SHORTCUT) { _, _, _ ->
                home.openWidgetPicker()
            },
        )
        home.host.openAllApps?.let { openAllApps ->
            add(
                PopupData(R.drawable.ic_apps, R.string.all_apps_button_label, PopupCategory.SYSTEM_SHORTCUT) { _, _, _ ->
                    openAllApps()
                },
            )
        }
    }

    /**
     * WorkspaceLongPressOptions.startWallpaperPicker: the system's "Wallpaper & style"
     * (docs/spec.md 7.1).
     */
    private fun startWallpaperPicker(@Suppress("UNUSED_PARAMETER") v: View) {
        val context = home.context
        // Utilities.isWallpaperAllowed
        if (!context.getSystemService(WallpaperManager::class.java).isSetWallpaperAllowed) {
            Toast.makeText(context, R.string.msg_disabled_by_admin, Toast.LENGTH_SHORT).show()
            return
        }
        // TextLauncher: one page, so the wallpaper is not scrolled (Workspace
        // .getWallpaperOffsetForCenterPage).
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(EXTRA_WALLPAPER_OFFSET, 0f)
            .putExtra(EXTRA_WALLPAPER_LAUNCH_SOURCE, "app_launched_launcher")
            .putExtra(EXTRA_WALLPAPER_FLAVOR, "focus_wallpaper")
        val pickerPackage = context.getString(R.string.wallpaper_picker_package)
        if (!TextUtils.isEmpty(pickerPackage)) {
            intent.setPackage(pickerPackage)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Unable to launch. intent=$intent", e)
            Toast.makeText(context, R.string.activity_not_available, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val TAG = "WorkspaceTouchListener"

        /**
         * STATE_PENDING_PARENT_INFORM is the state between longPress performed & the next
         * motionEvent. This next event is used to send an ACTION_CANCEL to Workspace, to that it
         * clears any temporary scroll state. After that, the state is set to COMPLETED, and we just
         * eat up all subsequent motion events.
         */
        const val STATE_CANCELLED = 0
        const val STATE_REQUESTED = 1
        const val STATE_PENDING_PARENT_INFORM = 2
        const val STATE_COMPLETED = 3

        // An intent extra to indicate the horizontal scroll of the wallpaper.
        const val EXTRA_WALLPAPER_OFFSET = "com.android.launcher3.WALLPAPER_OFFSET"
        const val EXTRA_WALLPAPER_FLAVOR = "com.android.launcher3.WALLPAPER_FLAVOR"

        // An intent extra to indicate the launch source by launcher.
        const val EXTRA_WALLPAPER_LAUNCH_SOURCE = "com.android.wallpaper.LAUNCH_SOURCE"
    }
}
