package com.malt03.textlauncher.ui.allapps

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.os.LocaleList
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.AllAppsGridAdapter
import com.android.launcher3.allapps.AllAppsRecyclerView
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.search.DefaultSearchAdapterProvider
import com.android.launcher3.compat.AlphabeticIndexCompat
import com.android.launcher3.icons.cache.CacheLookupFlag.Companion.DEFAULT_LOOKUP_FLAG
import com.android.launcher3.keyboard.FocusedItemDecorator
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.RecyclerViewFastScroller
import com.android.launcher3.views.RecyclerViewFastScroller.FastScrollerLocation.ALL_APPS_SCROLLER
import com.malt03.textlauncher.R
import com.malt03.textlauncher.data.AllAppsAppInfo
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.icons.AppIconLoader
import com.malt03.textlauncher.ui.GridSpec
import com.malt03.textlauncher.ui.search.ResultIconView

/**
 * The all apps list (docs/spec.md 6.4): the main page of Launcher3's ActivityAllAppsContainerView
 * (android-17.0.0_r1), with its AdapterHolder, the fast scroller and its popup. The apps are in
 * an [AllAppsStore], which [AlphabeticalAppsList] sorts and splits into fast scroller sections.
 *
 * TextLauncher: the search field sits above the list (no header, tabs or floating search bar),
 * and the apps come from [setApps] (AllAppsList and AppsListRepository in Launcher3).
 */
@SuppressLint("ViewConstructor")
class AllAppsView(
    context: Context,
    activityContext: ActivityContext,
    private val iconLoader: AppIconLoader,
    grid: GridSpec,
) : FrameLayout(context) {
    private val appsStore = AllAppsStore()
    private val appsList = AlphabeticalAppsList(activityContext, appsStore)
    private val adapter = AllAppsGridAdapter(
        activityContext, LayoutInflater.from(context), appsList, DefaultSearchAdapterProvider(activityContext),
    )

    val recyclerView: AllAppsRecyclerView
    private val fastScroller: RecyclerViewFastScroller

    /** ActivityAllAppsContainerView.mFastScrollerOffset and mTouchHandler. */
    private val fastScrollerOffset = Point()
    private var touchHandler: RecyclerViewFastScroller? = null

    /** Bumped by every [setApps]; icons loaded for an older list are dropped. */
    private var generation = 0

    init {
        LayoutInflater.from(context).inflate(R.layout.all_apps_view, this, true)
        recyclerView = findViewById(R.id.apps_list_view)
        fastScroller = findViewById(R.id.fast_scroller)
        // ActivityAllAppsContainerView.onFinishInflate
        fastScroller.setPopupView(findViewById<TextView>(R.id.fast_scroller_popup))

        // TextLauncher: the cells are made like the search results' (all_apps_icon.xml).
        adapter.setIconFactory { parent: ViewGroup ->
            ResultIconView(parent.context, grid).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, grid.allAppsCellHeightPx)
            }
        }
        // AdapterHolder: the adapter, its layout manager and the list.
        appsList.setAdapter(adapter)
        setup()
    }

    /** AdapterHolder.setup */
    private fun setup() {
        appsList.updateItemFilter(null)
        recyclerView.bindFastScrollbar(fastScroller, ALL_APPS_SCROLLER)
        recyclerView.setApps(appsList)
        recyclerView.layoutManager = adapter.layoutManager
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        // No animations will occur when changes occur to the items in this RecyclerView.
        recyclerView.itemAnimator = null
        val focusedItemDecorator = FocusedItemDecorator(recyclerView)
        recyclerView.addItemDecoration(focusedItemDecorator)
        adapter.setIconFocusListener(focusedItemDecorator.focusListener)
        // TextLauncher: the side padding of the search results.
        val sidePadding = resources.getDimensionPixelSize(R.dimen.search_bar_margin_horizontal)
        recyclerView.setPadding(sidePadding, 0, sidePadding, 0)
    }

    /** The space under the list that the navigation bar covers (ActivityAllAppsContainerView.setInsets). */
    fun setBottomInset(bottom: Int) {
        recyclerView.setPadding(recyclerView.paddingLeft, recyclerView.paddingTop, recyclerView.paddingRight, bottom)
    }

    /**
     * AllAppsList.add with AppsListRepository: the apps as AppInfos, with their titles, section
     * names (AlphabeticIndexCompat) and icons (IconCache.getTitleAndIcon with DEFAULT_LOOKUP_FLAG),
     * sorted by COMPONENT_KEY_COMPARATOR for the store. Launcher3 binds the list once the icons are
     * looked up; so does this.
     */
    fun setApps(entries: List<AppEntry>) {
        val requestedGeneration = ++generation
        iconLoader.loadAll(entries, DEFAULT_LOOKUP_FLAG) {
            if (requestedGeneration != generation) return@loadAll
            val index = AlphabeticIndexCompat(LocaleList.getDefault())
            val apps = entries.map { entry ->
                AllAppsAppInfo(entry).apply {
                    title = Utilities.trim(entry.label)
                    contentDescription = title
                    sectionName = index.computeSectionName(title)
                    iconLoader.cached(entry)?.let { bitmap = it }
                }
            }.sortedWith(AppInfo.COMPONENT_KEY_COMPARATOR)
            appsStore.setApps(apps.toTypedArray())
        }
    }

    /**
     * ActivityAllAppsContainerView.reset: the list goes back to the top. TextLauncher: the cells
     * are bound again, which shows an icon a drag left hidden (BaseAllAppsAdapter.onBindViewHolder
     * makes the view visible), as the search results do.
     */
    fun reset() {
        touchHandler?.endFastScrolling()
        recyclerView.scrollToTop()
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    /**
     * ActivityAllAppsContainerView.shouldContainerScroll: a swipe down here may close the list (the
     * list is at its top, and the touch is not on the scrollbar's thumb). [ev] is in [eventSource]'s
     * coordinates.
     */
    fun shouldContainerScroll(ev: MotionEvent, eventSource: View): Boolean =
        recyclerView.scrollbar == null || recyclerView.shouldContainerScroll(ev, eventSource)

    /** ActivityAllAppsContainerView.onInterceptTouchEvent: the fast scroller takes its touches. */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val scrollbar = recyclerView.scrollbar
            touchHandler = scrollbar?.takeIf { it.isHitInParent(ev.x, ev.y, fastScrollerOffset) }
        }
        return touchHandler?.handleTouchEvent(ev, fastScrollerOffset) ?: false
    }

    /** ActivityAllAppsContainerView.onTouchEvent */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val scrollbar = recyclerView.scrollbar
            touchHandler = scrollbar?.takeIf { it.isHitInParent(ev.x, ev.y, fastScrollerOffset) }
        }
        touchHandler?.let {
            it.handleTouchEvent(ev, fastScrollerOffset)
            return true
        }
        return false
    }
}
