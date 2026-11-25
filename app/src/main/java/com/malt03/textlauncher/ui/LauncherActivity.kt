package com.malt03.textlauncher.ui

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserHandle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.window.OnBackInvokedDispatcher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.android.launcher3.util.LifecycleHelper
import androidx.core.view.WindowCompat
import com.malt03.textlauncher.TextLauncherApplication
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.malt03.textlauncher.data.homeKey
import com.malt03.textlauncher.data.AppKey
import com.malt03.textlauncher.search.Normalizer
import com.android.launcher3.Launcher
import com.android.launcher3.Utilities
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.states.RotationHelper.REQUEST_LOCK
import com.android.launcher3.states.RotationHelper.REQUEST_NONE
import com.android.launcher3.util.Themes
import com.android.launcher3.util.WallpaperThemeManager
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AllAppsTransitionController
import com.android.launcher3.touch.AllAppsSwipeController
import com.malt03.textlauncher.ui.allapps.AllAppsTransitionHost
import com.malt03.textlauncher.ui.allapps.AllAppsView
import com.malt03.textlauncher.ui.home.HomeController
import com.android.launcher3.uioverrides.touchcontrollers.StatusBarTouchController
import com.android.launcher3.util.TouchController
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.LauncherState
import com.android.launcher3.LauncherState.ALL_APPS
import com.android.launcher3.LauncherState.NORMAL
import com.android.launcher3.LauncherState.SPRING_LOADED
import com.android.launcher3.anim.AnimatorListeners
import com.android.launcher3.statemanager.StateManager.StateHandler
import com.malt03.textlauncher.ui.search.SearchResults
import com.malt03.textlauncher.ui.statehandlers.DepthController

/**
 * The home screen. The search field always has focus and the keyboard is always shown
 * (docs/spec.md 1).
 */
