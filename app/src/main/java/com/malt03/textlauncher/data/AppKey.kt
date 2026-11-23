package com.malt03.textlauncher.data

import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import com.android.launcher3.pm.UserCache

/**
 * The persistent form of an app's identity (docs/spec.md 3.1): Launcher3's ComponentKey, with the
 * user as its serial number (as LauncherSettings.Favorites.PROFILE_ID saves it). The home screen,
 * the search learning and the search terms keep apps by it.
 */
object AppKey {
    fun of(component: ComponentName, userSerial: Long): String = "${component.flattenToString()}#$userSerial"

    fun of(context: Context, component: ComponentName, user: UserHandle): String =
        of(component, UserCache.INSTANCE.get(context).getSerialNumberForUser(user))

    fun componentOf(key: String): ComponentName? = ComponentName.unflattenFromString(key.substringBeforeLast('#'))
}
