package com.malt03.textlauncher.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.R as L3R
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.malt03.textlauncher.R
import com.malt03.textlauncher.data.ShareTarget
import com.malt03.textlauncher.data.ShareTargetStore

/**
 * "Share text"'s "Add" (docs/spec.md 2.7): the apps that take the text, as the share sheet lists
 * them, in a list like ShareTextDialog's rows. In place of the system's activity picker
 * (ACTION_PICK_ACTIVITY, Settings' ActivityPicker), whose look was never updated.
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
            val targets = ShareTargetStore.available(context).filter { it.component !in chosen }
            MAIN_EXECUTOR.execute {
                // The launcher may have been recreated (a configuration change) meanwhile.
                if ((context as? Activity)?.isDestroyed != true) {
                    showList(context, targets, onPicked, onClosed)
                } else {
                    onClosed()
                }
            }
        }
    }

    private fun showList(
        context: Context,
        targets: List<ShareTarget>,
        onPicked: (ComponentName) -> Unit,
        onClosed: () -> Unit,
    ) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()
        var dialog: AlertDialog? = null
        val ripple = TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId
        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            setPadding(0, dp(8), 0, 0)
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount(): Int = targets.size

                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        minimumHeight = dp(56)
                        // The icons line up with ShareTextDialog's, after its handle.
                        setPaddingRelative(dp(24), 0, dp(24), 0)
                        setBackgroundResource(ripple)
                        layoutParams = RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        addView(ImageView(context), LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(16) })
                        addView(TextView(context).apply {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                            setTextColor(context.getColor(L3R.color.materialColorOnSurface))
                            maxLines = 2
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                    return object : RecyclerView.ViewHolder(row) {}
                }

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val target = targets[position]
                    val row = holder.itemView as LinearLayout
                    (row.getChildAt(0) as ImageView).setImageDrawable(target.icon)
                    (row.getChildAt(1) as TextView).text = target.label
                    row.setOnClickListener {
                        onPicked(target.component)
                        dialog?.dismiss()
                    }
                }
            }
        }
        dialog = AlertDialog.Builder(context)
            .setTitle(R.string.share_text_title)
            .setView(list)
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { onClosed() }
            .create()
            .also { it.show() }
    }
}
