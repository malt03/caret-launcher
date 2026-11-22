package com.malt03.textlauncher.ui

import android.app.ActivityOptions
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.os.UserHandle
import android.text.Editable
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.method.TextKeyListener
import android.view.KeyEvent
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.malt03.textlauncher.TextLauncherApplication
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.search.Normalizer
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Flags
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.states.RotationHelper.REQUEST_LOCK
import com.android.launcher3.states.RotationHelper.REQUEST_NONE
import com.android.launcher3.util.MSDLPlayerWrapper
import com.android.launcher3.util.Themes
import com.android.launcher3.util.WallpaperThemeManager
import com.google.android.msdl.data.model.MSDLToken
import com.malt03.textlauncher.ui.allapps.AllAppsSwipeController
import com.malt03.textlauncher.ui.allapps.AllAppsTransitionController
import com.malt03.textlauncher.ui.allapps.AllAppsView
import com.malt03.textlauncher.ui.home.HomeController
import com.android.launcher3.views.ActivityContext
import com.malt03.textlauncher.ui.search.SearchResults

/**
 * The home screen. The search field always has focus and the keyboard is always shown
 * (docs/spec.md 1).
 */
class LauncherActivity : AppCompatActivity(), HomeController.Host, ActivityContext.Provider {

    private val app get() = application as TextLauncherApplication

    private lateinit var root: LauncherRootLayout
    private lateinit var results: SearchResults
    private lateinit var allApps: AllAppsView

    /** docs/spec.md 6.5: the search in the all apps list, on the sheet under the field. */
    private lateinit var allAppsResults: SearchResults
    private lateinit var allAppsTransition: AllAppsTransitionController

    /** LauncherState NORMAL / ALL_APPS: whether the all apps list is open or going to be. */
    private var isAllAppsOpen = false

    /** AppsSearchContainerLayout.mSearchQueryBuilder */
    private val searchQueryBuilder = SpannableStringBuilder().also { Selection.setSelection(it, 0) }
    private lateinit var home: HomeController

    /** docs/spec.md 5.4: while editing, the keyboard is down and the field has no focus. */
    private var isEditMode = false

    private var apps: List<AppEntry> = emptyList()
    private val appsListener: (List<AppEntry>) -> Unit = {
        apps = it
        refreshResults()
        home.onAppsChanged()
        allApps.setApps(it)
    }
    private val iconsListener: () -> Unit = {
        results.onIconsChanged()
        allAppsResults.onIconsChanged()
        home.rebuild()
        allApps.setApps(apps)
    }

    /** Launcher.mRotationHelper */
    private val rotationHelper = RotationHelper(this)

    /** InvariantDeviceProfile: the grid of the display the activity was created on. */
    private lateinit var gridSpec: GridSpec

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

        gridSpec = GridSpec.from(this)
        root = LauncherRootLayout(this)
        setContentView(root)

        results = SearchResults(
            this,
            app.iconLoader,
            app.searchStore,
            gridSpec,
            onClick = { entry, view -> launch(entry, view, fromSearch = true) },
            onLongClick = { entry, view -> home.onSearchResultLongPress(entry, view) },
        )
        home = HomeController(app, root, this)
        root.contentArea.addView(home.workspace)
        root.contentArea.addView(results.view)
        allApps = AllAppsView(this, home.activityContext, app.iconLoader, gridSpec)
        root.allAppsView = allApps
        allAppsResults = SearchResults(
            this,
            app.iconLoader,
            app.searchStore,
            gridSpec,
            onClick = { entry, view -> launch(entry, view, fromSearch = true) },
            onLongClick = { entry, view -> home.onSearchResultLongPress(entry, view) },
            inAllApps = true,
        )
        root.allAppsSheet.setSearchResults(allAppsResults.view)
        root.onBottomInsetChanged = allAppsResults::setBottomInset
        allAppsTransition = AllAppsTransitionController(root)
        root.allAppsTouchInterceptor = AllAppsSwipeController(this, root, allAppsTransition, allAppsSwipeCallbacks)::onTouch

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
        // docs/spec.md 6.5: tapping the field with the all apps list open searches in it; the field
        // gets the focus and the keyboard comes up, over the list.
        root.searchBar.onFocusChanged = { hasFocus ->
            if (hasFocus && isAllAppsOpen && !allAppsTransition.isAnimating) {
                WindowCompat.getInsetsController(window, root.searchBar.editText).show(WindowInsets.Type.ime())
            }
        }
        root.onKeyboardHidden = ::onKeyboardHidden

