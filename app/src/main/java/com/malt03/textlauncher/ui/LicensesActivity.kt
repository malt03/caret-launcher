package com.malt03.textlauncher.ui

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.WindowInsets
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.malt03.textlauncher.R

/**
 * The licenses the APK carries in assets/licenses/ (the NOTICE, then the Apache License 2.0;
 * app/build.gradle.kts makes them), with the app's version. The launcher has no settings screen
 * (docs/spec.md 8), so this opens only from the system's App info, through its button for the
 * app's settings (Intent.ACTION_APPLICATION_PREFERENCES).
 *
 * The app's name and version head the page. The texts are wrapped at 80 columns, so they keep
 * their lines in a monospace font and scroll sideways on a narrow screen.
 */
class LicensesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt()
        val title = TextView(this).apply {
            setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Headline)
            text = getString(R.string.app_name)
            setPadding(padding, padding, padding, 0)
        }
        val version = TextView(this).apply {
            setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium)
            text = packageManager.getPackageInfo(packageName, 0).versionName
            setPadding(padding, 0, padding, 0)
        }
        val text = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextIsSelectable(true)
            setPadding(padding, padding, padding, padding)
            text = FILES.joinToString("\n\n") { name ->
                assets.open("licenses/$name").bufferedReader().use { it.readText() }.trimEnd()
            }
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(version)
            addView(HorizontalScrollView(context).apply { addView(text) })
        }
        val scrollView = ScrollView(this).apply {
            clipToPadding = false
            addView(page)
            // The window is edge to edge: the page starts below the status bar and scrolls behind
            // the navigation bar.
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        setContentView(scrollView)
    }

    private companion object {
        val FILES = listOf("NOTICE", "LICENSE")
    }
}
