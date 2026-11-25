package com.malt03.textlauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.method.TextKeyListener
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import com.android.launcher3.R as L3R
import com.malt03.textlauncher.R
import com.malt03.textlauncher.data.ShareTarget

/**
 * The search field (docs/spec.md 1.1, 1.3), as AppsSearchContainerLayout (android-17.0.0_r1): the
 * field, and the keys typed while it has no focus ([preDispatchKeyEvent]). Sits right above the
 * keyboard. The outline gets the primary color while the field has focus. The share buttons
 * (docs/spec.md 2.7) are on its right while there is a query.
 */
@SuppressLint("ViewConstructor")
class SearchBarView(context: Context) : FrameLayout(context) {
    val editText: EditText = EditText(context)

    /** Called when Enter is pressed outside of IME composition. */
    var onEnter: (() -> Unit)? = null

    private val background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(28f)
        setColor(context.getColor(L3R.color.materialColorSurfaceContainerHigh))
    }
    private val focusedStroke = context.getColor(L3R.color.materialColorPrimary)
    private val unfocusedStroke = context.getColor(L3R.color.materialColorOutlineVariant)

    /** Called when the field gains or loses keyboard focus. */
    var onFocusChanged: ((Boolean) -> Unit)? = null

    /**
     * TextLauncher: focuses the field for a key typed while it had no focus ([preDispatchKeyEvent]),
     * as the launcher's state wants it (the keyboard comes up on the home screen, not in the all
     * apps list).
     */
    var requestFocusForKey: (() -> Unit)? = null

    /** Called when a share button is tapped (docs/spec.md 2.7), with the button. */
    var onShare: ((ShareTarget, View) -> Unit)? = null

    /** The share buttons (docs/spec.md 2.7), right of the text. */
    private val shareButtons = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, dp(6f).toInt(), 0)
    }
    private var shareTargets: List<ShareTarget> = emptyList()

    /** AppsSearchContainerLayout.mSearchQueryBuilder */
    private val searchQueryBuilder = SpannableStringBuilder().also { Selection.setSelection(it, 0) }

    init {
        setBackground(background)
        editText.apply {
            setBackground(null)
            setSingleLine(true)
            hint = context.getString(R.string.search_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(context.getColor(L3R.color.materialColorOnSurface))
            setHintTextColor(context.getColor(L3R.color.materialColorOnSurfaceVariant))
            setPadding(dp(20f).toInt(), 0, dp(20f).toInt(), 0)
            gravity = Gravity.CENTER_VERTICAL
            setOnEditorActionListener { _, actionId, event ->
                val isEnter = actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                if (isEnter) onEnter?.invoke()
                isEnter
            }
            setOnFocusChangeListener { _, hasFocus ->
                updateStroke()
                onFocusChanged?.invoke(hasFocus)
            }
        }
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateShareButtons()
        })
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(editText, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(shareButtons, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        updateStroke()
        updateShareButtons()
    }

    /** The share buttons' apps (docs/spec.md 2.7), in order. */
    fun setShareTargets(targets: List<ShareTarget>) {
        shareTargets = targets
        shareButtons.removeAllViews()
        val size = dp(44f).toInt()
        val padding = dp(6f).toInt()
        val ripple = TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
        }.resourceId
        for (target in targets) {
            shareButtons.addView(ImageButton(context).apply {
                setImageDrawable(target.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(padding, padding, padding, padding)
                setBackgroundResource(ripple)
                contentDescription = target.label
                tooltipText = target.label
                setOnClickListener { onShare?.invoke(target, it) }
            }, LinearLayout.LayoutParams(size, size))
        }
        updateShareButtons()
    }

    /**
     * docs/spec.md 2.7: the buttons are always shown, and disabled while there is no text to share,
     * with Material's disabled opacity.
     */
    private fun updateShareButtons() {
        val shown = shareTargets.isNotEmpty()
        val enabled = query.isNotBlank()
        shareButtons.visibility = if (shown) View.VISIBLE else View.GONE
        for (i in 0 until shareButtons.childCount) {
            shareButtons.getChildAt(i).apply {
                isEnabled = enabled
                alpha = if (enabled) 1f else DISABLED_ALPHA
            }
        }
        editText.setPaddingRelative(dp(20f).toInt(), 0, dp(if (shown) 4f else 20f).toInt(), 0)
    }

    /** The query in the field. */
    val query: String get() = editText.text?.toString().orEmpty()

    /**
     * AppsSearchContainerLayout.preDispatchKeyEvent: typing while a search result has keyboard focus
     * focuses the search field first, so that it gets the key.
     */
    fun preDispatchKeyEvent(event: KeyEvent) {
        // Determine if the key event was actual text, if so, focus the search bar and then dispatch
        // the key normally so that it can process this key event
        if (!editText.isFocused && event.action == KeyEvent.ACTION_DOWN) {
            val unicodeChar = event.unicodeChar
            val isKeyNotWhitespace = unicodeChar > 0 &&
                !Character.isWhitespace(unicodeChar) && !Character.isSpaceChar(unicodeChar)
            if (isKeyNotWhitespace) {
                val gotKey = TextKeyListener.getInstance().onKeyDown(editText, searchQueryBuilder,
                    event.keyCode, event)
                if (gotKey && searchQueryBuilder.isNotEmpty()) {
                    requestFocusForKey?.invoke() ?: editText.requestFocus()
                }
            }
        }
    }

    /** AppsSearchContainerLayout.clearSearchResult, with the field's text. */
    fun clearSearchResult() {
        editText.text?.clear()
        searchQueryBuilder.clear()
        searchQueryBuilder.clearSpans()
        Selection.setSelection(searchQueryBuilder, 0)
    }

    private fun updateStroke() {
        val focused = editText.hasFocus()
        background.setStroke(dp(if (focused) 2f else 1f).toInt(), if (focused) focusedStroke else unfocusedStroke)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        /** Material's opacity of disabled content. */
        const val DISABLED_ALPHA = 0.38f
    }
}
