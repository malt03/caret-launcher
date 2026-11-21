package com.malt03.textlauncher.data

import com.android.launcher3.model.data.AppInfo

/**
 * An app in the all apps list: Launcher3's AppInfo (AppInfo(LauncherActivityInfo, ...)), with the
 * [entry] it was made from, which launching and the long-press menu use.
 */
class AllAppsAppInfo(val entry: AppEntry) : AppInfo(entry.info, entry.user)
