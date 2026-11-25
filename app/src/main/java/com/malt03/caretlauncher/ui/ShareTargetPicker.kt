package com.malt03.caretlauncher.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.intentresolver.R as IR
import com.android.intentresolver.widget.BadgeTextView
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.malt03.caretlauncher.R
import com.malt03.caretlauncher.data.ShareTarget
import com.malt03.caretlauncher.data.ShareTargetStore

/**
 * "Share text"'s "Add" (docs/spec.md 2.7): the apps that take the text, in a grid like the share
 * sheet's (IntentResolver, android-17.0.0_r1; third_party/intentresolver). The cells are the share
 * sheet's (chooser_grid_item_hover.xml), labeled like ResolverListAdapter.ViewHolder.bindLabel. The
 * targets of one app are one cell with the share sheet's group indicator (ChooserListAdapter's
 * grouping and bindGroupIndicator), which opens the share sheet's stacked app dialog
 * (ChooserStackedAppDialogFragment, as a framework Dialog in place of a Fragment).
 *
 * In place of the system's activity picker (ACTION_PICK_ACTIVITY, Settings' ActivityPicker), whose
 * look was never updated; the share sheet itself launches what is picked.
 */
object ShareTargetPicker {
    /**
     * Lists the apps but [chosen], and gives the tapped one to [onPicked]. [onClosed] runs once the
     * list is gone (or was never shown).
     */
    fun show(
        context: Context,
        chosen: Collection<ComponentName>,
        onPicked: (ComponentName) -> Unit,
        onClosed: () -> Unit,
    ) {
        // Loading the labels and icons of every app that takes the text is slow.
        UI_HELPER_EXECUTOR.execute {
            val cells = group(ShareTargetStore.available(context).filter { it.component !in chosen })
            MAIN_EXECUTOR.execute {
                // The launcher may have been recreated (a configuration change) meanwhile.
                if ((context as? Activity)?.isDestroyed != true) {
                    showGrid(context, cells, onPicked, onClosed)
                } else {
                    onClosed()
                }
            }
        }
    }

    /**
     * ChooserListAdapter.sortedComponents: the targets of one app with one label are one cell
     * (MultiDisplayResolveInfo), in the order of their first target (the targets come sorted by
     * label, DisplayResolveInfoAzInfoComparator).
     */
    private fun group(targets: List<ShareTarget>): List<List<ShareTarget>> =
        targets.groupBy { it.component.packageName + "#" + it.label }.values.toList()

    private fun showGrid(
        context: Context,
        cells: List<List<ShareTarget>>,
        onPicked: (ComponentName) -> Unit,
        onClosed: () -> Unit,
    ) {
        val inflater = LayoutInflater.from(context)
        val res = context.resources
        var dialog: AlertDialog? = null
        fun pick(target: ShareTarget) {
            onPicked(target.component)
            dialog?.dismiss()
        }
        // ChooserActivity: config_chooser_max_targets_per_row cells in a row, which share the
        // sheet's width. CaretLauncher: fewer when the dialog is too narrow for the icons
        // (chooser_icon_width_with_padding).
        val maxColumns = res.getInteger(IR.integer.config_chooser_max_targets_per_row)
        val cellWidth = res.getDimensionPixelSize(IR.dimen.chooser_icon_width_with_padding)
        val layoutManager = GridLayoutManager(context, maxColumns)
        val grid = RecyclerView(context).apply {
            this.layoutManager = layoutManager
            val padding = (8 * res.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, 0)
            clipToPadding = false
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val columns = ((v.width - v.paddingLeft - v.paddingRight) / cellWidth).coerceIn(1, maxColumns)
                if (columns != layoutManager.spanCount) v.post { layoutManager.spanCount = columns }
            }
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount(): Int = cells.size

                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                    object : RecyclerView.ViewHolder(inflater.inflate(IR.layout.chooser_grid_item_hover, parent, false)) {}

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val cell = cells[position]
                    val first = cell.first()
                    val view = holder.itemView
                    val text = view.findViewById<BadgeTextView>(android.R.id.text1)
                    val text2 = view.findViewById<TextView>(android.R.id.text2)
                    view.findViewById<ImageView>(android.R.id.icon).setImageDrawable(first.icon)
                    // MultiDisplayResolveInfo.getExtendedInfo: no sublabel for an app's group.
                    bindLabel(text, text2, first.label, if (cell.size == 1) first.subLabel else null)
                    // ChooserListAdapter.bindGroupIndicator
                    text.badgeDrawable =
                        if (cell.size > 1) context.getDrawable(IR.drawable.chooser_group_background) else null
                    view.setOnClickListener {
                        if (cell.size == 1) pick(first) else showStacked(context, cell, ::pick)
                    }
                }
            }
        }
        dialog = AlertDialog.Builder(context)
            .setTitle(R.string.share_text_title)
            .setView(grid)
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { onClosed() }
            .create()
            .also {
                it.show()
                // As wide as the share sheet, which spans the screen of a phone.
                it.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
    }

    /**
     * ResolverListAdapter.ViewHolder.bindLabel: the label takes two lines, or one above the
     * sublabel.
     */
    private fun bindLabel(text: TextView, text2: TextView, label: CharSequence, subLabel: CharSequence?) {
        text.text = label
        if (!subLabel.isNullOrEmpty()) {
            text.maxLines = 1
            text2.text = subLabel
            text2.visibility = View.VISIBLE
        } else {
            text.maxLines = 2
            text2.visibility = View.GONE
        }
    }

    /**
     * ChooserStackedAppDialogFragment (ChooserTargetActionsDialogFragment.onCreateView): the app's
     * icon and label above its targets' labels, without icons.
     */
    private fun showStacked(context: Context, targets: List<ShareTarget>, onPicked: (ShareTarget) -> Unit) {
        val dialog = Dialog(context)
        // Inflated with a parent's layout params, so that the dialog keeps the layout's width.
        val v = LayoutInflater.from(context).inflate(IR.layout.chooser_dialog, FrameLayout(context), false)
        // Make the background transparent to show dialog rounding
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val first = targets.first()
        v.findViewById<TextView>(android.R.id.title).text = first.label
        v.findViewById<ImageView>(android.R.id.icon).setImageDrawable(first.icon)
        v.findViewById<RecyclerView>(IR.id.listContainer).adapter =
            object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount(): Int = targets.size

                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(
                    LayoutInflater.from(parent.context).inflate(IR.layout.chooser_dialog_item, parent, false),
                ) {}

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val target = targets[position]
                    // ChooserStackedAppDialogFragment.getItemLabel: the target's own label;
                    // getItemIcon: none, which hides the icon.
                    holder.itemView.findViewById<TextView>(IR.id.text).text = target.subLabel ?: target.label
                    holder.itemView.findViewById<ImageView>(android.R.id.icon).visibility = View.GONE
                    holder.itemView.setOnClickListener {
                        onPicked(target)
                        dialog.dismiss()
                    }
                }
            }
        v.findViewById<RecyclerView>(IR.id.listContainer).layoutManager = LinearLayoutManager(context)
        // Centered in the window, which the dialog theme's minimum width may make wider.
        dialog.setContentView(v, FrameLayout.LayoutParams(v.layoutParams).apply { gravity = Gravity.CENTER })
        dialog.show()
    }
}
