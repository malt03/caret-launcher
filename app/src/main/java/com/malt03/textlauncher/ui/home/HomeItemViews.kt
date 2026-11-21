package com.malt03.textlauncher.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.views.ActivityContext

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

    /** BubbleTextView.applyLabel without the text (docs/spec.md 5.2): only the description. */
    override fun applyLabel(info: ItemInfo) {
        contentDescription = info.contentDescription ?: info.title
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
    activityContext: ActivityContext,
    folderInfo: FolderInfo,
    private val callbacks: Callbacks,
) : FolderIcon(context, activityContext, folderInfo) {
    interface Callbacks {
        /** The folder's items changed (its FolderInfo's contents, which are the model). */
        fun onContentChanged(view: FolderIconView)
    }

    /** The apps and shortcuts in the folder, in order. */
    val contents: List<WorkspaceItemInfo> get() = folderInfo.contents.map { it as WorkspaceItemInfo }

    override fun onContentAdded(item: ItemInfo, rank: Int) = callbacks.onContentChanged(this)

    override fun onContentRemoved(items: List<ItemInfo>) = callbacks.onContentChanged(this)
}
