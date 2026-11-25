package com.malt03.textlauncher.ui.home

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.LoaderTask
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.provider.LauncherDbUtils.asSequence
import com.android.launcher3.provider.LauncherDbUtils.itemIdMatch
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.IntSet
import com.malt03.textlauncher.ui.GridSpec

/**
 * What the home screen does in Launcher3's place while LoaderTask loads the model, on the model
 * thread: the grid migration in the one database (ModelDbController.attemptMigrateDb), and the
 * ghost widgets of the app's widget host (ModelDbController.removeGhostWidgets).
 */
internal class HomeLoaderHooks(private val context: Context) : LoaderTask.Hooks {

    override fun migrateGridIfNeeded(dbController: ModelDbController) {
        backUpFavoritesOnce(dbController)
        // LoaderCursor.checkItemPlacement reads the grid from InvariantDeviceProfile, which the
        // home screen sets; until then, the grid from the device (HomeGridInitializer).
        LauncherAppState.getIDP(context).getDeviceProfile(context)

        val items = readDesktopItems(dbController)
        val before = items.associateWith { Placement(it) }
        val leftOut = GridSizeMigration.migrateGridIfNeeded(context, GridSpec.from(context), items) ?: return
        // GridSizeMigrationLogic writes the destination grid's database; here the moved items are
        // written in the one database, and the items that fit on no page are deleted with their
        // folders' contents.
        val removed = leftOut.toSet()
        dbController.newTransaction().use { t ->
            for (item in items) {
                if (item in removed || Placement(item) == before[item]) continue
                val values = ContentValues().apply {
                    put(Favorites.CELLX, item.cellX)
                    put(Favorites.CELLY, item.cellY)
                    put(Favorites.SPANX, item.spanX)
                    put(Favorites.SPANY, item.spanY)
                }
                dbController.update(values, itemIdMatch(item.id), null)
            }
            for (item in removed) {
                Log.d(TAG, "grid migration: deleting $item")
                dbController.delete("${Favorites._ID} = ${item.id} OR ${Favorites.CONTAINER} = ${item.id}", null)
            }
            t.commit()
        }
    }

    override fun removeGhostWidgets(validWidgets: IntSet) {
        val valid = validWidgets.toSet()
        MAIN_EXECUTOR.execute {
            // TextLauncher: the ids of an add flow in progress, and of its result waiting for the
            // load, are not ghosts.
            HomeWidgetHost(context).removeGhostWidgets(valid + HomeWidgetAddFlow.pendingWidgetIds)
        }
    }

    /**
     * TextLauncher: before Launcher3's loader first reads the favorites (it deletes the rows it
     * cannot resolve, LoaderCursor.markDeleted), they are copied to [BACKUP_TABLE] once.
     */
    private fun backUpFavoritesOnce(dbController: ModelDbController) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BACKED_UP, false)) return
        dbController.getTable().execSQL(
            "CREATE TABLE IF NOT EXISTS $BACKUP_TABLE AS SELECT * FROM ${Favorites.TABLE_NAME}",
        )
        prefs.edit().putBoolean(KEY_BACKED_UP, true).apply()
    }

    /**
     * DbReader.loadAllEntries: the items on the home page (CONTAINER_DESKTOP) with their cells and
     * spans, widgets as LauncherAppWidgetInfo (GridSizeMigration reads their providers' min spans).
     */
    private fun readDesktopItems(dbController: ModelDbController): List<ItemInfo> =
        dbController.query(
            arrayOf(
                Favorites._ID, Favorites.ITEM_TYPE, Favorites.CELLX, Favorites.CELLY, Favorites.SPANX,
                Favorites.SPANY, Favorites.APPWIDGET_ID, Favorites.APPWIDGET_PROVIDER,
            ),
            "${Favorites.CONTAINER} = ${Favorites.CONTAINER_DESKTOP}",
            null,
            null,
        ).use { c ->
            c.asSequence().map {
                val itemType = c.getInt(1)
                val provider = if (c.isNull(7)) null else ComponentName.unflattenFromString(c.getString(7))
                val item = if (itemType == Favorites.ITEM_TYPE_APPWIDGET && provider != null) {
                    LauncherAppWidgetInfo(c.getInt(6), provider)
                } else {
                    ItemInfo()
                }
                item.id = c.getInt(0)
                item.itemType = itemType
                item.container = Favorites.CONTAINER_DESKTOP
                item.cellX = c.getInt(2)
                item.cellY = c.getInt(3)
                item.spanX = c.getInt(4)
                item.spanY = c.getInt(5)
                item
            }.toList()
        }

    private data class Placement(val cellX: Int, val cellY: Int, val spanX: Int, val spanY: Int) {
        constructor(item: ItemInfo) : this(item.cellX, item.cellY, item.spanX, item.spanY)
    }

    private companion object {
        const val TAG = "HomeLoaderHooks"
        const val PREFS = "home_loader"
        const val KEY_BACKED_UP = "favorites_backed_up"
        const val BACKUP_TABLE = "favorites_backup_textlauncher"
    }
}
