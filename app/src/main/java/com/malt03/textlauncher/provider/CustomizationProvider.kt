package com.malt03.textlauncher.provider

import android.Manifest.permission.BIND_WALLPAPER
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.android.launcher3.R
import com.android.launcher3.shapes.ShapesProvider
import com.malt03.textlauncher.TextLauncherApplication
import java.security.MessageDigest

/**
 * Lets the system "Wallpaper & style" app switch themed icons and the icon shape
 * (docs/spec.md 7.3 and the appendix).
 *
 * The contract follows Launcher3 at android-17.0.0_r1: graphics/LauncherCustomizationProvider.kt
 * (the caller check) and graphics/GridCustomizationsProxy.java (paths and columns).
 * Grid options and the "hide labels" option are not offered.
 */
class CustomizationProvider : ContentProvider() {
    private val settings get() = (context!!.applicationContext as TextLauncherApplication).iconSettings

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        enforceCallerPermission()
        return when (uri.path) {
            SHAPE_OPTIONS -> {
                val context = context!!
                val selected = settings.shape.key
                MatrixCursor(arrayOf(KEY_SHAPE_KEY, KEY_SHAPE_TITLE, KEY_PATH, KEY_IS_DEFAULT)).apply {
                    for (shape in ShapesProvider.iconShapes) {
                        newRow()
                            .add(KEY_SHAPE_KEY, shape.key)
                            .add(KEY_SHAPE_TITLE, context.getString(shape.titleId))
                            .add(KEY_PATH, shape.pathString)
                            .add(KEY_IS_DEFAULT, shape.key == selected)
                    }
                }
            }
            GET_ICON_THEMED, ICON_THEMED ->
                MatrixCursor(arrayOf(BOOLEAN_VALUE)).apply {
                    newRow().add(BOOLEAN_VALUE, if (settings.isThemed) 1 else 0)
                }
            // Not offered: grid options, hiding labels (labels are always hidden on the home screen).
            else -> null
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        enforceCallerPermission()
        val result = when (uri.path) {
            SET_SHAPE -> {
                val key = values?.getAsString(KEY_SHAPE_KEY)
                if (key != null && settings.setShape(key)) SUCCESS else FAILURE
            }
            ICON_THEMED, SET_ICON_THEMED -> {
                val themed = values?.getAsBoolean(BOOLEAN_VALUE)
                if (themed != null) {
                    settings.isThemed = themed
                    SUCCESS
                } else {
                    FAILURE
                }
            }
            else -> FAILURE
        }
        if (result != FAILURE) context!!.contentResolver.notifyChange(uri, null)
        return result
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        enforceCallerPermission()
        // get_preview / get_preview_bitmap: no home screen preview is rendered yet.
        return null
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/launcher_grid"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    // Launcher3 LauncherCustomizationProvider.enforceCallerPermission.
    private fun enforceCallerPermission() {
        val ctx = context!!
        if (ctx.checkCallingPermission(BIND_WALLPAPER) == PackageManager.PERMISSION_GRANTED) return
        if (ctx.checkCallingPermission(permGridControl) == PackageManager.PERMISSION_GRANTED)
            return

        // Temporary change until the clients migrate to the new permission
        val source = callingAttributionSource?.packageName ?: throw genericAccessException()
        @Suppress("DEPRECATION")
        val signatures =
            ctx.packageManager.getPackageInfo(source, PackageManager.GET_SIGNATURES)?.signatures
                ?: throw genericAccessException()
        val signature =
            MessageDigest.getInstance("SHA-256")
                .apply { signatures.forEach { update(it.toByteArray()) } }
                .digest()
                .joinToString("") { String.format("%02x", it) }
        if (ctx.resources.getStringArray(R.array.grid_control_known_signers).contains(signature))
            return
        throw genericAccessException()
    }

    private fun genericAccessException() =
        SecurityException(
            "Permission Denial: opening provider ${javaClass.name} (pid=${Binder.getCallingPid()}, uid=${Binder.getCallingUid()}) requires $BIND_WALLPAPER or $permGridControl"
        )

    // Launcher3's PERM_GRID_CONTROL ("$APPLICATION_ID.permission.GRID_CONTROL").
    private val permGridControl get() = "${context!!.packageName}.permission.GRID_CONTROL"

    private companion object {
        const val KEY_SHAPE_KEY = "shape_key"
        const val KEY_SHAPE_TITLE = "shape_title"
        const val KEY_PATH = "path"
        const val KEY_IS_DEFAULT = "is_default"
        const val BOOLEAN_VALUE = "boolean_value"

        const val SHAPE_OPTIONS = "/shape_options"
        const val SET_SHAPE = "/shape"
        const val GET_ICON_THEMED = "/get_icon_themed"
        const val SET_ICON_THEMED = "/set_icon_themed"
        const val ICON_THEMED = "/icon_themed"

        const val SUCCESS = 1
        const val FAILURE = 0
    }
}
