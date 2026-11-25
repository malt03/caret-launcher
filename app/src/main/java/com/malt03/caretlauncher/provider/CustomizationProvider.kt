package com.malt03.caretlauncher.provider

import android.Manifest.permission.BIND_WALLPAPER
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.android.launcher3.R
import com.malt03.caretlauncher.CaretLauncherApplication
import java.security.MessageDigest

/**
 * Lets the system "Wallpaper & style" app switch themed icons and the icon shape, and show the
 * home screen's preview (docs/spec.md 7.3 and the appendix).
 *
 * Launcher3's LauncherCustomizationProvider at android-17.0.0_r1: the caller check, and the
 * requests handed to GridCustomizationsProxy (the derived copy in compat, which the app's
 * [CaretLauncherApplication.customizationsProxy] is).
 */
class CustomizationProvider : ContentProvider() {
    private val proxy get() = (context!!.applicationContext as CaretLauncherApplication).customizationsProxy

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        enforceCallerPermission()
        return proxy.query(uri, projection, selection, selectionArgs, sortOrder)
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        enforceCallerPermission()
        return proxy.update(uri, values ?: ContentValues(), selection, selectionArgs, null)
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        enforceCallerPermission()
        return proxy.call(method, arg, extras ?: Bundle())
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
}