        // docs/spec.md 1.4: the back gesture itself does nothing on the home screen.
        // Launcher.getOnBackAnimationCallback: a drag is canceled first (docs/spec.md 5.4).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (home.isDragging) {
                    home.cancelDrag()
                    return
                }
                if (home.closeTopFloatingView()) return
                // docs/spec.md 6.6: back closes the all apps list.
                if (root.isAllAppsShown) {
                    closeAllApps(animate = true)
                    return
                }
                // docs/spec.md 1.4: a search result has keyboard focus and the keyboard is down, so the
                // back gesture reaches here; it clears the query like closing the keyboard does.
                if (!root.searchBar.editText.hasFocus()) {
                    clearQuery()
                    focusSearch()
                }
            }
        })

        app.appRepository.addListener(appsListener)
        app.iconLoader.addChangeListener(iconsListener)
        // Launcher.onCreate
        rotationHelper.initialize()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rotationHelper.onConfigurationChanged(newConfig)
        // InvariantDeviceProfile.onConfigChanged: another display (a foldable's) or a new display
        // size has another grid; Launcher.onIdpChanged reloads with it. TextLauncher: the activity
        // is created again, and loading migrates the items into the new grid.
        if (GridSpec.from(this) != gridSpec) recreate()
    }

    override fun onDestroy() {
        rotationHelper.destroy()
        app.appRepository.removeListener(appsListener)
        app.iconLoader.removeChangeListener(iconsListener)
        home.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button while already on the home screen.
        home.cancelDrag()
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
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
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

    private fun focusSearch() {
        // docs/spec.md 6.4: with the all apps list open, the field has no focus or keyboard.
        if (isEditMode || root.isAllAppsShown) return
        val editText = root.searchBar.editText
        editText.requestFocus()
        WindowCompat.getInsetsController(window, editText).show(WindowInsets.Type.ime())
    }

    private fun clearQuery() {
        root.searchBar.editText.text?.clear()
        // AppsSearchContainerLayout.clearSearchResult
        searchQueryBuilder.clear()
        searchQueryBuilder.clearSpans()
        Selection.setSelection(searchQueryBuilder, 0)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        preDispatchKeyEvent(event)
        return super.dispatchKeyEvent(event)
    }

    /**
     * AppsSearchContainerLayout.preDispatchKeyEvent: typing while a search result has keyboard focus
     * focuses the search field first, so that it gets the key.
     */
    private fun preDispatchKeyEvent(event: KeyEvent) {
        // Determine if the key event was actual text, if so, focus the search bar and then dispatch
        // the key normally so that it can process this key event
        val editText = root.searchBar.editText
        if (!editText.isFocused && event.action == KeyEvent.ACTION_DOWN) {
            val unicodeChar = event.unicodeChar
            val isKeyNotWhitespace = unicodeChar > 0 &&
                !Character.isWhitespace(unicodeChar) && !Character.isSpaceChar(unicodeChar)
            if (isKeyNotWhitespace) {
                val gotKey = TextKeyListener.getInstance().onKeyDown(editText, searchQueryBuilder,
                    event.keyCode, event)
                if (gotKey && searchQueryBuilder.isNotEmpty()) {
                    if (isAllAppsOpen) {
                        // docs/spec.md 6.5: the search in the all apps list.
                        editText.requestFocus()
                    } else {
                        focusSearch()
                    }
                }
            }
        }
    }

    /** docs/spec.md 1.4: closing the keyboard with back clears the query and shows it again. */
    private fun onKeyboardHidden() {
        // docs/spec.md 6.5: closing the keyboard while searching in the all apps list keeps the
        // query and shows the filtered list without the focus.
        if (isAllAppsOpen && !allAppsTransition.isAnimating && root.searchBar.editText.hasFocus()) {
            root.requestFocus()
            return
        }
        // docs/spec.md 6.4: the keyboard is not watched while the all apps list is open.
        if (isEditMode || root.isAllAppsShown || isLaunching || !isResumedState || !hasWindowFocus()) return
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
     * The all apps swipe (docs/spec.md 6.1, 6.2, 6.6), as AllAppsSwipeController.canInterceptTouch
     * and the state changes of LauncherStateManager.
     */
    private val allAppsSwipeCallbacks = object : AllAppsSwipeController.Callbacks {
        override fun canSwipe(): Boolean =
            !isEditMode && !home.isDragging && AbstractFloatingView.getTopOpenView(home.activityContext) == null

        override fun canOpenFrom(ev: MotionEvent): Boolean {
            if (isInSearchBar(ev)) return true
            // docs/spec.md 6.1: the home screen scrolled to its bottom, or not scrolling at all.
            return currentQuery().isEmpty() && ev.y < root.closedBarTop && !home.scrollView.canScrollVertically(1)
        }

        /**
         * ActivityAllAppsContainerView.shouldContainerScroll: the search field, the sheet's handle
         * area, or the list at its top.
         */
        override fun canCloseFrom(ev: MotionEvent): Boolean {
            if (isInSearchBar(ev)) return true
            val handleTop = root.sheetTop
            val handleBottom = handleTop + root.sheetHandleAreaHeight
            return ev.y >= handleTop && (ev.y < handleBottom || allApps.shouldContainerScroll(ev, root))
        }

        override fun onThresholdCrossed(opening: Boolean) {
            // AllAppsTransitionController.setStateWithAnimation: the haptic for going to all apps.
            if (Flags.msdlFeedback()) {
                MSDLPlayerWrapper.INSTANCE.get(this@LauncherActivity).playToken(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
            } else {
                root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            }
            // docs/spec.md 6.2: the keyboard goes down as the field comes up.
            if (opening) prepareAllAppsOpen()
        }

        override fun onSettling(open: Boolean) {
            if (open) prepareAllAppsOpen() else prepareAllAppsClose()
        }

        override fun onSettled(open: Boolean) {
            if (open) isAllAppsOpen = true else onAllAppsClosed(focus = true)
        }
    }

    private fun isInSearchBar(ev: MotionEvent): Boolean {
        val bar = root.searchBarArea
        return ev.y >= bar.y && ev.y < bar.y + bar.height
    }

    /**
     * docs/spec.md 6.4: the field has no focus and the keyboard goes down; the query is cleared
     * (the list replaces it).
     */
    private fun prepareAllAppsOpen() {
        isAllAppsOpen = true
        home.closeFloatingViews(animate = false)
        clearQuery()
        root.requestFocus()
        WindowCompat.getInsetsController(window, root).hide(WindowInsets.Type.ime())
    }

    /** docs/spec.md 6.6: closing, the field gets the focus and the keyboard comes back with it. */
    private fun prepareAllAppsClose() {
        isAllAppsOpen = false
        // docs/spec.md 6.6: closing clears the query (of the search in the list).
        clearQuery()
        root.holdFieldAboveKeyboard = true
        val editText = root.searchBar.editText
        editText.requestFocus()
        WindowCompat.getInsetsController(window, editText).show(WindowInsets.Type.ime())
    }

    /** Opens the all apps list with the state animation (docs/spec.md 6.3, the menu's "Apps"). */
    @Suppress("unused")
    private fun openAllApps() {
        if (isEditMode || isAllAppsOpen) return
        prepareAllAppsOpen()
        allAppsTransition.animateTo(open = true) {}
    }

    /**
     * docs/spec.md 6.6: closes the list; the field goes back above the keyboard with the focus
     * ([focus]). Without [animate], at once (the home button, leaving the launcher).
     */
    private fun closeAllApps(animate: Boolean, focus: Boolean = true) {
        if (!root.isAllAppsShown && !isAllAppsOpen) return
        if (animate) {
            prepareAllAppsClose()
            allAppsTransition.animateTo(open = false) { onAllAppsClosed(focus) }
        } else {
            allAppsTransition.cancelAnimation()
            isAllAppsOpen = false
            clearQuery()
            allAppsTransition.progress = 0f
            onAllAppsClosed(focus)
        }
    }

    private fun onAllAppsClosed(focus: Boolean) {
        isAllAppsOpen = false
        allApps.reset()
        refreshResults()
        if (focus) focusSearch()
    }

    private fun currentQuery(): String = root.searchBar.editText.text?.toString().orEmpty()

    /** docs/spec.md 2.5: the results replace the home screen while there is a query. */
    private fun refreshResults() {
        val query = Normalizer.normalize(currentQuery())
        if (isAllAppsOpen) {
            // docs/spec.md 6.5: the results replace the list on the sheet, without an animation.
            results.hide()
            home.scrollView.visibility = View.VISIBLE
            if (query.isEmpty()) {
                allAppsResults.hide()
                allApps.visibility = View.VISIBLE
            } else {
                allAppsResults.show(query, apps)
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
            results.show(query, apps)
            home.scrollView.visibility = View.GONE
        }
    }

    /** docs/spec.md 4: the long-press menu's "Edit search terms". */
    override fun editSearchTerms(entry: AppEntry) {
        SearchTermsDialog.show(this, entry, app.searchStore) {
            refreshResults()
            focusSearch()
        }
    }

    override fun onEditModeChanged(editing: Boolean) {
        isEditMode = editing
        // Launcher.onStateSetStart: the rotation is locked while the workspace icons are dragged
        // (SPRING_LOADED), and the lock is cleared going back to NORMAL.
        rotationHelper.setCurrentStateRequest(if (editing) REQUEST_LOCK else REQUEST_NONE)
        if (editing) {
            // docs/spec.md 6.4: a drag from the all apps list closes it and shows the home screen.
            closeAllApps(animate = false, focus = false)
            clearQuery()
            root.requestFocus()
            WindowCompat.getInsetsController(window, root).hide(WindowInsets.Type.ime())
        } else {
            focusSearch()
        }
    }

    override fun launch(entry: AppEntry, view: View) = launch(entry, view, fromSearch = false)

    /** An item added from the long-press menu shows on the home screen, so the query goes. */
    override fun showHome() = clearQuery()

    /** ActivityContext.lookupContext finds Launcher3's ActivityContext through the activity. */
    override fun getActivityContext(): ActivityContext = home.activityContext

    private fun launchTopResult() {
        val shown = if (isAllAppsOpen) allAppsResults else results
        val top = shown.topResult ?: return
        launch(top, shown.topResultView, fromSearch = true)
    }

    /** Launcher.startShortcut, for an app shortcut in the long-press menu or on the home screen. */
    override fun startShortcut(packageName: String, id: String, view: View, user: UserHandle) {
        val location = IntArray(2).also { view.getLocationOnScreen(it) }
        val bounds = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        val options = ActivityOptions.makeScaleUpAnimation(view, 0, 0, view.width, view.height).toBundle()
        isLaunching = true
        try {
            getSystemService(LauncherApps::class.java).startShortcut(packageName, id, bounds, options, user)
        } catch (e: Exception) {
            isLaunching = false
        }
    }

    private fun launch(entry: AppEntry, view: View?, fromSearch: Boolean) {
        if (fromSearch) app.searchStore.recordLaunch(currentQuery(), entry.key)
        val launcherApps = getSystemService(LauncherApps::class.java)
        val bounds = view?.let { v ->
            val location = IntArray(2).also { v.getLocationOnScreen(it) }
            Rect(location[0], location[1], location[0] + v.width, location[1] + v.height)
        }
        val options = view?.let { ActivityOptions.makeScaleUpAnimation(it, 0, 0, it.width, it.height).toBundle() }
        isLaunching = true
        try {
            launcherApps.startMainActivity(entry.component, entry.user, bounds, options)
        } catch (e: Exception) {
            isLaunching = false
        }
        // The query and results stay while the app opens over them; onStop clears them once
        // the launcher is hidden, so the home screen never flashes in between.
    }
}
