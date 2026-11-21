package com.malt03.textlauncher.ui.search

import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.icons.AppIconLoader
import com.malt03.textlauncher.ui.GridSpec
import com.malt03.textlauncher.ui.home.AppIconView
import com.malt03.textlauncher.data.AppItemInfo

/**
 * App cells in the style of Launcher3's all-apps grid: icon with the app name below
 * (docs/spec.md 2.5). The cells take keyboard focus like Launcher3's all-apps icons; the
 * container's focus outline is set with [iconFocusListener].
 */
class ResultsAdapter(
    private val iconLoader: AppIconLoader,
    private val grid: GridSpec,
    private val onClick: (AppEntry, View) -> Unit,
    private val onLongClick: (AppEntry, AppIconView) -> Boolean,
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
        val context = parent.context
        val density = context.resources.displayMetrics.density
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, grid.allAppsCellHeightPx)
            val padding = (4 * density).toInt()
            setPadding(padding, padding, padding, padding)
            isClickable = true
            // BaseIconUnBounded: focusable, without the default focus highlight.
            isFocusable = true
            defaultFocusHighlightEnabled = false
            onFocusChangeListener = iconFocusListener
        }
        // The cell's pressed state reaches the icon, so FastBitmapDrawable plays its click feedback.
        val icon = AppIconView(context, grid.iconSizePx).apply { isDuplicateParentStateEnabled = true }
        cell.addView(icon, LinearLayout.LayoutParams(grid.iconSizePx, grid.iconSizePx))
        val label = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, grid.iconTextSizeSp)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_HORIZONTAL
            setShadowLayer(4 * density, 0f, 0f, 0x88000000.toInt())
        }
        cell.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (6 * density).toInt()
        })
        return Holder(cell, icon, label)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = items[position]
        holder.entry = entry
        holder.label.text = entry.label
        holder.icon.iconDrawable = null
        // A drag hides the icon it starts from (PopupContainer's pre-drag condition).
        holder.icon.visibility = View.VISIBLE
        val itemInfo = AppItemInfo(entry, iconLoader.cached(entry))
        holder.icon.tag = itemInfo
        iconLoader.load(entry) { info ->
            if (holder.entry == entry) {
                itemInfo.bitmap = info
                holder.icon.iconDrawable = iconLoader.newDrawable(info)
            }
        }
        holder.itemView.setOnClickListener { onClick(entry, holder.icon) }
        holder.itemView.setOnLongClickListener { onLongClick(entry, holder.icon) }
    }

    class Holder(view: View, val icon: AppIconView, val label: TextView) : RecyclerView.ViewHolder(view) {
        var entry: AppEntry? = null
    }
}
