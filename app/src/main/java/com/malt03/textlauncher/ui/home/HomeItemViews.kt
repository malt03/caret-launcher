package com.malt03.textlauncher.ui.home

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.ShortcutInfo
import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.LauncherSettings
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
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

/**
 * An app icon: icon only, no label (docs/spec.md 5.2). Launcher3's BubbleTextView draws the icon,
 * centered vertically in the cell (DeviceProfile's iconCenterVertically), and passes the pressed
 * state to it, so iconloaderlib's FastBitmapDrawable plays Launcher3's click feedback.
 */
@SuppressLint("ViewConstructor")
class AppIconView(context: Context, iconSizePx: Int) : BubbleTextView(context, iconSizePx) {
    /** The app or shortcut shown here; Launcher3's views keep it in their tag. */
    var itemInfo: WorkspaceItemInfo? = null

    init {
        setCenterVertically(true)
    }

    /** BubbleTextView.setIcon; null clears the icon while the next one loads. */
    var iconDrawable: FastBitmapDrawable?
        get() = icon
        set(value) {
            if (value == null) setCompoundDrawables(null, null, null, null)
            setIcon(value)
        }

    /** No label (docs/spec.md 5.2), so only the icon is centered. */
    override fun getCellSpecMaxTextLineCount(): Int = 0
}

/**
 * A folder on the home screen: no name (docs/spec.md 5.2). The background, the preview and the
 * drop and create animations come from the FolderIcon stand-in, which runs Launcher3's copied
 * PreviewItemManager.
 */
@SuppressLint("ViewConstructor")
class FolderIconView(
    context: Context,
    activityContext: ActivityContext,
    private val callbacks: Callbacks,
) : FolderIcon(context, activityContext) {
    interface Callbacks {
        /** The folder's items changed; the home model is saved from [FolderIconView.contents]. */
        fun onContentChanged(view: FolderIconView)

        /** Folder.notifyDrop: something was dropped on this icon. */
        fun onNotifyDrop(view: FolderIconView) = Unit
    }

    /** The apps and shortcuts in the folder, in order. */
    val contents: List<WorkspaceItemInfo> get() = folderInfo.contents.map { it as WorkspaceItemInfo }

    /** Binds the folder's items without animation (FolderIcon.inflateIcon). */
    fun setContents(items: List<WorkspaceItemInfo>) {
        folderInfo.contents.clear()
        folderInfo.contents.addAll(items)
        items.forEachIndexed { index, item -> item.rank = index }
        onItemsChanged(false)
    }

    override fun onContentAdded(item: ItemInfo, rank: Int) = callbacks.onContentChanged(this)

    override fun onContentRemoved(items: List<ItemInfo>) = callbacks.onContentChanged(this)

    override fun onNotifyDrop() = callbacks.onNotifyDrop(this)
}
