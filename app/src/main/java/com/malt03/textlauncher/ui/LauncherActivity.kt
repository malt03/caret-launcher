package com.malt03.textlauncher.ui

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
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
import com.malt03.textlauncher.ui.search.ResultsAdapter

/**
 * The home screen. The search field always has focus and the keyboard is always shown
 * (docs/spec.md 1).
 */
class LauncherActivity : AppCompatActivity() {
    private val app get() = application as TextLauncherApplication

    private lateinit var root: LauncherRootLayout
    private lateinit var results: RecyclerView
    private lateinit var resultsAdapter: ResultsAdapter

    private var apps: List<AppEntry> = emptyList()
    private val appsListener: (List<AppEntry>) -> Unit = { apps = it; refreshResults() }

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
            onLongClick = { _, _ -> false },
        )
        results = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, grid.columns, RecyclerView.VERTICAL, true)
            val sidePadding = resources.getDimensionPixelSize(R.dimen.search_bar_margin_horizontal)
            setPadding(sidePadding, 0, sidePadding, 0)
            clipToPadding = false
            adapter = resultsAdapter
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            visibility = View.GONE
        }
        root.contentArea.addView(results)

        val editText = root.searchBar.editText
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = refreshResults()
        })
        root.searchBar.onEnter = { launchTopResult() }
        root.onKeyboardHidden = ::onKeyboardHidden

        // docs/spec.md 1.4: the back gesture itself does nothing on the home screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        app.appRepository.addListener(appsListener)
    }

    override fun onDestroy() {
        app.appRepository.removeListener(appsListener)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button while already on the home screen.
        clearQuery()
        focusSearch()
    }

    override fun onResume() {
        super.onResume()
        isResumedState = true
        isLaunching = false
        focusSearch()
    }

    override fun onPause() {
        isResumedState = false
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // docs/spec.md 1.2: the query is cleared every time the launcher comes back.
        clearQuery()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) focusSearch()
    }

    private fun focusSearch() {
        val editText = root.searchBar.editText
        editText.requestFocus()
        WindowCompat.getInsetsController(window, editText).show(WindowInsets.Type.ime())
    }

    private fun clearQuery() {
        root.searchBar.editText.text?.clear()
    }

    /** docs/spec.md 1.4: closing the keyboard with back clears the query and shows it again. */
    private fun onKeyboardHidden() {
        if (isLaunching || !isResumedState || !hasWindowFocus()) return
        clearQuery()
        focusSearch()
    }

    private fun currentQuery(): String = root.searchBar.editText.text?.toString().orEmpty()

    private fun refreshResults() {
        val query = Normalizer.normalize(currentQuery())
        if (query.isEmpty()) {
            results.visibility = View.GONE
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
    }

    private fun launchTopResult() {
        val top = resultsAdapter.items.firstOrNull() ?: return
        val view = results.findViewHolderForAdapterPosition(0)?.itemView
        launch(top, view, fromSearch = true)
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
            return
        }
        clearQuery()
    }
}
