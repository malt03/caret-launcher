package com.malt03.textlauncher.ui.search

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.util.Themes
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.data.AppItemInfo
import com.malt03.textlauncher.icons.AppIconLoader
import com.malt03.textlauncher.ui.GridSpec

/**
 * App cells in the style of Launcher3's all-apps grid (docs/spec.md 2.5): like
 * BaseAllAppsAdapter's VIEW_TYPE_ICON, each cell is a BubbleTextView with the icon and the app name
 * below. The cells take keyboard focus like Launcher3's all-apps icons; the container's focus
 * outline is set with [iconFocusListener].
 */
class ResultsAdapter(
    private val iconLoader: AppIconLoader,
    private val grid: GridSpec,
    private val onClick: (AppEntry, View) -> Unit,
    /** The cells' text style: on the all apps sheet, or over the wallpaper. */
    private val onSurface: Boolean = false,
    private val onLongClick: (AppEntry, BubbleTextView) -> Boolean,
) : RecyclerView.Adapter<ResultsAdapter.Holder>() {

    var items: List<AppEntry> = emptyList()
        private set

    /** BaseAllAppsAdapter.setIconFocusListener */
    var iconFocusListener: View.OnFocusChangeListener? = null

    fun submit(newItems: List<AppEntry>) {
        items = newItems
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val icon = ResultIconView(parent.context, grid, onSurface).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, grid.allAppsCellHeightPx)
            onFocusChangeListener = iconFocusListener
        }
        return Holder(icon)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = items[position]
        val icon = holder.icon
        holder.entry = entry
        // A drag hides the icon it starts from (PopupContainer's pre-drag condition).
        icon.setIconVisible(true)
        // BubbleTextView.applyFromWorkspaceItem: the icon (drawn disabled for a suspended app),
        // the label and the tag.
        val itemInfo = AppItemInfo(entry, iconLoader.cached(entry))
        icon.applyFromWorkspaceItem(itemInfo)
        iconLoader.load(entry) { info ->
            if (holder.entry == entry) {
                itemInfo.bitmap = info
                icon.applyFromWorkspaceItem(itemInfo)
            }
        }
        icon.setOnClickListener { onClick(entry, icon) }
        icon.setOnLongClickListener { onLongClick(entry, icon) }
    }

    class Holder(val icon: ResultIconView) : RecyclerView.ViewHolder(icon) {
        var entry: AppEntry? = null
    }
}

/**
 * The all-apps BubbleTextView (all_apps_icon.xml): the icon, and the app name below it, in the
 * text style [onSurface] (the all apps list's sheet, BaseIcon.AllApps: materialColorOnSurface,
 * no shadow) or over the wallpaper (the search results, which take the home screen's place:
 * BaseIcon.Workspace.Shadows, workspaceTextColor with workspaceShadowColor).
 */
@SuppressLint("ViewConstructor")
class ResultIconView(context: Context, grid: GridSpec, onSurface: Boolean = false) :
    BubbleTextView(context, grid.iconSizePx, DISPLAY_ALL_APPS) {
    init {
        // BaseIconUnBounded: focusable, without the default focus highlight.
        isFocusable = true
        defaultFocusHighlightEnabled = false
        val density = resources.displayMetrics.density
        setCenterVertically(true)
        compoundDrawablePadding = (6 * density).toInt()
        val padding = (4 * density).toInt()
        setPadding(padding, padding, padding, padding)
        gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        setTextSize(TypedValue.COMPLEX_UNIT_SP, grid.iconTextSizeSp)
        // BaseIcon: one line (not singleLine, which scrolls the text horizontally and moves the
        // focus outline with it).
        setLines(1)
        ellipsize = TextUtils.TruncateAt.END
        if (onSurface) {
            setTextColor(context.getColor(com.android.launcher3.R.color.materialColorOnSurface))
        } else {
            setTextColor(Themes.getAttrColor(context, com.android.launcher3.R.attr.workspaceTextColor))
            // android:shadowRadius 2.0 with ?attr/workspaceShadowColor
            setShadowLayer(SHADOW_RADIUS, 0f, 0f, Themes.getAttrColor(context, com.android.launcher3.R.attr.workspaceShadowColor))
        }
    }

    private companion object {
        /** BaseIcon.Workspace.Shadows: android:shadowRadius. */
        const val SHADOW_RADIUS = 2f
    }
}
