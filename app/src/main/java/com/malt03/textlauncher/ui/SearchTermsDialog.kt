package com.malt03.textlauncher.ui

import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.malt03.textlauncher.R
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.data.SearchStore

/** Edits the search strings of one app (docs/spec.md 4). */
object SearchTermsDialog {
    fun show(context: Context, entry: AppEntry, store: SearchStore, onDismiss: () -> Unit) {
        val terms = store.termsOf(entry.key).toMutableList()
        val density = context.resources.displayMetrics.density
        val padding = (24 * density).toInt()

        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val input = EditText(context).apply {
            hint = context.getString(R.string.search_terms_hint)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, (8 * density).toInt(), padding, 0)
            addView(list)
            addView(input)
        }

        fun render() {
            list.removeAllViews()
            for (term in terms) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(TextView(context).apply {
                    text = term
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(ImageButton(context).apply {
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    background = null
                    contentDescription = context.getString(R.string.search_terms_remove)
                    setOnClickListener {
                        terms.remove(term)
                        store.setTerms(entry.key, terms)
                        render()
                    }
                })
                list.addView(row)
            }
        }

        fun addFromInput() {
            val term = input.text.toString().trim()
            if (term.isNotEmpty() && term !in terms) {
                terms += term
                store.setTerms(entry.key, terms)
                render()
            }
            input.text.clear()
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addFromInput()
                true
            } else {
                false
            }
        }
        render()

        val dialog: AlertDialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.search_terms_title, entry.label))
            .setView(content)
            .setPositiveButton(R.string.search_terms_add, null)
            .setNegativeButton(R.string.search_terms_close, null)
            .setOnDismissListener { onDismiss() }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { addFromInput() }
            input.requestFocus()
        }
        dialog.show()
    }
}
