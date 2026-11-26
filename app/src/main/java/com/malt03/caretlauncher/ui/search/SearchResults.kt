package com.malt03.caretlauncher.ui.search

import android.content.Context
import android.os.UserHandle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.allapps.AllAppsGridAdapter
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
import com.android.launcher3.allapps.search.DefaultSearchAdapterProvider
import com.android.launcher3.keyboard.FocusedItemDecorator
import com.android.launcher3.keyboard.ViewGroupFocusHelper
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.views.ActivityContext
import com.malt03.caretlauncher.R
import com.malt03.caretlauncher.data.AppKey
import com.malt03.caretlauncher.data.SearchStore
import com.malt03.caretlauncher.search.Normalizer
import com.malt03.caretlauncher.search.Ranker
import com.malt03.caretlauncher.search.SearchCandidate
import com.malt03.caretlauncher.ui.NoPressDelayRecyclerView

/**
 * The search results (docs/spec.md 2): the search part of Launcher3's ActivityAllAppsContainerView
 * (the search RecyclerView with its AdapterHolder: an AlphabeticalAppsList without apps, whose
 * setSearchResults the AllAppsGridAdapter shows) and the ranking that AllAppsSearchBarController
 * asks for. The cells bind the all apps list's AppInfos, as the A-Z list does, and a click or long
 * press goes the same way (ItemClickHandler, ItemLongClickListener.INSTANCE_ALL_APPS).
 * CaretLauncher: the ranking is its own (docs/spec.md 2.2 to 2.4), and Pixel Launcher's outline
 * marks the result Enter launches.
 *
 * [inAllApps]: the search in the all apps list (docs/spec.md 6.5), on the sheet under the field at
 * the top of the screen: the results go from the top, in the sheet's text style. Otherwise they are
 * over the wallpaper right above the field, and go from the bottom (docs/spec.md 2.5; the grid
 * layout manager is reversed).
 * [fromBottom]: whether the first result is at the bottom, right above the field; the results in
 * the all apps panel of two panels are in the sheet's text style but above the field
 * (docs/spec.md 5.5).
 */
class SearchResults(
    context: Context,
    activityContext: ActivityContext,
    private val searchStore: SearchStore,
    private val inAllApps: Boolean = false,
    fromBottom: Boolean = !inAllApps,
) {
    /** AdapterHolder.SEARCH's AlphabeticalAppsList: no apps of its own, only search results. */
    private val appsList = AlphabeticalAppsList(activityContext, null)
    private val adapter = AllAppsGridAdapter(
        activityContext, LayoutInflater.from(context), appsList, DefaultSearchAdapterProvider(activityContext),
    )

    val view: RecyclerView = NoPressDelayRecyclerView(context).apply {
        layoutManager = this@SearchResults.adapter.layoutManager.also { it.reverseLayout = fromBottom }
        val sidePadding = resources.getDimensionPixelSize(R.dimen.search_bar_margin_horizontal)
        setPadding(sidePadding, 0, sidePadding, 0)
        clipToPadding = false
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
        // CaretLauncher: the cells are made like the A-Z list's (all_apps_icon.xml), in this list's
        // text style.
        adapter.setIconFactory { parent: ViewGroup ->
            ResultIconView(parent.context, onSurface = inAllApps).apply {
                // BaseAllAppsAdapter gives it the device profile's all apps cell height.
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        appsList.setAdapter(adapter)
        view.adapter = adapter
        val focusedItemDecorator = FocusedItemDecorator(focusHelper)
        view.addItemDecoration(focusedItemDecorator)
        adapter.setIconFocusListener(focusedItemDecorator.focusListener)
    }

    /** The space under the results that the keyboard or the navigation bar covers. */
    fun setBottomInset(bottom: Int) {
        view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottom)
    }

    val isShown: Boolean get() = view.isVisible

    /** The result Enter launches (AllAppsSearchBarController.onEditorAction), and its view. */
    val topResult: AppInfo?
        get() = appsList.adapterItems.firstOrNull()?.itemInfo as? AppInfo
    val topResultView: View? get() = view.findViewHolderForAdapterPosition(0)?.itemView

    /** CaretLauncher: whether [cell] is one of these results (a launch from it is learned). */
    fun isResult(cell: View): Boolean = cell.parent === view

    /**
     * Ranks [apps] (the all apps list's AppInfos) for [query] (normalized and not empty) and shows
     * them (AlphabeticalAppsList.setSearchResults).
     */
    fun show(query: String, apps: Array<AppInfo>) {
        val userCache = UserCache.INSTANCE.get(view.context)
        val serials = HashMap<UserHandle, Long>()
        val candidates = apps.mapNotNull { app ->
            val component = app.componentName ?: return@mapNotNull null
            val key = AppKey.of(component, serials.getOrPut(app.user) { userCache.getSerialNumberForUser(app.user) })
            val label = app.title?.toString().orEmpty()
            SearchCandidate(
                item = app,
                key = key,
                label = label,
                normalizedTargets = (listOf(label) + searchStore.termsOf(key)).map(Normalizer::normalize),
            )
        }
        val ranked = Ranker.rank(query, candidates) { key -> searchStore.scoreOf(query, key) }
        appsList.setSearchResults(ArrayList(ranked.map { AdapterItem.asApp(it.item) }))
        view.scrollToPosition(0)
        view.visibility = View.VISIBLE
        focusTopResult()
    }

    fun hide() {
        view.visibility = View.GONE
        appsList.setSearchResults(null)
        focusTopResult()
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
