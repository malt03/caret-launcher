package com.malt03.caretlauncher.ui.home

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.android.launcher3.R
import com.android.launcher3.notification.NotificationListener

/**
 * NotificationDotsPreference.NotificationAccessConfirmation: the dialog that takes the user to the
 * system's notification access settings, for the empty space's menu's "Notification dots"
 * (WorkspaceLongPressOptions, docs/spec.md 7.5). CaretLauncher: Launcher3 shows it in its settings
 * activity; here it is shown over the home screen.
 */
internal object NotificationAccessConfirmation {
    fun show(context: Context) {
        val msg = context.getString(
            R.string.msg_missing_notification_access,
            context.getString(com.malt03.caretlauncher.R.string.app_name),
        )
        AlertDialog.Builder(context)
            .setTitle(R.string.title_missing_notification_access)
            .setMessage(msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.title_change_settings) { _, _ ->
                val cn = ComponentName(context, NotificationListener::class.java)
                val showFragmentArgs = Bundle()
                showFragmentArgs.putString(EXTRA_FRAGMENT_HIGHLIGHT_KEY, cn.flattenToString())

                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_FRAGMENT_HIGHLIGHT_KEY, cn.flattenToString())
                    .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, showFragmentArgs)
                context.startActivity(intent)
            }
            .create()
            .show()
    }

    /** SettingsActivity.EXTRA_FRAGMENT_HIGHLIGHT_KEY */
    private const val EXTRA_FRAGMENT_HIGHLIGHT_KEY = ":settings:fragment_args_key"

    /** NotificationDotsPreference.EXTRA_SHOW_FRAGMENT_ARGS */
    private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
}
