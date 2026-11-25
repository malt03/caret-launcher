package com.malt03.textlauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.android.launcher3.util.LabelComparator

/**
 * The apps the typed text is shared with (docs/spec.md 2.7): up to [MAX] activities that take
 * ACTION_SEND of text/plain, in the user's order. Kept in the app's preferences. Accessed on the
 * main thread.
 */
class ShareTargetStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val listeners = mutableListOf<() -> Unit>()

    /** The chosen activities, in order, whether or not they are still installed. */
    var components: List<ComponentName> = load()
        private set

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /** Adds [component] at the end; does nothing once there are [MAX], or if it is chosen already. */
    fun add(component: ComponentName) {
        if (components.size >= MAX || component in components) return
        save(components + component)
    }

    fun remove(component: ComponentName) = save(components - component)

    /** Replaces the targets with [newComponents], in their order (a reorder, or dropping the gone ones). */
    fun set(newComponents: List<ComponentName>) {
        val cleaned = newComponents.distinct().take(MAX)
        if (cleaned != components) save(cleaned)
    }

    /**
     * The chosen activities that still take the text, with the label and icon they show for it
     * (ResolveInfo: the intent filter's, else the activity's, else the app's).
     */
    fun resolve(): List<ShareTarget> = components.mapNotNull { resolve(context, it) }

    private fun load(): List<ComponentName> =
        prefs.getString(KEY, null).orEmpty().lines()
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .distinct()
            .take(MAX)

    private fun save(newComponents: List<ComponentName>) {
        components = newComponents
        prefs.edit().putString(KEY, newComponents.joinToString("\n") { it.flattenToString() }).apply()
        listeners.toList().forEach { it() }
    }

    companion object {
        /** docs/spec.md 2.7: up to three buttons next to the search field. */
        const val MAX = 3
        private const val PREFS = "share_targets"
        private const val KEY = "components"

        /** ACTION_SEND of plain text, the intent the targets take. */
        fun sendIntent(): Intent = Intent(Intent.ACTION_SEND).setType("text/plain")

        /**
         * Every activity that takes the text, as the share sheet lists them (without the launcher
         * itself), sorted by label like the all apps list (LabelComparator). Slow: not on the main
         * thread.
         */
        fun available(context: Context): List<ShareTarget> {
            val pm = context.packageManager
            val comparator = LabelComparator()
            return pm.queryIntentActivities(sendIntent(), PackageManager.MATCH_DEFAULT_ONLY)
                .filter { it.activityInfo.exported && it.activityInfo.packageName != context.packageName }
                .map { info ->
                    val activity = info.activityInfo
                    ShareTarget(ComponentName(activity.packageName, activity.name), info.loadLabel(pm), info.loadIcon(pm))
                }
                .distinctBy { it.component }
                .sortedWith { a, b -> comparator.compare(a.label.toString(), b.label.toString()) }
        }

        /** [component] as a target, or null when it no longer takes the text. */
        fun resolve(context: Context, component: ComponentName): ShareTarget? {
            // Asked as [available] asks, so that a target resolves exactly when the list has it, with
            // the label and icon of its intent filter.
            val pm = context.packageManager
            val info = pm.queryIntentActivities(sendIntent().setPackage(component.packageName), PackageManager.MATCH_DEFAULT_ONLY)
                .firstOrNull { it.activityInfo.name == component.className && it.activityInfo.exported }
                ?: return null
            return ShareTarget(component, info.loadLabel(pm), info.loadIcon(pm))
        }
    }
}

/** An activity the typed text is shared with, as the system shows it in the share sheet. */
class ShareTarget(val component: ComponentName, val label: CharSequence, val icon: Drawable)
