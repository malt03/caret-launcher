package com.malt03.textlauncher.provider

import android.content.Context
import com.android.launcher3.LauncherModel
import com.android.launcher3.graphics.GridCustomizationsProxy
import com.android.launcher3.preview.LauncherPreviewRenderer
import com.malt03.textlauncher.icons.IconSettings
import com.malt03.textlauncher.ui.home.HomePreviewRenderer

/**
 * What GridCustomizationsProxy asks of the app in place of Launcher3's preferences and preview
 * context: the icon settings (ThemeManager's PREF_ICON_SHAPE and ThemePreference's themed icons)
 * and the home screen's preview ([HomePreviewRenderer]).
 */
internal class HomeCustomizations(private val settings: IconSettings) : GridCustomizationsProxy.Delegate {
    override fun getIconShape(): String = settings.shape.key

    override fun setIconShape(key: String) {
        settings.setShape(key)
    }

    override fun isIconThemed(): Boolean = settings.isThemed

    override fun setIconThemed(themed: Boolean) {
        settings.isThemed = themed
    }

    override fun newPreviewRenderer(
        context: Context,
        workspaceScreenId: Int,
        model: LauncherModel,
        themeRes: Int,
    ): LauncherPreviewRenderer = HomePreviewRenderer(context, workspaceScreenId, model, themeRes)
}
