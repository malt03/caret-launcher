package com.malt03.caretlauncher.ui.allapps

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.allapps.AllAppsGridAdapter
import com.android.launcher3.allapps.AllAppsRecyclerView
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.search.DefaultSearchAdapterProvider
import com.android.launcher3.keyboard.FocusedItemDecorator
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.RecyclerViewFastScroller
import com.android.launcher3.views.RecyclerViewFastScroller.FastScrollerLocation.ALL_APPS_SCROLLER
import com.malt03.caretlauncher.R
import com.malt03.caretlauncher.ui.search.ResultIconView

/**
 * The all apps list (docs/spec.md 6.4): the main page of Launcher3's ActivityAllAppsContainerView
 * (android-17.0.0_r1), with its AdapterHolder, the fast scroller and its popup. The apps are in
 * an [AllAppsStore], which [AlphabeticalAppsList] sorts and splits into fast scroller sections.
 *
 * CaretLauncher: the search field sits above the list (no header, tabs or floating search bar),
 * and the apps come from [setApps] (ModelCallbacks.bindAllApplications).
 */
@SuppressLint("ViewConstructor")
class AllAppsView(
    context: Context,
    activityContext: ActivityContext,
) : FrameLayout(context) {
    /** The activity's AllAppsStore, which also updates the icons' notification dots. */
    private val appsStore: AllAppsStore = activityContext.activityComponent.appsStore
    private val appsList = AlphabeticalAppsList(activityContext, appsStore)
    private val adapter = AllAppsGridAdapter(
        activityContext, LayoutInflater.from(context), appsList, DefaultSearchAdapterProvider(activityContext),
    )

    val recyclerView: AllAppsRecyclerView
    private val fastScroller: RecyclerViewFastScroller

    /** ActivityAllAppsContainerView.mFastScrollerOffset and mTouchHandler. */
    private val fastScrollerOffset = Point()
    private var touchHandler: RecyclerViewFastScroller? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.all_apps_view, this, true)
        recyclerView = findViewById(R.id.apps_list_view)
        fastScroller = findViewById(R.id.fast_scroller)
        // ActivityAllAppsContainerView.onFinishInflate
        fastScroller.setPopupView(findViewById<TextView>(R.id.fast_scroller_popup))

        // CaretLauncher: the cells are made like the search results' (all_apps_icon.xml).
        adapter.setIconFactory { parent: ViewGroup ->
            ResultIconView(parent.context, onSurface = true).apply {
                // BaseAllAppsAdapter gives it the device profile's all apps cell height.
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
        // ActivityAllAppsContainerView.setupHeader: the list's icons get notification dot updates.
        appsStore.registerIconContainer(recyclerView)
        val focusedItemDecorator = FocusedItemDecorator(recyclerView)
        recyclerView.addItemDecoration(focusedItemDecorator)
        adapter.setIconFocusListener(focusedItemDecorator.focusListener)
        // CaretLauncher: the side padding of the search results.
        val sidePadding = resources.getDimensionPixelSize(R.dimen.search_bar_margin_horizontal)
        recyclerView.setPadding(sidePadding, 0, sidePadding, 0)
    }

    /** The space under the list that the navigation bar covers (ActivityAllAppsContainerView.setInsets). */
    fun setBottomInset(bottom: Int) {
        recyclerView.setPadding(recyclerView.paddingLeft, recyclerView.paddingTop, recyclerView.paddingRight, bottom)
    }

    /**
     * ModelCallbacks.bindAllApplications: the app list's AppInfos (AllAppsList.copyData, with their
     * titles, section names and icons, sorted by COMPONENT_KEY_COMPARATOR) go to the store.
     */
    fun setApps(apps: Array<AppInfo>) {
        appsStore.setApps(apps)
    }

    /**
     * ActivityAllAppsContainerView.reset: the list goes back to the top. CaretLauncher: the cells
     * are bound again, which shows an icon a drag left hidden (BaseAllAppsAdapter.onBindViewHolder
     * makes the view visible), as the search results do.
     */
    fun reset() {
        touchHandler?.endFastScrolling()
        recyclerView.scrollToTop()
        rebindCells()
    }

    /**
     * The cells are bound again, which shows an icon a drag left hidden. CaretLauncher: with two
     * panels (docs/spec.md 5.5) the list stays shown, and is not reset, after a drag from it.
     */
    fun rebindCells() {
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
