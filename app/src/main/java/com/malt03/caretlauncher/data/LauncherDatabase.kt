package com.malt03.caretlauncher.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Process
import android.os.UserManager
import com.android.launcher3.LauncherSettings.Favorites
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
        private const val NAME = "launcher.db"
        private const val VERSION = 3
    }
}

