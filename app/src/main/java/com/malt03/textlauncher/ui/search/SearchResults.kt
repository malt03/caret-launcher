package com.malt03.textlauncher.ui.search

import android.content.Context
import android.view.View
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.keyboard.FocusedItemDecorator
import com.android.launcher3.keyboard.ViewGroupFocusHelper
import com.malt03.textlauncher.R
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.data.SearchStore
import com.malt03.textlauncher.icons.AppIconLoader
import com.malt03.textlauncher.search.Normalizer
import com.malt03.textlauncher.search.Ranker
import com.malt03.textlauncher.search.SearchCandidate
import com.malt03.textlauncher.ui.GridSpec
import com.malt03.textlauncher.ui.NoPressDelayRecyclerView

/**
 * The search results (docs/spec.md 2): the search part of Launcher3's ActivityAllAppsContainerView
 * (the search RecyclerView with its AdapterHolder) and the ranking that AllAppsSearchBarController
 * asks for. TextLauncher: the ranking is its own (docs/spec.md 2.2 to 2.4), and Pixel Launcher's
 * outline marks the result Enter launches.
 *
 * [inAllApps]: the search in the all apps list (docs/spec.md 6.5), on the sheet under the field at
 * the top of the screen: the results go from the top, in the sheet's text style. Otherwise they are
 * over the wallpaper right above the field, and go from the bottom (docs/spec.md 2.5).
 */
class SearchResults(
    context: Context,
    iconLoader: AppIconLoader,
    private val searchStore: SearchStore,
    grid: GridSpec,
    onClick: (AppEntry, View) -> Unit,
    onLongClick: (AppEntry, BubbleTextView) -> Unit,
    private val inAllApps: Boolean = false,
) {
    private val adapter = ResultsAdapter(iconLoader, grid, onClick, onSurface = inAllApps) { entry, view ->
        onLongClick(entry, view)
        true
    }

    val view: RecyclerView = NoPressDelayRecyclerView(context).apply {
        layoutManager = GridLayoutManager(context, grid.columns, RecyclerView.VERTICAL, !inAllApps)
        val sidePadding = resources.getDimensionPixelSize(R.dimen.search_bar_margin_horizontal)
        setPadding(sidePadding, 0, sidePadding, 0)
        clipToPadding = false
        adapter = this@SearchResults.adapter
        overScrollMode = View.OVER_SCROLL_NEVER
        itemAnimator = null
        visibility = View.GONE
    }

    /**
     * ActivityAllAppsContainerView.AdapterHolder.setup: the search list draws the outline with a
     * ViewGroupFocusHelper, which the icons get as their focus listener.
     */
    private val focusHelper = ViewGroupFocusHelper(view)

    /** The result [focusTopResult] put the focus outline on. */
    private var autoFocusedResult: View? = null

    init {
        val focusedItemDecorator = FocusedItemDecorator(focusHelper)
        view.addItemDecoration(focusedItemDecorator)
        adapter.iconFocusListener = focusedItemDecorator.focusListener
    }

    /** The space under the results that the keyboard or the navigation bar covers. */
    fun setBottomInset(bottom: Int) {
        view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottom)
    }

    val isShown: Boolean get() = view.visibility == View.VISIBLE

    /** The result Enter launches (AllAppsSearchBarController.onEditorAction), and its view. */
    val topResult: AppEntry? get() = adapter.items.firstOrNull()
    val topResultView: View? get() = view.findViewHolderForAdapterPosition(0)?.itemView

    /** Ranks [apps] for [query] (normalized and not empty) and shows them. */
    fun show(query: String, apps: List<AppEntry>) {
        val candidates = apps.map { entry ->
            SearchCandidate(
                item = entry,
                key = entry.key,
                label = entry.label,
                normalizedTargets = (listOf(entry.label) + searchStore.termsOf(entry.key)).map(Normalizer::normalize),
            )
        }
        val ranked = Ranker.rank(query, candidates) { key -> searchStore.scoreOf(query, key) }.map { it.item }
        adapter.submit(ranked)
        view.scrollToPosition(0)
        view.visibility = View.VISIBLE
        focusTopResult()
    }

    fun hide() {
        view.visibility = View.GONE
        adapter.submit(emptyList())
        focusTopResult()
    }

    /** The icons changed (theme or shape): the cells load them again. */
    fun onIconsChanged() {
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    /**
     * Pixel Launcher's search (not in AOSP Launcher3): when the results change, the one Enter
     * launches gets the focus outline, while the keyboard focus stays in the search field.
     * The outline is Launcher3's ([focusHelper]), so arrow keys move it from there.
     */
    private fun focusTopResult() {
        if (!isShown) {
            setAutoFocusedResult(null)
            return
        }
        // The new results are bound on the next layout.
        view.doOnLayout {
            if (isShown) setAutoFocusedResult(topResultView)
        }
    }

    private fun setAutoFocusedResult(view: View?) {
        val previous = autoFocusedResult
        autoFocusedResult = view
        when {
            view != null -> focusHelper.onFocusChange(view, true)
            previous != null -> focusHelper.onFocusChange(previous, false)
        }
    }
}
