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
import com.android.launcher3.icons.FastBitmapDrawable
import com.google.android.material.color.MaterialColors
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
        val icon = ResultIconView(parent.context, grid).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, grid.allAppsCellHeightPx)
            // BaseIconUnBounded: focusable, without the default focus highlight.
            isFocusable = true
            defaultFocusHighlightEnabled = false
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
        icon.iconDrawable = null
        val itemInfo = AppItemInfo(entry, iconLoader.cached(entry))
        icon.tag = itemInfo
        icon.text = entry.label
        iconLoader.load(entry) { info ->
            if (holder.entry == entry) {
                itemInfo.bitmap = info
                icon.iconDrawable = iconLoader.newDrawable(info)
            }
        }
        icon.setOnClickListener { onClick(entry, icon) }
        icon.setOnLongClickListener { onLongClick(entry, icon) }
    }

    class Holder(val icon: ResultIconView) : RecyclerView.ViewHolder(icon) {
        var entry: AppEntry? = null
    }
}

/** The all-apps BubbleTextView (all_apps_icon.xml): the icon, and the app name below it. */
@SuppressLint("ViewConstructor")
class ResultIconView(context: Context, grid: GridSpec) : BubbleTextView(context, grid.iconSizePx) {
    init {
        val density = resources.displayMetrics.density
        setCenterVertically(true)
        compoundDrawablePadding = (6 * density).toInt()
        val padding = (4 * density).toInt()
        setPadding(padding, padding, padding, padding)
        gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        setTextSize(TypedValue.COMPLEX_UNIT_SP, grid.iconTextSizeSp)
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
        // BaseIcon: one line (not singleLine, which scrolls the text horizontally and moves the
        // focus outline with it).
        setLines(1)
        ellipsize = TextUtils.TruncateAt.END
        setShadowLayer(4 * density, 0f, 0f, 0x88000000.toInt())
    }

    /** BubbleTextView.setIcon; null clears the icon while the next one loads. */
    var iconDrawable: FastBitmapDrawable?
        get() = icon
        set(value) {
            if (value == null) setCompoundDrawables(null, null, null, null)
            setIcon(value)
        }
}
