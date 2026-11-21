package com.malt03.textlauncher.ui

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.os.UserHandle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsets
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.malt03.textlauncher.R
import androidx.recyclerview.widget.RecyclerView
import com.malt03.textlauncher.TextLauncherApplication
import com.malt03.textlauncher.data.AppEntry
import com.malt03.textlauncher.search.Normalizer
import com.malt03.textlauncher.search.Ranker
import com.malt03.textlauncher.search.SearchCandidate
import com.malt03.textlauncher.ui.home.HomeController
import com.android.launcher3.views.ActivityContext
import com.malt03.textlauncher.ui.search.ResultsAdapter

/**
 * The home screen. The search field always has focus and the keyboard is always shown
 * (docs/spec.md 1).
 */
class LauncherActivity : AppCompatActivity(), HomeController.Host, ActivityContext.Provider {
    private val app get() = application as TextLauncherApplication

    private lateinit var root: LauncherRootLayout
    private lateinit var results: RecyclerView
    private lateinit var resultsAdapter: ResultsAdapter
    private lateinit var home: HomeController

    /** docs/spec.md 5.4: while editing, the keyboard is down and the field has no focus. */
    private var isEditMode = false

    private var apps: List<AppEntry> = emptyList()
    private val appsListener: (List<AppEntry>) -> Unit = {
        apps = it
        refreshResults()
        home.onAppsChanged()
    }
    private val iconsListener: () -> Unit = {
        resultsAdapter.notifyItemRangeChanged(0, resultsAdapter.itemCount)
        home.rebuild()
    }

    /** True between launching an app and coming back; keyboard changes are expected then. */
    private var isLaunching = false
    private var isResumedState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        root = LauncherRootLayout(this)
        setContentView(root)

        val grid = GridSpec.from(this)
        resultsAdapter = ResultsAdapter(
            app.iconLoader,
            grid,
            onClick = { entry, view -> launch(entry, view, fromSearch = true) },
            onLongClick = { entry, iconView ->
                home.onSearchResultLongPress(entry, iconView)
                true
            },
        )
        results = NoPressDelayRecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, grid.columns, RecyclerView.VERTICAL, true)
            val sidePadding = resources.getDimensionPixelSize(R.dimen.search_bar_margin_horizontal)
            setPadding(sidePadding, 0, sidePadding, 0)
            clipToPadding = false
            adapter = resultsAdapter
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            visibility = View.GONE
        }
        home = HomeController(app, root, this)
        root.contentArea.addView(home.workspace)
        root.contentArea.addView(results)

        val editText = root.searchBar.editText
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) home.closeFolder()
                refreshResults()
            }
        })
        root.searchBar.onEnter = { launchTopResult() }
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
                if (home.isFolderOpen) home.closeFolder()
            }
        })

        app.appRepository.addListener(appsListener)
        app.iconLoader.addChangeListener(iconsListener)
    }

    override fun onDestroy() {
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
        home.closeFolder()
        clearQuery()
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
        home.closeFolder()
        clearQuery()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isEditMode) focusSearch()
    }

    private fun focusSearch() {
        if (isEditMode) return
        val editText = root.searchBar.editText
        editText.requestFocus()
        WindowCompat.getInsetsController(window, editText).show(WindowInsets.Type.ime())
    }

    private fun clearQuery() {
        root.searchBar.editText.text?.clear()
    }

    /** docs/spec.md 1.4: closing the keyboard with back clears the query and shows it again. */
    private fun onKeyboardHidden() {
        if (isEditMode || isLaunching || !isResumedState || !hasWindowFocus()) return
        // The keyboard takes the back gesture while it is shown, so closing it also closes the
        // long-press menu, which Launcher3 closes on back.
        home.closeTopFloatingView()
        clearQuery()
        focusSearch()
    }

    private fun currentQuery(): String = root.searchBar.editText.text?.toString().orEmpty()

    private fun refreshResults() {
        val query = Normalizer.normalize(currentQuery())
        if (query.isEmpty()) {
            results.visibility = View.GONE
            home.scrollView.visibility = View.VISIBLE
            resultsAdapter.submit(emptyList())
            return
        }
        val store = app.searchStore
        val candidates = apps.map { entry ->
            SearchCandidate(
                item = entry,
                key = entry.key,
                label = entry.label,
                normalizedTargets = (listOf(entry.label) + store.termsOf(entry.key)).map(Normalizer::normalize),
            )
        }
        val ranked = Ranker.rank(query, candidates) { key -> store.scoreOf(query, key) }.map { it.item }
        resultsAdapter.submit(ranked)
        results.scrollToPosition(0)
        results.visibility = View.VISIBLE
        home.scrollView.visibility = View.GONE
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
        if (editing) {
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
        val top = resultsAdapter.items.firstOrNull() ?: return
        val view = results.findViewHolderForAdapterPosition(0)?.itemView
        launch(top, view, fromSearch = true)
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
