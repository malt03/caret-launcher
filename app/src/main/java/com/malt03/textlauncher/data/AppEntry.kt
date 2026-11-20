package com.malt03.textlauncher.data

import android.content.ComponentName
import android.content.pm.LauncherActivityInfo
import android.os.UserHandle

/**
 * A launchable activity. Identified by component + user (docs/spec.md 3.1).
 * [key] is the persistent form of that identity.
 */
class AppEntry(
    val key: String,
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    val info: LauncherActivityInfo,
) {
    override fun equals(other: Any?): Boolean = other is AppEntry && other.key == key
    override fun hashCode(): Int = key.hashCode()

    companion object {
        fun keyOf(component: ComponentName, userSerial: Long): String =
            "${component.flattenToString()}#$userSerial"

        fun componentOf(key: String): ComponentName? =
            ComponentName.unflattenFromString(key.substringBeforeLast('#'))
    }
}