class LauncherActivity :
    Activity(), HomeController.Host, ActivityContext.Provider, LifecycleOwner, SavedStateRegistryOwner {

    // BaseActivity: the framework's Activity, as in Launcher3 (no AppCompat), with the lifecycle
    // and saved state that Compose (the widget resize frame) looks up in the view tree.
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    init {
        savedStateRegistryController.performAttach()
        registerActivityLifecycleCallbacks(
            LifecycleHelper(this, savedStateRegistryController, lifecycleRegistry),
        )
    }

    private val app get() = application as TextLauncherApplication

    private lateinit var root: LauncherRootLayout
    private lateinit var results: SearchResults
    private lateinit var allApps: AllAppsView

    /** docs/spec.md 6.5: the search in the all apps list, on the sheet under the field. */
    private lateinit var allAppsResults: SearchResults
    private lateinit var allAppsTransition: AllAppsTransitionController

    /** The all apps swipe; none with two panels (docs/spec.md 5.5). */
    private var allAppsSwipe: AllAppsSwipeController? = null

    /**
     * Launcher.mStateManager: NORMAL, SPRING_LOADED (edit mode) or ALL_APPS. TextLauncher: the home
     * screen's Launcher holds it.
     */
    private val stateManager get() = home.launcher.stateManager

    /** Launcher.mDepthController */
    override lateinit var depthController: DepthController
        private set

    private lateinit var home: HomeController

    /** ModelCallbacks.bindAllApplications */
    private val appsListener: (Array<AppInfo>) -> Unit = { allApps.setApps(it) }

    /**
     * The all apps list's AppInfos changed (the apps or their icons): the search results, which
     * bind them, are made again.
     */
    private val appsStoreListener = AllAppsStore.OnUpdateListener { refreshResults() }
    private val appsStore: AllAppsStore get() = home.activityContext.activityComponent.appsStore

    /** Launcher.mRotationHelper */
    override val rotationHelper = RotationHelper(this)

    /**
     * DeviceProfile.isTwoPanels of the home screen's device profile (Launcher.getDeviceProfile),
     * which is made from the grid of the display the activity was created on.
     */
    private val isTwoPanels: Boolean get() = home.activityContext.deviceProfile.deviceProperties.isTwoPanels

    /** True between launching an app and coming back; keyboard changes are expected then. */
    private var isLaunching = false
    private var isResumedState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Launcher.onCreate: the theme follows the wallpaper's color hints.
        WallpaperThemeManager(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Launcher.onCreate: SystemUiController.UI_STATE_BASE_WINDOW, dark system bar icons over a
        // wallpaper that suits dark text.
        val darkText = Themes.getAttrBoolean(this, com.android.launcher3.R.attr.isWorkspaceDarkText)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = darkText
            isAppearanceLightNavigationBars = darkText
        }

        root = LauncherRootLayout(this)
        setContentView(root)
        depthController = DepthController(this, root, root.contentArea)
        // ActivityAllAppsContainerView.getBackgroundColor: the sheet's color with the blur.
        depthController.addBlurStateListener(root.allAppsSheet::setBlurEnabled)

        home = HomeController(app, root, this)
        // Launcher.restoreState: a widget add flow that was waiting for an activity's result.
        home.restoreState(savedInstanceState)
        root.contentArea.addView(home.workspace)
        results = SearchResults(
            this,
            home.activityContext,
            app.searchStore,
            // docs/spec.md 5.5: with two panels, the results replace the all apps list on its panel.
            inAllApps = isTwoPanels,
            fromBottom = true,
        )
        allApps = AllAppsView(this, home.activityContext)
        if (isTwoPanels) {
            // docs/spec.md 5.5: the all apps list is always shown on the left panel, and there is
            // no all apps sheet to open.
            root.enableTwoPanels().apply {
                setList(allApps)
                setSearchResults(results.view)
            }
            // The list and the results end at the search field's top; the panel goes on behind it.
            root.onAllAppsPanelBottomInsetChanged = { inset ->
                allApps.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = inset }
                results.view.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = inset }
            }
        } else {
            root.contentArea.addView(results.view)
            root.allAppsView = allApps
        }
        allAppsResults = SearchResults(this, home.activityContext, app.searchStore, inAllApps = true)
        // ActivityAllAppsContainerView.setupHeader: the search results' icons (AdapterHolder.SEARCH)
        // get notification dot updates too.
        home.activityContext.activityComponent.appsStore.apply {
            registerIconContainer(results.view)
            registerIconContainer(allAppsResults.view)
            addUpdateListener(appsStoreListener)
        }
        allAppsTransition = AllAppsTransitionController(home.launcher, AllAppsTransitionHost(root))
        if (!isTwoPanels) {
            root.allAppsSheet.setSearchResults(allAppsResults.view)
            // ActivityAllAppsContainerView.setInsets: the list and the search results on the sheet
            // leave the space at the bottom that the system covers (AdapterHolder.applyPadding);
            // the keyboard can be up for the search.
            root.onBottomInsetsChanged = { navBottom, imeBottom ->
                allApps.setBottomInset(navBottom)
                allAppsResults.setBottomInset(maxOf(imeBottom, navBottom))
            }
            allAppsSwipe = AllAppsSwipeController(home.launcher, allAppsTransition, allAppsSwipeCallbacks)
        }
        // DragLayer.recreateControllers; open floating views (the long-press menu, folders) are
        // asked first. TextLauncher: the drag layer is an overlay, which sees the touches first.
        root.overlay.setControllers(*createTouchControllers())
        root.touchInterceptor = root.overlay::dispatchTouchEventFromParent

        val editText = root.searchBar.editText
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) home.closeFolders()
                refreshResults()
            }
        })
        root.searchBar.onEnter = { launchTopResult() }
        // AppsSearchContainerLayout.preDispatchKeyEvent's focus: docs/spec.md 6.5, the search in
        // the all apps list keeps the keyboard as it is.
        root.searchBar.requestFocusForKey = {
            if (stateManager.state == ALL_APPS) root.searchBar.editText.requestFocus() else focusSearch()
        }
        // docs/spec.md 6.5: tapping the field with the all apps list open searches in it; the field
        // gets the focus and the keyboard comes up, over the list.
        root.searchBar.onFocusChanged = { hasFocus ->
            if (hasFocus && stateManager.state == ALL_APPS && !isAllAppsAnimating) {
                WindowCompat.getInsetsController(window, root.searchBar.editText).show(WindowInsets.Type.ime())
            }
        }
        root.onKeyboardHidden = ::onKeyboardHidden

        // docs/spec.md 1.4: the back gesture itself does nothing on the home screen.
        // Launcher.getOnBackAnimationCallback: a drag is canceled first (docs/spec.md 5.4).
        // BaseActivity.registerBackDispatcher
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
            if (home.isDragging) {
                home.cancelDrag()
                return@registerOnBackInvokedCallback
            }
            if (home.closeTopFloatingView()) return@registerOnBackInvokedCallback
            // docs/spec.md 6.6: back closes the all apps list.
            if (allAppsTransition.isAllAppsShown()) {
                closeAllApps(animate = true)
                return@registerOnBackInvokedCallback
            }
            // docs/spec.md 1.4: a search result has keyboard focus and the keyboard is down, so the
            // back gesture reaches here; it clears the query like closing the keyboard does.
            if (!root.searchBar.editText.hasFocus()) {
                clearQuery()
                focusSearch()
            }
        }

        app.appRepository.addListener(appsListener)
        // Launcher.onCreate
        rotationHelper.initialize()
        // Launcher.onCreate: the widget picker's add (WidgetPickerAddItemListener) waits for it.
        // TextLauncher: no saved state for an internal state to restore.
        Launcher.ACTIVITY_TRACKER.handleCreate(home.launcher)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rotationHelper.onConfigurationChanged(newConfig)
        // InvariantDeviceProfile.onConfigChanged: another display (a foldable's) or a new display
        // size has another grid; Launcher.onIdpChanged reloads with it. TextLauncher: the activity
        // is created again, and loading migrates the items into the new grid.
        if (GridSpec.from(this) != home.gridSpec) recreate()
    }

    override fun onDestroy() {
        rotationHelper.destroy()
        app.appRepository.removeListener(appsListener)
        appsStore.removeUpdateListener(appsStoreListener)
        home.destroy()
        super.onDestroy()
        Launcher.ACTIVITY_TRACKER.onContextDestroyed(home.launcher)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Launcher.onNewIntent: an intent that something waits for (the widget picker's add) is
        // handled instead of going home.
        val internalStateHandled = Launcher.ACTIVITY_TRACKER.handleNewIntent(home.launcher)
        if (internalStateHandled) return
        // Home button while already on the home screen. TextLauncher: a drag from the widget
        // picker (a system drag, BaseItemDragListener) goes on, as it starts before the picker's
        // home intent comes; Launcher3's onNewIntent cancels no drag.
        if (!home.isSystemDragInProgress) home.cancelDrag()
        home.closeFloatingViews(animate = false)
        clearQuery()
        // docs/spec.md 6.6: the home button closes the all apps list.
        closeAllApps(animate = false)
        focusSearch()
    }

    override fun onResume() {
        super.onResume()
        isResumedState = true
        isLaunching = false
        home.onResume()
        focusSearch()
    }

    override fun onPause() {
        isResumedState = false
        home.onPause()
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        home.onStart()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Launcher.onSaveInstanceState: the widget add flow waiting for an activity's result.
        home.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Launcher.onActivityResult: the widget and shortcut add flow (docs/spec.md 5.2).
        home.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStop() {
        super.onStop()
        home.onStop()
        // docs/spec.md 1.2: the query is cleared every time the launcher comes back.
        home.cancelDrag()
        home.closeFloatingViews(animate = false)
        clearQuery()
        // docs/spec.md 6.6: coming back after launching an app from the list, it is closed.
        closeAllApps(animate = false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isEditMode) focusSearch()
    }

    /**
     * docs/spec.md 5.4: the home screen is edited (SPRING_LOADED, or a widget's resize frame,
     * which Launcher3 has in NORMAL), and the field has no focus or keyboard.
     */
    private val isEditMode: Boolean get() = stateManager.state == SPRING_LOADED || home.isWidgetResizeFrameOpen

    private fun focusSearch() {
        // docs/spec.md 6.4: with the all apps list open, the field has no focus or keyboard.
        if (isEditMode || allAppsTransition.isAllAppsShown()) return
        val editText = root.searchBar.editText
        editText.requestFocus()
        WindowCompat.getInsetsController(window, editText).show(WindowInsets.Type.ime())
    }

    private fun clearQuery() = root.searchBar.clearSearchResult()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        root.searchBar.preDispatchKeyEvent(event)
        return super.dispatchKeyEvent(event)
    }

    /** docs/spec.md 1.4: closing the keyboard with back clears the query and shows it again. */
    private fun onKeyboardHidden() {
        // docs/spec.md 6.5: closing the keyboard while searching in the all apps list keeps the
        // query and shows the filtered list without the focus.
        if (stateManager.state == ALL_APPS && !isAllAppsAnimating && root.searchBar.editText.hasFocus()) {
            root.requestFocus()
            return
        }
        // docs/spec.md 6.4: the keyboard is not watched while the all apps list is open.
        if (isEditMode || allAppsTransition.isAllAppsShown() || isLaunching ||
            !isResumedState || !hasWindowFocus()
        ) return
        // Keyboard focus moved from the field to a search result (arrow keys), which hides the
        // keyboard.
        if (!root.searchBar.editText.hasFocus()) return
        // The keyboard takes the back gesture while it is shown, so closing it also closes what
        // Launcher3 closes on back: the top floating view (the long-press menu or the folder).
        home.closeTopFloatingView()
        clearQuery()
        focusSearch()
    }

    /**
     * Launcher.createTouchControllers (QuickstepLauncher's): the drag controller, the all apps
     * swipe, then the status bar's swipe down (isEnabledCheck: the NORMAL state; HomeController).
     */
    private fun createTouchControllers(): Array<TouchController> = listOfNotNull(
        home.dragController,
        allAppsSwipe,
        StatusBarTouchController(home.launcher, home::canSwipeDownToStatusBar),
    ).toTypedArray()

    /** TextLauncher: a state animation of the all apps list, with a gesture or without, is running. */
    private val isAllAppsAnimating: Boolean
        get() = stateManager.isInTransition || allAppsSwipe?.isSettling == true

    /** The all apps swipe (docs/spec.md 6.1, 6.2, 6.6): where it may start. */
    private val allAppsSwipeCallbacks = object : AllAppsSwipeController.Callbacks {
        override fun canOpenFrom(ev: MotionEvent): Boolean {
            if (isInSearchBar(ev)) return true
            // docs/spec.md 6.1: the home screen scrolled to its bottom, or not scrolling at all.
            return currentQuery().isEmpty() && ev.y < root.closedBarTop && !home.scrollView.canScrollVertically(1)
        }

        /** ActivityAllAppsContainerView.shouldContainerScroll: the search field, or the sheet's check. */
        override fun shouldContainerScroll(ev: MotionEvent): Boolean =
            isInSearchBar(ev) || root.allAppsSheet.shouldContainerScroll(ev, root)
    }

    private fun isInSearchBar(ev: MotionEvent): Boolean {
        val bar = root.searchBarArea
        return ev.y >= bar.y && ev.y < bar.y + bar.height
    }

    /** Launcher.collectStateHandlers: the all apps transition, the workspace and the depth controller. */
    override fun collectStateHandlers(out: MutableList<StateHandler<LauncherState>>) {
        out.add(allAppsTransition)
        out.add(home.workspace)
        out.add(depthController)
    }

    /**
     * Launcher.onStateSetStart. TextLauncher: the search field's focus and the keyboard follow the
     * state (docs/spec.md 5.4, 6.4, 6.6).
     */
    override fun onStateSetStart(fromState: LauncherState, toState: LauncherState, animated: Boolean) {
        when (toState) {
            ALL_APPS -> prepareAllAppsOpen()
            NORMAL -> if (fromState != SPRING_LOADED) {
                if (animated) {
                    prepareAllAppsClose()
                } else {
                    // docs/spec.md 6.6: closing clears the query (of the search in the list).
                    clearQuery()
                }
            }
            SPRING_LOADED -> {
                // Launcher.onStateSetStart: the rotation is locked while the workspace icons are
                // dragged (SPRING_LOADED).
                rotationHelper.setCurrentStateRequest(REQUEST_LOCK)
                clearQuery()
                // docs/spec.md 6.4: a drag from the all apps list closes it and shows the home
                // screen.
                if (fromState == ALL_APPS || allAppsTransition.isAllAppsShown()) {
                    allApps.reset()
                    refreshResults()
                }
                // The keyboard goes down with edit mode (onEditLayoutChanged).
            }
        }
    }

    /**
     * Launcher.dispatchPopulateAccessibilityEvent: the window's events are titled with the state's
     * description (the home screen or the apps list).
     */
    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
        val result = super.dispatchPopulateAccessibilityEvent(event)
        val text = event.text
        text.clear()
        // Populate event with a fake title based on the current state.
        text.add(
            if (!::home.isInitialized) {
                getString(com.android.launcher3.R.string.home_screen)
            } else {
                home.launcher.stateManager.state.getDescription(home.launcher)
            },
        )
        return result
    }

    /** Launcher.onStateSetEnd */
    override fun onStateSetEnd(state: LauncherState, lastStableState: LauncherState) {
        // Set screen title for Talkback (Launcher.setTitle)
        setTitle(state.title)
        if (state != NORMAL) return
        if (lastStableState == SPRING_LOADED) {
            // Launcher.onStateSetStart: the lock is cleared going back to NORMAL.
            rotationHelper.setCurrentStateRequest(REQUEST_NONE)
            // docs/spec.md 5.5: the all apps panel shows the icon a drag from it hid.
            if (isTwoPanels) allApps.rebindCells()
            // The keyboard comes back as edit mode ends (onEditLayoutChanged), unless a widget's
            // resize frame keeps it.
            return
        }
        allApps.reset()
        refreshResults()
        focusSearch()
    }

    /**
     * docs/spec.md 6.4: the field has no focus and the keyboard goes down; the query is cleared
     * (the list replaces it).
     */
    private fun prepareAllAppsOpen() {
        home.closeFloatingViews(animate = false)
        clearQuery()
        root.requestFocus()
        WindowCompat.getInsetsController(window, root).hide(WindowInsets.Type.ime())
    }

    /** docs/spec.md 6.6: closing, the field gets the focus and the keyboard comes back with it. */
    private fun prepareAllAppsClose() {
        // docs/spec.md 6.6: closing clears the query (of the search in the list).
        clearQuery()
        root.holdFieldAboveKeyboard = true
        val editText = root.searchBar.editText
        editText.requestFocus()
        WindowCompat.getInsetsController(window, editText).show(WindowInsets.Type.ime())
    }

    /**
     * The empty space's menu's "Apps list" (docs/spec.md 5.3): none with two panels, where the
     * list is always shown (docs/spec.md 5.5).
     */
    override val openAllApps: (() -> Unit)? get() = if (isTwoPanels) null else ::openAllApps

    /**
     * Launcher3's NORMAL state for the empty space's long press: the home screen is shown, not
     * the search results, the all apps list or edit mode.
     */
    override fun canShowOptions(): Boolean =
        stateManager.state == NORMAL && !allAppsTransition.isAllAppsShown() && currentQuery().isEmpty()

    /** Opens the all apps list with the state animation (docs/spec.md 6.3, the menu's "Apps"). */
    private fun openAllApps() {
        // docs/spec.md 5.5: with two panels, the list is always shown.
        if (stateManager.state != NORMAL || isTwoPanels) return
        stateManager.goToState(ALL_APPS, true)
    }

    /**
     * docs/spec.md 6.6: closes the list (goToState(NORMAL)); the field goes back above the keyboard
     * with the focus. Without [animate], at once (the home button, leaving the launcher). [onClosed]
     * runs once it is closed, and not when something stops the animation before that
     * (forSuccessCallback). TextLauncher: edit mode is left to the drag or the resize frame that
     * keeps it.
     */
    private fun closeAllApps(animate: Boolean, onClosed: () -> Unit = {}) {
        if (stateManager.state == SPRING_LOADED) {
            onClosed()
            return
        }
        stateManager.goToState(NORMAL, animate, AnimatorListeners.forSuccessCallback(onClosed))
    }

    private fun currentQuery(): String = root.searchBar.query

    /** docs/spec.md 2.5: the results replace the home screen while there is a query. */
    private fun refreshResults() {
        val query = Normalizer.normalize(currentQuery())
        if (isTwoPanels) {
            // docs/spec.md 5.5: the results replace the all apps list on the left panel, and the
            // home screen stays on the right.
            if (query.isEmpty()) {
                results.hide()
                allApps.visibility = View.VISIBLE
            } else {
                results.show(query, appsStore.apps)
                allApps.visibility = View.INVISIBLE
            }
            return
        }
        if (stateManager.state == ALL_APPS) {
            // docs/spec.md 6.5: the results replace the list on the sheet, without an animation.
            results.hide()
            home.scrollView.visibility = View.VISIBLE
            if (query.isEmpty()) {
                allAppsResults.hide()
                allApps.visibility = View.VISIBLE
            } else {
                allAppsResults.show(query, appsStore.apps)
                allApps.visibility = View.INVISIBLE
            }
            return
        }
        allAppsResults.hide()
        allApps.visibility = View.VISIBLE
        if (query.isEmpty()) {
            results.hide()
            home.scrollView.visibility = View.VISIBLE
        } else {
            results.show(query, appsStore.apps)
            home.scrollView.visibility = View.GONE
        }
    }

    /** docs/spec.md 4: the long-press menu's "Edit search terms". */
    override fun editSearchTerms(item: WorkspaceItemInfo) {
        SearchTermsDialog.show(this, item.homeKey(this), item.title, app.searchStore) {
            refreshResults()
            focusSearch()
        }
    }

    /**
     * docs/spec.md 5.4: while the home screen is edited, the field has no focus and the keyboard
     * is down; it comes back after.
     */
    override fun onEditLayoutChanged(editing: Boolean) {
        if (editing) {
            root.requestFocus()
            WindowCompat.getInsetsController(window, root).hide(WindowInsets.Type.ime())
        } else {
            focusSearch()
        }
    }

    /**
     * ItemClickHandler's launch (Launcher.startActivitySafely). TextLauncher: a launch from the
     * search results is learned (docs/spec.md 2.3).
     */
    override fun launch(info: ItemInfo, view: View) =
        launch(info, view, fromSearch = results.isResult(view) || allAppsResults.isResult(view))

    /**
     * An item added from the long-press menu shows on the home screen, so the query goes, and the
     * all apps list closes with its animation before the item is bound
     * (LauncherAccessibilityDelegate.addToWorkspace: goToState(NORMAL, true, ...)).
     */
    override fun showHome(onDone: () -> Unit) {
        clearQuery()
        closeAllApps(animate = true, onClosed = onDone)
    }

    /** ActivityContext.lookupContext finds Launcher3's ActivityContext through the activity. */
    override fun getActivityContext(): ActivityContext = home.activityContext

    private fun launchTopResult() {
        val shown = if (stateManager.state == ALL_APPS) allAppsResults else results
        val top = shown.topResult ?: return
        launch(top, shown.topResultView, fromSearch = true)
    }

    /** Launcher.startShortcut, for an app shortcut in the long-press menu or on the home screen. */
    override fun startShortcut(packageName: String, id: String, view: View, user: UserHandle) {
        isLaunching = true
        try {
            getSystemService(LauncherApps::class.java)
                .startShortcut(packageName, id, Utilities.getViewBounds(view), launchOptions(view), user)
        } catch (e: Exception) {
            isLaunching = false
        }
    }

    private fun launch(info: ItemInfo, view: View?, fromSearch: Boolean) {
        val component = info.targetComponent ?: return
        if (fromSearch) app.searchStore.recordLaunch(currentQuery(), AppKey.of(this, component, info.user))
        val launcherApps = getSystemService(LauncherApps::class.java)
        isLaunching = true
        try {
            launcherApps.startMainActivity(
                component, info.user, view?.let(Utilities::getViewBounds), view?.let(::launchOptions),
            )
        } catch (e: Exception) {
            isLaunching = false
        }
        // The query and results stay while the app opens over them; onStop clears them once
        // the launcher is hidden, so the home screen never flashes in between.
    }

    /** The animation an app or a shortcut opens with: it scales up from [view]. */
    private fun launchOptions(view: View) =
        ActivityOptions.makeScaleUpAnimation(view, 0, 0, view.width, view.height).toBundle()
}
