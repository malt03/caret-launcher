package com.malt03.textlauncher.ui.drag

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.UserHandle
import android.os.UserManager
import android.text.TextUtils
import android.util.ArrayMap
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.android.app.animation.Interpolators
import com.android.launcher3.Alarm
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Flags
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings
import com.android.launcher3.OnAlarmListener
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_SYSTEM_MASK
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_SYSTEM_NO
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.util.MSDLPlayerWrapper
import com.android.launcher3.dragndrop.DragLayer
import com.google.android.msdl.data.model.MSDLToken
import com.malt03.textlauncher.R
import com.android.launcher3.R as L3R

/**
 * The "Remove" and "Uninstall" targets at the top while dragging (docs/spec.md 5.3), following
 * Launcher3's DropTargetBar, ButtonDropTarget, DeleteDropTarget and SecondaryDropTarget
 * (android-17.0.0_r1): outlined pill buttons with an icon in the workspace accent color, filled
 * while an item is over them. Their drawables, colors and dimensions are copied into
 * third_party/launcher3/res-textlauncher.
 */
@SuppressLint("ViewConstructor")
class DropTargetBar(
    context: Context,
    private val activity: () -> ActivityContext,
    /** DropTargetHandler: its drag layer and Launcher.removeItem. */
    private val launcher: Launcher,
    private val dragController: () -> DragController,
): FrameLayout(context), DragController.DragListener {
    val remove = DeleteDropTarget()
    val uninstall = UninstallDropTarget()
    private val dropTargets = arrayOf(remove, uninstall)
    private val tempTargets = arrayOfNulls<ButtonDropTarget>(2)
    private var deferOnDragEnd = false
    private var visible = false

    // DropTargetProfile
    private val textSizePx = resources.getDimensionPixelSize(L3R.dimen.drop_target_text_size)
    private val horizontalPaddingPx = resources.getDimensionPixelSize(L3R.dimen.drop_target_button_drawable_horizontal_padding)
    private val verticalPaddingPx = resources.getDimensionPixelSize(L3R.dimen.drop_target_button_drawable_vertical_padding)
    private val gapPx = resources.getDimensionPixelSize(L3R.dimen.drop_target_button_gap)

    init {
        // drop_target_bar.xml
        isFocusable = false
        alpha = 0f
        visibility = View.INVISIBLE
        for (button in dropTargets) {
            addView(button, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        // DropTargetBar.setupInsets
        for (button in dropTargets) {
            button.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx.toFloat())
            button.setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
        }
    }

    /** Registers the buttons with the drag controller (Launcher.setupViews). */
    fun setup(controller: DragController) {
        controller.addDragListener(this)
        for (target in dropTargets) {
            controller.addDragListener(target)
            controller.addDropTarget(target)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)

        val visibleCount = getVisibleButtons(tempTargets)
        if (visibleCount == 1) {
            val widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST)

            val firstButton = tempTargets[0]!!
            firstButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx.toFloat())
            firstButton.setTextVisible(true)
            firstButton.setIconVisible(true)
            firstButton.measure(widthSpec, heightSpec)
            firstButton.resizeTextToFit()
        } else if (visibleCount == 2) {
            val verticalPadding = verticalPaddingPx
            val horizontalPadding = horizontalPaddingPx

            val firstButton = tempTargets[0]!!
            firstButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx.toFloat())
            firstButton.setTextVisible(true)
            firstButton.setIconVisible(true)
            firstButton.setTextMultiLine(false)
            // Reset first button padding in case it was previously changed to multi-line text.
            firstButton.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

            val secondButton = tempTargets[1]!!
            secondButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx.toFloat())
            secondButton.setTextVisible(true)
            secondButton.setIconVisible(true)
            secondButton.setTextMultiLine(false)
            // Reset second button padding in case it was previously changed to multi-line text.
            secondButton.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

            // Both buttons plus the button gap do not display past the edge of the screen.
            // TextLauncher: the available width is the bar's, which spans the screen.
            var availableWidth = width - gapPx

            var widthSpec = MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST)
            firstButton.measure(widthSpec, heightSpec)
            // Remove both icons and put the button's text on two lines if text is truncated.
            if (firstButton.isTextTruncated(availableWidth)) {
                firstButton.setIconVisible(false)
                secondButton.setIconVisible(false)
                firstButton.setTextMultiLine(true)
                firstButton.setPadding(horizontalPadding, verticalPadding / 2, horizontalPadding, verticalPadding / 2)
            }

            availableWidth -= firstButton.measuredWidth + gapPx
            widthSpec = MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST)
            secondButton.measure(widthSpec, heightSpec)
            // Remove both icons and put the button's text on two lines if text is truncated.
            if (secondButton.isTextTruncated(availableWidth)) {
                secondButton.setIconVisible(false)
                firstButton.setIconVisible(false)
                secondButton.setTextMultiLine(true)
                secondButton.setPadding(horizontalPadding, verticalPadding / 2, horizontalPadding, verticalPadding / 2)
            }

            // If text is still truncated, shrink to fit in measured width and resize both targets.
            val minTextSize = minOf(firstButton.resizeTextToFit(), secondButton.resizeTextToFit())
            if (firstButton.textSize != minTextSize || secondButton.textSize != minTextSize) {
                firstButton.textSize = minTextSize
                secondButton.textSize = minTextSize
            }
        }
        setMeasuredDimension(width, height)
    }

    /**
     * DropTargetBar.onLayout. TextLauncher: the home grid shrinks about its horizontal center
     * while dragging (SpringLoadedListener) and is centered under the bar, so the bar's center
     * stands for the scaled cell layout's. The bar's width stands for the scaled panel width,
     * which cancels out of the two buttons' positions.
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val visibleCount = getVisibleButtons(tempTargets)
        if (visibleCount == 0) {
            return
        }

        val barCenter = (right - left) / 2

        if (visibleCount == 1) {
            val button = tempTargets[0]!!
            button.layout(
                barCenter - (button.measuredWidth / 2), 0,
                barCenter + (button.measuredWidth / 2), button.measuredHeight,
            )
        } else if (visibleCount == 2) {
            val buttonGap = gapPx

            val leftButton = tempTargets[0]!!
            val rightButton = tempTargets[1]!!
            val scaledPanelWidth = right - left

            val leftButtonWidth = leftButton.measuredWidth
            val rightButtonWidth = rightButton.measuredWidth
            val extraSpace = scaledPanelWidth - leftButtonWidth - rightButtonWidth - buttonGap

            val leftButtonStart = barCenter - (scaledPanelWidth / 2) + extraSpace / 2
            val leftButtonEnd = leftButtonStart + leftButtonWidth
            val rightButtonStart = leftButtonEnd + buttonGap
            val rightButtonEnd = rightButtonStart + rightButtonWidth

            leftButton.layout(leftButtonStart, 0, leftButtonEnd, leftButton.measuredHeight)
            rightButton.layout(rightButtonStart, 0, rightButtonEnd, rightButton.measuredHeight)
        }
    }

    private fun getVisibleButtons(outVisibleButtons: Array<ButtonDropTarget?>): Int {
        var visibleCount = 0
        for (button in dropTargets) {
            if (button.visibility != View.GONE) {
                outVisibleButtons[visibleCount] = button
                visibleCount++
            }
        }
        return visibleCount
    }

    /** DropTargetBar.onDragStart */
    override fun onDragStart(dragObject: DragObject, options: DragOptions) {
        animateToVisibility(true)
    }

    /** DropTargetBar.deferOnDragEnd: stay visible until the drop animation into a button ends. */
    fun deferOnDragEnd() {
        deferOnDragEnd = true
    }

    /** DropTargetBar.onDragEnd */
    override fun onDragEnd() {
        if (!deferOnDragEnd) {
            animateToVisibility(false)
        } else {
            deferOnDragEnd = false
        }
    }

    /** DropTargetBar.animateToVisibility */
    private fun animateToVisibility(isVisible: Boolean) {
        if (visible != isVisible) {
            visible = isVisible

            // Cancel any existing animation
            animate().cancel()

            val finalAlpha = if (visible) 1f else 0f
            if (alpha.compareTo(finalAlpha) != 0) {
                visibility = View.VISIBLE
                animate().alpha(finalAlpha)
                    .setInterpolator(Interpolators.ACCELERATE)
                    .setDuration(DEFAULT_DRAG_FADE_DURATION)
                    .withEndAction { updateVisibility() }
            }
        }
    }

    /** AlphaUpdateListener.updateVisibility */
    private fun updateVisibility() {
        visibility = if (alpha > 0f) View.VISIBLE else View.INVISIBLE
    }

    /** ButtonDropTarget, styled with DropTargetButton (styles.xml). */
    abstract inner class ButtonDropTarget : TextView(context), DropTarget, DragController.DragListener {
        private val dragDistanceThreshold = resources.getDimensionPixelSize(L3R.dimen.drag_distanceThreshold)
        /** The size of the drawable shown in the drop target. */
        private val drawableSize = resources.getDimensionPixelSize(L3R.dimen.drop_target_button_drawable_size)
        /** The padding, in pixels, between the text and drawable. */
        private val drawablePadding = resources.getDimensionPixelSize(L3R.dimen.drop_target_button_drawable_padding)
        private var active = false
        private val tempCords = IntArray(2)
        private val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)

        protected var mText: CharSequence = ""
        protected lateinit var mDrawable: Drawable
        private var textVisible = true
        private var iconVisible = true
        private var textMultiLine = true

        init {
            // DropTargetButtonBase / DropTargetButton
            gravity = Gravity.CENTER
            compoundDrawablePadding = drawablePadding
            includeFontPadding = false
            setTextColor(context.getColorStateList(L3R.color.drop_target_text))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(L3R.dimen.drop_target_text_size))
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            background = context.getDrawable(L3R.drawable.drop_target_background)
            typeface = Typeface.create("variable-title-medium", Typeface.NORMAL)
        }

        protected fun updateText(resId: Int) {
            setText(resId)
            mText = text
            contentDescription = mText
        }

        protected fun setDrawable(resId: Int) {
            // We do not set the drawable in the xml as that inflates two drawables corresponding to
            // drawableLeft and drawableStart.
            mDrawable = context.getDrawable(resId)!!.mutate()
            mDrawable.setTintList(textColors)
            updateIconVisibility()
        }

        protected abstract fun supportsDrop(info: DragObject): Boolean
        protected abstract fun setupItemInfo(d: DragObject)
        abstract fun completeDrop(d: DragObject)

        override fun onDragEnter(d: DragObject) {
            // Perform Haptic feedback
            if (Flags.msdlFeedback()) {
                mMSDLPlayerWrapper.playToken(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
            }
            // TextLauncher: the text is always visible, so there is no tooltip.
            d.dragView.alpha = DRAG_VIEW_HOVER_OVER_OPACITY
            isSelected = true
        }

        override fun onDragOver(d: DragObject) {
            // Do nothing
        }

        override fun onDragExit(d: DragObject) {
            if (!d.dragComplete) {
                d.dragView.alpha = 1f
                isSelected = false
            } else {
                d.dragView.alpha = DRAG_VIEW_HOVER_OVER_OPACITY
            }
        }

        override fun onDragStart(dragObject: DragObject, options: DragOptions) {
            if (options.isKeyboardDrag || options.isMouseDrag) {
                active = false
            } else {
                setupItemInfo(dragObject)
                active = supportsDrop(dragObject)
            }
            visibility = if (active) View.VISIBLE else View.GONE
        }

        override fun acceptDrop(dragObject: DragObject): Boolean = supportsDrop(dragObject)

        override fun isDropEnabled(): Boolean =
            active && dragController().distanceDragged >= dragDistanceThreshold

        override fun onDragEnd() {
            active = false
            isSelected = false
        }

        /** On drop animate the dropView to the icon. */
        override fun onDrop(d: DragObject, options: DragOptions) {
            val dragLayer = launcher.dragLayer
            val dragView = d.dragView
            val to = getIconRect(d)
            val scale = to.width().toFloat() / dragView.measuredWidth
            dragView.detachContentView(/* reattachToPreviousParent= */ true)

            deferOnDragEnd()

            val onAnimationEndRunnable = Runnable {
                completeDrop(d)
                this@DropTargetBar.onDragEnd()
            }

            dragLayer.animateView(
                d.dragView, to, scale, 0.1f, 0.1f,
                DRAG_VIEW_DROP_DURATION,
                Interpolators.DECELERATE_2, onAnimationEndRunnable,
                DragLayer.ANIMATION_END_DISAPPEAR, null,
            )
        }

        override fun prepareAccessibilityDrop() = Unit

        override fun getHitRectRelativeToDragLayer(outRect: Rect) {
            super.getHitRect(outRect)
            outRect.bottom += resources.getDimensionPixelSize(L3R.dimen.drop_target_drag_padding)

            tempCords[0] = 0
            tempCords[1] = 0
            activity().dragLayer.getDescendantCoordRelativeToSelf(this, tempCords)
            outRect.offsetTo(tempCords[0], tempCords[1])
        }

        private fun getIconRect(dragObject: DragObject): Rect {
            val viewWidth = dragObject.dragView.measuredWidth
            val viewHeight = dragObject.dragView.measuredHeight
            val drawableWidth = mDrawable.intrinsicWidth
            val drawableHeight = mDrawable.intrinsicHeight
            val dragLayer = activity().dragLayer

            // Find the rect to animate to (the view is center aligned)
            val to = Rect()
            dragLayer.getViewRectRelativeToSelf(this, to)

            val width = drawableWidth
            val height = drawableHeight

            val left: Int
            val right: Int

            if (layoutDirection == LAYOUT_DIRECTION_RTL) {
                right = to.right - paddingRight
                left = right - width
            } else {
                left = to.left + paddingLeft
                right = left + width
            }

            val top = to.top + (measuredHeight - height) / 2
            val bottom = top + height

            to.set(left, top, right, bottom)

            // Center the destination rect about the trash icon
            val xOffset = -(viewWidth - width) / 2
            val yOffset = -(viewHeight - height) / 2
            to.offset(xOffset, yOffset)

            return to
        }

        private fun centerIcon() {
            val x = if (textVisible) 0 else (width - paddingLeft - paddingRight) / 2 - drawableSize / 2
            mDrawable.setBounds(x, 0, x + drawableSize, drawableSize)
        }

        fun setTextVisible(isVisible: Boolean) {
            val newText = if (isVisible) mText else ""
            if (textVisible != isVisible || !TextUtils.equals(newText, text)) {
                textVisible = isVisible
                text = newText
                updateIconVisibility()
            }
        }

        /** Display button text over multiple lines when isMultiLine is true, single line otherwise. */
        fun setTextMultiLine(isMultiLine: Boolean) {
            if (textMultiLine != isMultiLine) {
                textMultiLine = isMultiLine
                isSingleLine = !isMultiLine
                maxLines = if (isMultiLine) MAX_LINES_TEXT_MULTI_LINE else MAX_LINES_TEXT_SINGLE_LINE
            }
        }

        /** Sets the button icon visible when isVisible is true, hides it otherwise. */
        fun setIconVisible(isVisible: Boolean) {
            if (iconVisible != isVisible) {
                iconVisible = isVisible
                updateIconVisibility()
            }
        }

        private fun updateIconVisibility() {
            if (iconVisible) {
                centerIcon()
            }
            setCompoundDrawablesRelative(if (iconVisible) mDrawable else null, null, null, null)
            compoundDrawablePadding = if (iconVisible && textVisible) drawablePadding else 0
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            centerIcon()
        }

        /** Returns if the text will be truncated within the provided availableWidth. */
        fun isTextTruncated(availableWidth: Int): Boolean {
            var available = availableWidth - (paddingLeft + paddingRight)
            if (iconVisible) {
                available -= mDrawable.intrinsicWidth + compoundDrawablePadding
            }
            if (available <= 0) {
                return true
            }
            val firstLine = TextUtils.ellipsize(mText, paint, available.toFloat(), TextUtils.TruncateAt.END)
            if (!textMultiLine) {
                return !TextUtils.equals(mText, firstLine)
            }
            if (TextUtils.equals(mText, firstLine)) {
                // When multi-line is active, if it can display as one line, then text is not truncated.
                return false
            }
            val secondLine = TextUtils.ellipsize(
                mText.subSequence(firstLine.length, mText.length), paint, available.toFloat(), TextUtils.TruncateAt.END,
            )
            return !(TextUtils.equals(mText.subSequence(0, firstLine.length), firstLine) &&
                TextUtils.equals(mText.subSequence(firstLine.length, secondLine.length), secondLine))
        }

        /** Returns if the text will be clipped vertically within the provided availableHeight. */
        private fun isTextClippedVertically(availableHeight: Int): Boolean {
            val fontMetricsInt = paint.fontMetricsInt
            val lineCount = if (lineCount <= 0) 1 else lineCount
            val textHeight = lineCount * (fontMetricsInt.bottom - fontMetricsInt.top)

            return textHeight + paddingTop + paddingBottom >= availableHeight
        }

        /**
         * Reduce the size of the text until it fits the measured width or reaches a minimum.
         *
         * @return The biggest text size in SP that makes the text fit or if the text can't fit
         *         returns the min available value
         */
        fun resizeTextToFit(): Float {
            val minSize = pxToSp(resources.getDimensionPixelSize(L3R.dimen.button_drop_target_min_text_size).toFloat())
            val step = pxToSp(resources.getDimensionPixelSize(L3R.dimen.button_drop_target_resize_text_increment).toFloat())
            var textSize = pxToSp(textSize)

            val availableWidth = measuredWidth
            val availableHeight = measuredHeight

            while (isTextTruncated(availableWidth) || isTextClippedVertically(availableHeight)) {
                textSize -= step
                if (textSize < minSize) {
                    textSize = minSize
                    setTextSize(textSize)
                    break
                }
                setTextSize(textSize)
            }
            return textSize
        }

        /** Utilities.pxToSp */
        private fun pxToSp(size: Float): Float = size / resources.displayMetrics.scaledDensity
    }

    /** DeleteDropTarget: removes the item from home; for other items it only cancels the drag. */
    inner class DeleteDropTarget : ButtonDropTarget() {
        init {
            updateText(R.string.drop_target_remove)
            setDrawable(L3R.drawable.ic_remove_no_shadow)
        }

        override fun onDragStart(dragObject: DragObject, options: DragOptions) {
            super.onDragStart(dragObject, options)
            setTextBasedOnDragSource(dragObject)
        }

        override fun setupItemInfo(d: DragObject) = Unit

        /** Set the drop target's text to either "Remove" or "Cancel" depending on the drag item. */
        private fun setTextBasedOnDragSource(d: DragObject) {
            if (!TextUtils.isEmpty(mText)) {
                val resId = if (canRemove(d.dragInfo)) R.string.drop_target_remove else android.R.string.cancel
                mText = resources.getString(resId)
                contentDescription = mText
                requestLayout()
            }
        }

        /**
         * Items on the home screen or in a folder have an id; apps from the search results and
         * shortcuts from the menu are not there yet, so for them the button cancels the drag.
         */
        private fun canRemove(item: ItemInfo): Boolean = item.id != ItemInfo.NO_ID

        override fun supportsDrop(info: DragObject): Boolean = true

        /**
         * DeleteDropTarget.completeDrop with DropTargetHandler.onDeleteComplete: the item leaves
         * the model (Launcher.removeItem). TextLauncher: no undo and no empty screens to strip.
         */
        override fun completeDrop(d: DragObject) {
            val item = d.dragInfo
            if (canRemove(item)) {
                launcher.removeItem(null, item, true /* deleteFromDb */, "removed via drop target")
            }
        }
    }

    /**
     * SecondaryDropTarget with the UNINSTALL action. TextLauncher: there is no RECONFIGURE (a
     * widget's settings are in its long-press menu) and no DISMISS_PREDICTION (no predicted apps),
     * so the button is only for the apps that can be uninstalled.
     */
    inner class UninstallDropTarget : ButtonDropTarget(), OnAlarmListener {
        private val uninstallDisabledCache = ArrayMap<UserHandle, Boolean>(1)
        private val cacheExpireAlarm = Alarm()
        private var hadPendingAlarm = false
        private var deferred: DeferredOnComplete? = null

        init {
            updateText(R.string.drop_target_uninstall)
            setDrawable(L3R.drawable.ic_uninstall_no_shadow)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (hadPendingAlarm) {
                cacheExpireAlarm.setAlarm(CACHE_EXPIRE_TIMEOUT)
                cacheExpireAlarm.setOnAlarmListener(this)
                hadPendingAlarm = false
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            if (cacheExpireAlarm.alarmPending()) {
                cacheExpireAlarm.cancelAlarm()
                cacheExpireAlarm.setOnAlarmListener(null)
                hadPendingAlarm = true
            }
        }

        override fun onAlarm(alarm: Alarm) {
            uninstallDisabledCache.clear()
        }

        override fun setupItemInfo(d: DragObject) = Unit

        override fun supportsDrop(info: DragObject): Boolean = getButtonType(info.dragInfo)

        /**
         * SecondaryDropTarget.getButtonType: whether the button is UNINSTALL (true) rather than
         * INVALID.
         */
        private fun getButtonType(info: ItemInfo): Boolean {
            // TextLauncher: no RECONFIGURE for a widget and no predicted items; a widget has no
            // uninstall target below.
            var uninstallDisabled = uninstallDisabledCache[info.user]
            if (uninstallDisabled == null) {
                val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
                val restrictions = userManager.getUserRestrictions(info.user)
                uninstallDisabled = restrictions.getBoolean(UserManager.DISALLOW_APPS_CONTROL, false) ||
                    restrictions.getBoolean(UserManager.DISALLOW_UNINSTALL_APPS, false)
                uninstallDisabledCache[info.user] = uninstallDisabled
            }
            // Cancel any pending alarm and set cache expiry after some time
            cacheExpireAlarm.setAlarm(CACHE_EXPIRE_TIMEOUT)
            cacheExpireAlarm.setOnAlarmListener(this)
            if (uninstallDisabled) {
                return false
            }
            // TextLauncher: no private space (Flags.enablePrivateSpace).

            if (info is ItemInfoWithIcon) {
                if (info.runtimeStatusFlags and FLAG_SYSTEM_MASK != 0 &&
                    info.runtimeStatusFlags and FLAG_SYSTEM_NO == 0
                ) {
                    return false
                }
            }
            if (getUninstallTarget(info) == null) {
                return false
            }
            return true
        }

        /**
         * SecondaryDropTarget.getUninstallTarget: the component name that should be uninstalled
         * or null.
         */
        private fun getUninstallTarget(item: ItemInfo?): ComponentName? {
            var intent: Intent? = null
            var user: UserHandle? = null
            if (item != null && item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                // TextLauncher: an app's home item (AppItemInfo) has no intent; its activity is
                // its target component.
                intent = item.targetComponent?.let { Intent(Intent.ACTION_MAIN).setComponent(it) }
                user = item.user
            }
            if (intent != null) {
                val info = context.getSystemService(LauncherApps::class.java).resolveActivity(intent, user)
                if (info != null && info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                    return info.componentName
                }
            }
            return null
        }

        override fun onDrop(d: DragObject, options: DragOptions) {
            // Defer onComplete
            d.dragSource = DeferredOnComplete(d.dragSource).also { deferred = it }
            super.onDrop(d, options)
        }

        override fun completeDrop(d: DragObject) {
            val target = performDropAction(d.dragInfo)
            // DropTargetHandler.onSecondaryTargetCompleteDrop
            val source = d.dragSource as? DeferredOnComplete ?: return
            if (target != null) {
                source.packageName = target.packageName
            } else {
                source.sendFailure()
                deferred = null
            }
        }

        /**
         * SecondaryDropTarget.performDropAction for UNINSTALL, with performUninstall: starts the
         * uninstall and returns the component it uninstalls, or null.
         */
        private fun performDropAction(info: ItemInfo): ComponentName? {
            // TextLauncher: no toast for a system app (uninstall_system_app_text), as supportsDrop
            // takes only the items with an uninstall target.
            val cn = getUninstallTarget(info) ?: return null
            return try {
                // R.string.delete_package_intent
                val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", cn.packageName, cn.className))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    .putExtra(Intent.EXTRA_USER, info.user)
                context.startActivity(intent)
                cn
            } catch (e: Exception) {
                null
            }
        }

        /** Launcher.addOnResumeCallback(DeferredOnComplete::onLauncherResume) */
        fun onLauncherResume() {
            val pending = deferred ?: return
            if (pending.packageName == null) return
            deferred = null
            pending.onLauncherResume()
        }

        /**
         * A wrapper around [DragSource] which delays the onDropCompleted action until
         * [onLauncherResume].
         */
        inner class DeferredOnComplete(private val original: DragSource) : DragSource {
            var packageName: String? = null
            private var dragObject: DragObject? = null

            override fun onDropCompleted(target: View?, d: DragObject, success: Boolean) {
                dragObject = d
            }

            fun onLauncherResume() {
                val d = dragObject ?: return
                // TextLauncher: ApplicationInfoWrapper.getInfo, which is null when the package is
                // not installed for the user.
                val pkg = packageName ?: return
                val launcherApps = context.getSystemService(LauncherApps::class.java)
                val installed = runCatching { launcherApps.getApplicationInfo(pkg, 0, d.dragInfo.user) }.isSuccess
                if (!installed) {
                    d.dragSource = original
                    original.onDropCompleted(this@UninstallDropTarget, d, true)
                } else {
                    sendFailure()
                }
            }

            fun sendFailure() {
                val d = dragObject ?: return
                d.dragSource = original
                d.cancelled = true
                original.onDropCompleted(this@UninstallDropTarget, d, false)
            }
        }
    }

    private companion object {
        /** ButtonDropTarget */
        const val DRAG_VIEW_DROP_DURATION = 285
        const val DRAG_VIEW_HOVER_OVER_OPACITY = 0.65f

        const val MAX_LINES_TEXT_MULTI_LINE = 2
        const val MAX_LINES_TEXT_SINGLE_LINE = 1

        /** DropTargetBar.DEFAULT_DRAG_FADE_DURATION */
        const val DEFAULT_DRAG_FADE_DURATION = 175L

        /** SecondaryDropTarget.CACHE_EXPIRE_TIMEOUT */
        const val CACHE_EXPIRE_TIMEOUT = 5000L
    }
}
