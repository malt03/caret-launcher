package com.malt03.textlauncher.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.android.launcher3.R as L3R
import com.malt03.textlauncher.R
import com.malt03.textlauncher.data.SearchStore

/** Edits the search strings of one app (docs/spec.md 4). */
object SearchTermsDialog {
    fun show(context: Context, key: String, label: CharSequence?, store: SearchStore, onDismiss: () -> Unit) {
        val terms = store.termsOf(key).toMutableList()
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
                    setTextColor(context.getColor(L3R.color.materialColorOnSurface))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(ImageButton(context).apply {
                    // Launcher3's remove icon (the drop target bar's), in the dialog's text color.
                    setImageResource(com.android.launcher3.R.drawable.ic_remove_no_shadow)
                    imageTintList = ColorStateList.valueOf(
                        context.getColor(L3R.color.materialColorOnSurfaceVariant),
                    )
                    background = null
                    contentDescription = context.getString(R.string.search_terms_remove)
                    setOnClickListener {
                        terms.remove(term)
                        store.setTerms(key, terms)
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
                store.setTerms(key, terms)
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

        // The framework's dialog, as Launcher3 has no AppCompat.
        val dialog: AlertDialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.search_terms_title, label))
            .setView(content)
            .setPositiveButton(R.string.search_terms_add, null)
            .setNegativeButton(R.string.search_terms_close, null)
            .setOnDismissListener { onDismiss() }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { addFromInput() }
            input.requestFocus()
            // The field has the focus, so the keyboard comes up with it: the dialog is a window of
            // its own, which the launcher's keyboard does not carry over to.
            dialog.window?.let { WindowCompat.getInsetsController(it, input).show(WindowInsetsCompat.Type.ime()) }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
    }
}
