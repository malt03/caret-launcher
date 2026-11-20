package com.malt03.textlauncher.ui.search

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.malt03.textlauncher.R
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.icons.AppIconLoader

/**
 * Search results (docs/spec.md 2.5): icon + app name. The first row is the one Enter launches,
 * so it is highlighted.
 */
class ResultsAdapter(
    private val iconLoader: AppIconLoader,
    private val onClick: (AppEntry, View) -> Unit,
    private val onLongClick: (AppEntry, View) -> Boolean,
) : RecyclerView.Adapter<ResultsAdapter.Holder>() {

    var items: List<AppEntry> = emptyList()
        private set

    var highlightFirst: Boolean = true

    fun submit(newItems: List<AppEntry>) {
        items = newItems
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val density = context.resources.displayMetrics.density
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val horizontal = (16 * density).toInt()
            setPadding(horizontal, 0, horizontal, 0)
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.result_row_height),
            ).apply {
                val margin = context.resources.getDimensionPixelSize(R.dimen.search_bar_margin_horizontal)
                leftMargin = margin; rightMargin = margin
            }
            isClickable = true
            isFocusable = false
        }
        val iconSize = context.resources.getDimensionPixelSize(R.dimen.result_icon_size)
        val icon = ImageView(context)
        row.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
        val label = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            setSingleLine(true)
            setShadowLayer(4 * density, 0f, 0f, 0x66000000)
        }
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = (16 * density).toInt()
        })
        return Holder(row, icon, label)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = items[position]
        holder.entry = entry
        holder.label.text = entry.label
        holder.icon.setImageDrawable(null)
        iconLoader.load(entry) { info ->
            if (holder.entry == entry) holder.icon.setImageDrawable(iconLoader.newDrawable(info))
        }
        holder.itemView.background = if (position == 0 && highlightFirst) holder.highlight else null
        holder.itemView.setOnClickListener { onClick(entry, holder.icon) }
        holder.itemView.setOnLongClickListener { onLongClick(entry, holder.icon) }
    }

    class Holder(view: View, val icon: ImageView, val label: TextView) : RecyclerView.ViewHolder(view) {
        var entry: AppEntry? = null
        val highlight = GradientDrawable().apply {
            cornerRadius = 20 * view.resources.displayMetrics.density
            setColor(MaterialColors.getColor(view, com.google.android.material.R.attr.colorSecondaryContainer))
        }
    }
}
