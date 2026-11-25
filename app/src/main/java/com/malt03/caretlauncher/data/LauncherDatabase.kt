package com.malt03.caretlauncher.data

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Process
import android.os.UserManager
import android.util.Log
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.malt03.caretlauncher.search.LearningRecord

/**
 * Persistent storage for search strings, learning records and the home layout. The home layout is
 * Launcher3's favorites table (LauncherSettings.Favorites), which the home model reads and writes
 * through ModelDbController.
 */
class LauncherDatabase(private val context: Context, name: String? = NAME) :
    SQLiteOpenHelper(context, name, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE search_terms (app_key TEXT NOT NULL, term TEXT NOT NULL, " +
                "PRIMARY KEY (app_key, term))"
        )
        db.execSQL(
            "CREATE TABLE learning (query TEXT NOT NULL, app_key TEXT NOT NULL, " +
                "score REAL NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (query, app_key))"
        )
        Favorites.addTableToDb(db, myUserSerial(), false /* optional */)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // LauncherSettings.Favorites.ICON: the icon of an app shortcut, for when it cannot be
        // looked up.
        if (oldVersion < 2) db.execSQL("ALTER TABLE home_items ADD COLUMN icon BLOB")
        // Version 3: the home layout moves to Launcher3's favorites table.
        if (oldVersion < 3) migrateHomeItemsToFavorites(db)
    }

    /**
     * Copies the rows of the version 2 home_items table into the favorites table, with Launcher3's
     * item types, containers, intents and profile ids, and drops home_items. A row that cannot be
     * read (an app or shortcut without a valid key) is left out, as LoaderTask would delete it.
     */
    private fun migrateHomeItemsToFavorites(db: SQLiteDatabase) {
        Favorites.addTableToDb(db, myUserSerial(), true /* optional */)
        db.query(
            "home_items",
            arrayOf("id", "type", "container", "cell_x", "cell_y", "span_x", "span_y", "rank", "app_key", "widget_id", "provider", "icon"),
            null, null, null, null, null,
        ).use { c ->
            while (c.moveToNext()) {
                val values = migratedValues(
                    id = c.getLong(0), type = c.getInt(1), container = c.getLong(2),
                    cellX = c.getInt(3), cellY = c.getInt(4), spanX = c.getInt(5), spanY = c.getInt(6),
                    rank = c.getInt(7),
                    appKey = if (c.isNull(8)) null else c.getString(8),
                    widgetId = if (c.isNull(9)) null else c.getInt(9),
                    provider = if (c.isNull(10)) null else c.getString(10),
                    icon = if (c.isNull(11)) null else c.getBlob(11),
                )
                if (values == null) {
                    Log.w(TAG, "Dropping home item ${c.getLong(0)} that cannot be migrated")
                    continue
                }
                db.insert(Favorites.TABLE_NAME, null, values)
            }
        }
        db.execSQL("DROP TABLE home_items")
    }

    private fun migratedValues(
        id: Long, type: Int, container: Long, cellX: Int, cellY: Int, spanX: Int, spanY: Int, rank: Int,
        appKey: String?, widgetId: Int?, provider: String?, icon: ByteArray?,
    ): ContentValues? {
        val values = ContentValues()
        values.put(Favorites._ID, id)
        // Version 2: -1 was the home page; a folder's items had the folder's id.
        values.put(Favorites.CONTAINER, if (container == V2_CONTAINER_HOME) Favorites.CONTAINER_DESKTOP.toLong() else container)
        values.put(Favorites.SCREEN, 0)
        values.put(Favorites.CELLX, cellX)
        values.put(Favorites.CELLY, cellY)
        values.put(Favorites.SPANX, spanX)
        values.put(Favorites.SPANY, spanY)
        values.put(Favorites.RANK, rank)
        when (type) {
            V2_TYPE_APP -> {
                val key = appKey ?: return null
                val component = AppKey.componentOf(key) ?: return null
                val serial = key.substringAfterLast('#').toLongOrNull() ?: return null
                values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION)
                values.put(Favorites.INTENT, AppInfo.makeLaunchIntent(component).toUri(0))
                values.put(Favorites.PROFILE_ID, serial)
            }
            V2_TYPE_SHORTCUT -> {
                val key = HomeShortcutKey.parse(appKey ?: return null) ?: return null
                values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_DEEP_SHORTCUT)
                values.put(Favorites.INTENT, ShortcutKey.makeIntent(key.id, key.packageName).toUri(0))
                values.put(Favorites.PROFILE_ID, key.userSerial)
                values.put(Favorites.ICON, icon)
            }
            V2_TYPE_FOLDER -> {
                values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_FOLDER)
                values.put(Favorites.PROFILE_ID, myUserSerial())
            }
            V2_TYPE_WIDGET -> {
                val component = ComponentName.unflattenFromString(provider ?: return null) ?: return null
                values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPWIDGET)
                values.put(Favorites.APPWIDGET_ID, widgetId ?: return null)
                values.put(Favorites.APPWIDGET_PROVIDER, component.flattenToString())
                values.put(Favorites.PROFILE_ID, myUserSerial())
            }
            else -> return null
        }
        return values
    }

    /** DatabaseHelper.getDefaultUserSerial */
    private fun myUserSerial(): Long =
        context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())

    // ---- search terms ----

    fun loadSearchTerms(): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        readableDatabase.query("search_terms", arrayOf("app_key", "term"), null, null, null, null, "rowid")
            .use { c ->
                while (c.moveToNext()) result.getOrPut(c.getString(0)) { mutableListOf() } += c.getString(1)
            }
        return result
    }

    fun replaceSearchTerms(appKey: String, terms: List<String>) {
        writableDatabase.transaction {
            delete("search_terms", "app_key = ?", arrayOf(appKey))
            for (term in terms) {
                insertWithOnConflict(
                    "search_terms", null,
                    ContentValues().apply { put("app_key", appKey); put("term", term) },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
        }
    }

    // ---- learning ----

    fun loadLearning(): Map<String, Map<String, LearningRecord>> {
        val result = hashMapOf<String, HashMap<String, LearningRecord>>()
        readableDatabase.query("learning", arrayOf("query", "app_key", "score", "updated_at"), null, null, null, null, null)
            .use { c ->
                while (c.moveToNext()) {
                    result.getOrPut(c.getString(0)) { hashMapOf() }[c.getString(1)] =
                        LearningRecord(c.getDouble(2), c.getLong(3))
                }
            }
        return result
    }

    fun putLearning(query: String, appKey: String, record: LearningRecord) {
        writableDatabase.insertWithOnConflict(
            "learning", null,
            ContentValues().apply {
                put("query", query); put("app_key", appKey)
                put("score", record.score); put("updated_at", record.updatedAtMillis)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun deleteLearning(query: String, appKey: String) {
        writableDatabase.delete("learning", "query = ? AND app_key = ?", arrayOf(query, appKey))
    }

    private inline fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    companion object {
        private const val TAG = "LauncherDatabase"
        private const val NAME = "launcher.db"
        private const val VERSION = 3

        /** The version 2 home_items table's container of the home page and its item types. */
        private const val V2_CONTAINER_HOME = -1L
        private const val V2_TYPE_APP = 0
        private const val V2_TYPE_FOLDER = 1
        private const val V2_TYPE_WIDGET = 2
        private const val V2_TYPE_SHORTCUT = 3
    }
}

/**
 * The key of an app shortcut in version 2's home_items table: Launcher3's ShortcutKey (package and
 * shortcut id) plus the user, as [AppKey]s are.
 */
private data class HomeShortcutKey(val packageName: String, val id: String, val userSerial: Long) {
    companion object {
        private const val PREFIX = "shortcut:"

        fun parse(key: String): HomeShortcutKey? {
            if (!key.startsWith(PREFIX)) return null
            val body = key.removePrefix(PREFIX)
            val serial = body.substringAfterLast('#').toLongOrNull() ?: return null
            val packageAndId = body.substringBeforeLast('#')
            val slash = packageAndId.indexOf('/')
            if (slash <= 0) return null
            return HomeShortcutKey(packageAndId.substring(0, slash), packageAndId.substring(slash + 1), serial)
        }
    }
}
