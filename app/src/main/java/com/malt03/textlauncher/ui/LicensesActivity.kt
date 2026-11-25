package com.malt03.textlauncher.ui

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.widget.HorizontalScrollView
import android.widget.TextView
import com.android.launcher3.R as L3R
import com.malt03.textlauncher.R

/**
 * The licenses the APK carries in assets/licenses/ (the NOTICE, then the Apache License 2.0;
 * app/build.gradle.kts makes them), opened from [AppSettingsActivity]. The texts are wrapped at 80
 * columns, so they keep their lines in a monospace font and scroll sideways on a narrow screen.
 */
class LicensesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = dp(16)
        val text = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(getColor(L3R.color.materialColorOnSurface))
            setTextIsSelectable(true)
            setPadding(padding, 0, padding, padding)
            text = FILES.joinToString("\n\n") { name ->
                assets.open("licenses/$name").bufferedReader().use { it.readText() }.trimEnd()
            }
        }
        setSettingsPage(getString(R.string.licenses), HorizontalScrollView(this).apply { addView(text) })
    }

    private companion object {
        val FILES = listOf("NOTICE", "LICENSE")
    }
}
