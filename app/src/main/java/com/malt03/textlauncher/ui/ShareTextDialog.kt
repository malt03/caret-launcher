package com.malt03.textlauncher.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.R as L3R
import com.malt03.textlauncher.R
import com.malt03.textlauncher.data.ShareTarget
import com.malt03.textlauncher.data.ShareTargetStore

/**
 * The empty space's menu's "Share text" (docs/spec.md 2.7): the apps of the share buttons, up to
 * [ShareTargetStore.MAX]. "Add" lists the apps that take the text ([ShareTargetPicker]); a row's
 * handle drags it to reorder, and its remove button takes it out. The framework's dialog, as
 * SearchTermsDialog's.
 */
class ShareTextDialog(
    private val context: Context,
    private val store: ShareTargetStore,
    private val onDismiss: () -> Unit,
) {
    private val density = context.resources.displayMetrics.density
    private val targets = mutableListOf<ShareTarget>()
    private val adapter = Adapter()
    private val touchHelper = ItemTouchHelper(DragCallback())
    private var dialog: AlertDialog? = null
    private val storeListener: () -> Unit = { reload() }
    /** The list of "Add" is loading or shown. */
    private var isPicking = false

    fun show() {
        // The apps that no longer take the text are dropped, so that they do not hold a place.
        store.set(store.resolve().map { it.component })
        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ShareTextDialog.adapter
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, dp(8), 0, 0)
        }
        touchHelper.attachToRecyclerView(list)
        val created = AlertDialog.Builder(context)
            .setTitle(R.string.share_text_title)
            .setMessage(R.string.share_text_message)
            .setView(list)
            .setPositiveButton(R.string.search_terms_add, null)
            .setNegativeButton(R.string.search_terms_close, null)
            .setOnDismissListener {
                store.removeListener(storeListener)
                dialog = null
                onDismiss()
            }
            .create()
        created.setOnShowListener {
            // Kept open: the picked app is added to this dialog's list.
            created.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                // One list at a time: "Add" waits for it to close.
                isPicking = true
                updateAddButton()
                ShareTargetPicker.show(context, targets.map { it.component }, store::add) {
                    isPicking = false
                    updateAddButton()
                }
            }
            updateAddButton()
        }
        dialog = created
        store.addListener(storeListener)
        reload()
        created.show()
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun reload() {
        targets.clear()
        targets += store.resolve()
        adapter.notifyDataSetChanged()
        updateAddButton()
    }

    /** docs/spec.md 2.7: no more than [ShareTargetStore.MAX] apps. */
    private fun updateAddButton() {
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled =
            !isPicking && targets.size < ShareTargetStore.MAX
    }

    /** Saves the rows' order. */
    private fun saveOrder() = store.set(targets.map { it.component })

    /** The accessibility actions' move: saved at once, which binds the rows again. */
    private fun move(from: Int, to: Int) {
        if (from !in targets.indices || to !in targets.indices) return
        targets.add(to, targets.removeAt(from))
        saveOrder()
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private inner class Holder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val handle = ImageView(context)
        val icon = ImageView(context)
        val label = TextView(context)
        val remove = ImageButton(context)
    }

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount(): Int = targets.size

        @SuppressLint("ClickableViewAccessibility")
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(56)
                setPaddingRelative(dp(12), 0, dp(12), 0)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val holder = Holder(row)
            val variant = ColorStateList.valueOf(context.getColor(L3R.color.materialColorOnSurfaceVariant))
            holder.handle.apply {
                setImageResource(R.drawable.ic_drag_handle)
                imageTintList = variant
                setPadding(dp(12), dp(12), dp(12), dp(12))
                // The handle starts the drag at once; a long press on the row does too.
                setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) touchHelper.startDrag(holder)
                    false
                }
            }
            row.addView(holder.handle, LinearLayout.LayoutParams(dp(48), dp(48)))
            row.addView(holder.icon, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(16) })
            holder.label.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(context.getColor(L3R.color.materialColorOnSurface))
                maxLines = 2
            }
            row.addView(holder.label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            holder.remove.apply {
                // Launcher3's remove icon, as SearchTermsDialog's.
                setImageResource(L3R.drawable.ic_remove_no_shadow)
                imageTintList = variant
                background = null
                contentDescription = context.getString(R.string.search_terms_remove)
                setOnClickListener {
                    val position = holder.bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) store.remove(targets[position].component)
                }
            }
            row.addView(holder.remove, LinearLayout.LayoutParams(dp(48), dp(48)))
            return holder
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val target = targets[position]
            holder.icon.setImageDrawable(target.icon)
            holder.label.text = target.label
            holder.remove.contentDescription =
                "${context.getString(R.string.search_terms_remove)} ${target.label}"
            // The drag is not reachable with TalkBack; the rows can be moved with actions instead.
            replaceAction(holder, R.id.share_text_move_up, R.string.share_text_move_up, position > 0) {
                move(holder.bindingAdapterPosition, holder.bindingAdapterPosition - 1)
            }
            replaceAction(holder, R.id.share_text_move_down, R.string.share_text_move_down, position < targets.lastIndex) {
                move(holder.bindingAdapterPosition, holder.bindingAdapterPosition + 1)
            }
        }

        private fun replaceAction(holder: Holder, id: Int, label: Int, enabled: Boolean, action: () -> Unit) {
            ViewCompat.removeAccessibilityAction(holder.row, id)
            if (!enabled) return
            ViewCompat.replaceAccessibilityAction(
                holder.row,
                AccessibilityActionCompat(id, context.getString(label)),
                null,
            ) { _, _ ->
                action()
                true
            }
        }
    }

    /** Reorders the rows by dragging; the order is saved once the row is dropped. */
    private inner class DragCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            targets.add(to, targets.removeAt(from))
            adapter.notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            saveOrder()
        }
    }
}
