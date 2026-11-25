package com.malt03.caretlauncher.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import com.malt03.caretlauncher.R

/**
 * The app's settings, which the system's App info opens from its "Additional settings in the
 * app" row (Intent.ACTION_APPLICATION_PREFERENCES). The launcher keeps its settings elsewhere
 * (docs/spec.md 8), so this has only the licenses ([LicensesActivity]); the version is in App info.
 */
class AppSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(16))
            addView(
                settingsRow(getString(R.string.licenses)) {
                    startActivity(Intent(this@AppSettingsActivity, LicensesActivity::class.java))
                },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
            )
        }
        setSettingsPage(getString(R.string.app_name), rows)
    }
}
