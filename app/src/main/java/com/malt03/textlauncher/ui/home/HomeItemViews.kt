package com.malt03.textlauncher.ui.home

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.ShortcutInfo
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.LauncherSettings
import com.android.launcher3.Reorderable
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.MultiTranslateDelegate
import com.android.launcher3.views.ActivityContext
import com.malt03.textlauncher.data.AppEntry

/**
 * An item on the home screen or in a folder as Launcher3's views and drag code see it
 * (WorkspaceItemInfo): what is dragged, and what a folder holds. [homeKey] is the key the home
 * model stores it by ([AppEntry.key] or [com.malt03.textlauncher.data.HomeShortcutKey.key]).
 */
interface HomeItemInfo {
    val homeKey: String
}

/** An app (WorkspaceItemInfo of ITEM_TYPE_APPLICATION). */
class AppItemInfo(val entry: AppEntry, bitmap: BitmapInfo?) : WorkspaceItemInfo(), HomeItemInfo {
    init {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
        if (bitmap != null) this.bitmap = bitmap
        user = entry.user
        title = entry.label
    }

    override val homeKey: String get() = entry.key

    /** ItemInfo.mComponentName */
    override fun getComponent(): ComponentName = entry.component
}

/**
 * An app shortcut placed on the home screen (WorkspaceItemInfo of ITEM_TYPE_DEEP_SHORTCUT,
 * docs/spec.md 5.3).
 */
class ShortcutItemInfo(
    override val homeKey: String,
    shortcut: ShortcutInfo,
    context: Context,
    bitmap: BitmapInfo?,
) : WorkspaceItemInfo(shortcut, context), HomeItemInfo {
    var shortcut: ShortcutInfo = shortcut
        private set

    init {
        if (bitmap != null) this.bitmap = bitmap
    }

    /** ShortcutsChangedTask: the app changed the shortcut. */
    fun update(shortcut: ShortcutInfo, context: Context) {
        this.shortcut = shortcut
        updateFromDeepShortcutInfo(shortcut, context)
    }
}

/**
 * Keyboard focus as Launcher3's icons have it (docs/spec.md 2.6): the BaseIconUnBounded style
 * makes them focusable without the default focus highlight, and ItemInflater / FolderPagedView
 * set the container's focus outline as their focus listener.
 */
fun View.setFocusableAsLauncher3Icon(focusListener: View.OnFocusChangeListener) {
    isFocusable = true
    defaultFocusHighlightEnabled = false
    onFocusChangeListener = focusListener
}

/** A view on the home grid. Launcher3's ReorderPreviewAnimation shakes it through [Reorderable]. */
interface HomeItemView : Reorderable

/** BubbleTextView.setReorderBounceScale and friends, shared by the home item views. */
private class ReorderableState(private val view: View) {
    val translateDelegate = MultiTranslateDelegate(view)
    var bounceScale = 1f
        set(value) {
            field = value
            view.scaleX = value
            view.scaleY = value
        }
}

/**
 * An app icon: icon only, no label (docs/spec.md 5.2). Stands in for Launcher3's BubbleTextView.
 * The icon sits at the top padding, which centers it vertically (DeviceProfile's
 * iconCenterVertically). The pressed state is passed to the icon, so iconloaderlib's
 * FastBitmapDrawable plays Launcher3's click feedback.
 */
@SuppressLint("ViewConstructor")
class AppIconView(context: Context, private val iconSizePx: Int) : BubbleTextView(context), HomeItemView {
    private val reorderable = ReorderableState(this)

    /** The app or shortcut shown here; Launcher3's views keep it in their tag. */
    var itemInfo: WorkspaceItemInfo? = null

    /** BubbleTextView.setIconVisible: the long-press menu hides the icon while it is lifted. */
    private var isIconVisible = true

    var iconDrawable: FastBitmapDrawable? = null
        set(value) {
            field?.callback = null
            field = value
            value?.callback = this
            value?.state = drawableState
            invalidate()
        }

    override fun getIcon(): FastBitmapDrawable? = iconDrawable

    override fun getIconSize(): Int = iconSizePx

    override fun getIconHeight(): Int = iconSizePx

    override fun setIconVisible(visible: Boolean) {
        isIconVisible = visible
        invalidate()
    }

    override fun getTranslateDelegate(): MultiTranslateDelegate = reorderable.translateDelegate
    override fun setReorderBounceScale(scale: Float) {
        reorderable.bounceScale = scale
    }
    override fun getReorderBounceScale(): Float = reorderable.bounceScale

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // FolderIcon.onMeasure with iconCenterVertically: the top padding centers the icon.
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setPadding(0, ((height - iconSizePx) / 2).coerceAtLeast(0), 0, 0)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
        if (!isIconVisible) return
        val drawable = iconDrawable ?: return
        val left = (width - iconSizePx) / 2
        drawable.setBounds(left, paddingTop, left + iconSizePx, paddingTop + iconSizePx)
        drawable.draw(canvas)
    }

    override fun verifyDrawable(who: Drawable): Boolean = who === iconDrawable || super.verifyDrawable(who)

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        iconDrawable?.let { if (it.isStateful && it.setState(drawableState)) invalidate() }
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        iconDrawable?.jumpToCurrentState()
    }
}

/**
 * A folder on the home screen: no name (docs/spec.md 5.2). The background, the preview and the
 * drop and create animations come from the FolderIcon stand-in, which runs Launcher3's copied
 * PreviewItemManager.
 */
@SuppressLint("ViewConstructor")
class FolderIconView(
    context: Context,
    private val iconSizePx: Int,
    activityContext: ActivityContext,
    private val callbacks: Callbacks,
) : FolderIcon(context, activityContext), HomeItemView {
    interface Callbacks {
        /** The folder's items changed; the home model is saved from [FolderIconView.contents]. */
        fun onContentChanged(view: FolderIconView)

        /** Folder.notifyDrop: something was dropped on this icon. */
        fun onNotifyDrop(view: FolderIconView) = Unit
    }

    private val reorderable = ReorderableState(this)

    /** The apps and shortcuts in the folder, in order. */
    val contents: List<WorkspaceItemInfo> get() = folderInfo.contents.map { it as WorkspaceItemInfo }

    /** Binds the folder's items without animation (FolderIcon.inflateIcon). */
    fun setContents(items: List<WorkspaceItemInfo>) {
        folderInfo.contents.clear()
        folderInfo.contents.addAll(items)
        items.forEachIndexed { index, item -> item.rank = index }
        onItemsChanged(false)
    }

    override fun getTranslateDelegate(): MultiTranslateDelegate = reorderable.translateDelegate
    override fun setReorderBounceScale(scale: Float) {
        reorderable.bounceScale = scale
    }
    override fun getReorderBounceScale(): Float = reorderable.bounceScale

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // FolderIcon.onMeasure with iconCenterVertically: the top padding centers the icon.
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setPadding(0, ((height - iconSizePx) / 2).coerceAtLeast(0), 0, 0)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onContentAdded(item: ItemInfo, rank: Int) = callbacks.onContentChanged(this)

    override fun onContentRemoved(items: List<ItemInfo>) = callbacks.onContentChanged(this)

    override fun onNotifyDrop() = callbacks.onNotifyDrop(this)
}
