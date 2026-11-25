package com.malt03.caretlauncher.data

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import com.android.intentresolver.TargetPresentationGetter
import java.text.Collator

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
         * itself), sorted by label like the share sheet (ChooserListAdapter's
         * DisplayResolveInfoAzInfoComparator: the locale's collator). Slow: not on the main thread.
         */
        fun available(context: Context): List<ShareTarget> {
            val factory = presentationFactory(context)
            val collator = Collator.getInstance(context.resources.configuration.locales[0])
            return context.packageManager.queryIntentActivities(sendIntent(), PackageManager.MATCH_DEFAULT_ONLY)
                .filter { it.activityInfo.exported && it.activityInfo.packageName != context.packageName }
                .map { target(factory, it) }
                .distinctBy { it.component }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
        }

        /** [component] as a target, or null when it no longer takes the text. */
        fun resolve(context: Context, component: ComponentName): ShareTarget? {
            // Asked as [available] asks, so that a target resolves exactly when the list has it.
            val info = context.packageManager
                .queryIntentActivities(sendIntent().setPackage(component.packageName), PackageManager.MATCH_DEFAULT_ONLY)
                .firstOrNull { it.activityInfo.name == component.className && it.activityInfo.exported }
                ?: return null
            return target(presentationFactory(context), info)
        }

        /** The share sheet's labels and icons (ChooserTargetActionsDialogFragment's factory). */
        private fun presentationFactory(context: Context) = TargetPresentationGetter.Factory(
            context.packageManager,
            context.getSystemService(ActivityManager::class.java).launcherLargeIconDensity,
        )

        private fun target(factory: TargetPresentationGetter.Factory, info: ResolveInfo): ShareTarget {
            val getter = factory.makePresentationGetter(info)
            val label = getter.label
            // ResolverListAdapter.ViewHolder.bindLabel: the sublabel is not shown when it is the label.
            val subLabel = getter.subLabel?.takeUnless { it.isEmpty() || it == label }
            val activity = info.activityInfo
            return ShareTarget(ComponentName(activity.packageName, activity.name), label, subLabel, getter.icon)
        }
    }
}

/**
 * An activity the typed text is shared with, as the share sheet shows it (TargetPresentationGetter):
 * the app's label and icon, with the activity's label as [subLabel] when it differs. Apps that may
 * substitute them (SUBSTITUTE_SHARE_TARGET_APP_NAME_AND_ICON) show the activity's instead.
 */
class ShareTarget(
    val component: ComponentName,
    val label: String,
    val subLabel: String?,
    val icon: Drawable,
) {
    /** The label and sublabel, for a description that tells the targets of one app apart. */
    val description: String get() = listOfNotNull(label, subLabel).joinToString(" ")
}
