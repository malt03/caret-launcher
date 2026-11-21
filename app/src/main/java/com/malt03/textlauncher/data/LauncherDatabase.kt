package com.malt03.textlauncher.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.malt03.textlauncher.search.LearningRecord

/** Persistent storage for search strings, learning records and the home layout. */
class LauncherDatabase(context: Context, name: String? = NAME) :
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
        db.execSQL(
            "CREATE TABLE home_items (id INTEGER PRIMARY KEY, type INTEGER NOT NULL, " +
                "container INTEGER NOT NULL, cell_x INTEGER NOT NULL, cell_y INTEGER NOT NULL, " +
                "span_x INTEGER NOT NULL, span_y INTEGER NOT NULL, rank INTEGER NOT NULL, " +
                "app_key TEXT, widget_id INTEGER, provider TEXT, icon BLOB)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // LauncherSettings.Favorites.ICON: the icon of an app shortcut, for when it cannot be
        // looked up.
        if (oldVersion < 2) db.execSQL("ALTER TABLE home_items ADD COLUMN icon BLOB")
    }

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

    // ---- home layout ----

    fun loadHomeRows(): List<HomeRow> {
        val rows = mutableListOf<HomeRow>()
        readableDatabase.query(
            "home_items",
            arrayOf("id", "type", "container", "cell_x", "cell_y", "span_x", "span_y", "rank", "app_key", "widget_id", "provider", "icon"),
            null, null, null, null, "container, rank",
        ).use { c ->
            while (c.moveToNext()) {
                rows += HomeRow(
                    id = c.getLong(0), type = c.getInt(1), container = c.getLong(2),
                    cellX = c.getInt(3), cellY = c.getInt(4), spanX = c.getInt(5), spanY = c.getInt(6),
                    rank = c.getInt(7),
                    appKey = if (c.isNull(8)) null else c.getString(8),
                    widgetId = if (c.isNull(9)) null else c.getInt(9),
                    provider = if (c.isNull(10)) null else c.getString(10),
                    icon = if (c.isNull(11)) null else c.getBlob(11),
                )
            }
        }
        return rows
    }

    fun replaceHomeRows(rows: List<HomeRow>) {
        writableDatabase.transaction {
            delete("home_items", null, null)
            for (row in rows) {
                insert("home_items", null, ContentValues().apply {
                    put("id", row.id); put("type", row.type); put("container", row.container)
                    put("cell_x", row.cellX); put("cell_y", row.cellY)
                    put("span_x", row.spanX); put("span_y", row.spanY); put("rank", row.rank)
                    put("app_key", row.appKey); put("widget_id", row.widgetId); put("provider", row.provider)
                    put("icon", row.icon)
                })
            }
        }
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
        private const val NAME = "launcher.db"
        private const val VERSION = 2
    }
}

/** One row of the home_items table. container is [HomeRow.CONTAINER_HOME] or a folder id. */
data class HomeRow(
    val id: Long,
    val type: Int,
    val container: Long,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
    val rank: Int,
    val appKey: String?,
    val widgetId: Int?,
    val provider: String?,
    /** An app shortcut's icon as GraphicsUtils.createDefaultFlatBitmap makes it, or null. */
    val icon: ByteArray? = null,
) {
    companion object {
        const val CONTAINER_HOME = -1L
        const val TYPE_APP = 0
        const val TYPE_FOLDER = 1
        const val TYPE_WIDGET = 2

        /** An app shortcut; [appKey] holds its HomeShortcutKey key. */
        const val TYPE_SHORTCUT = 3
    }
}
