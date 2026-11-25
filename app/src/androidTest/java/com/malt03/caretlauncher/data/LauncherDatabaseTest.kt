package com.malt03.caretlauncher.data

import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.shortcuts.ShortcutKey
import com.malt03.caretlauncher.search.LearningRecord
import com.malt03.caretlauncher.search.LearningScore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class LauncherDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "launcher-test.db"
    private lateinit var db: LauncherDatabase
    private val direct = Executor { it.run() }

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        db = LauncherDatabase(context, dbName)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun reopen(): LauncherDatabase {
        db.close()
        db = LauncherDatabase(context, dbName)
        return db
    }

    @Test
    fun searchTermsSurviveReopen() {
        db.replaceSearchTerms("app#0", listOf("かめら", "cam"))
        db.replaceSearchTerms("other#0", listOf("x"))
        db.replaceSearchTerms("app#0", listOf("かめら", "photo"))
        val loaded = reopen().loadSearchTerms()
        assertEquals(listOf("かめら", "photo"), loaded["app#0"])
        assertEquals(listOf("x"), loaded["other#0"])
    }

    @Test
    fun learningRecordsSurviveReopen() {
        db.putLearning("か", "app#0", LearningRecord(1.5, 1000))
        db.putLearning("か", "app#0", LearningRecord(2.5, 2000))
        db.putLearning("か", "other#0", LearningRecord(1.0, 3000))
        val loaded = reopen().loadLearning()
        assertEquals(LearningRecord(2.5, 2000), loaded["か"]?.get("app#0"))
        assertEquals(LearningRecord(1.0, 3000), loaded["か"]?.get("other#0"))
    }

    /** Version 2's home_items rows move to the favorites table with Launcher3's values. */
    @Test
    fun homeItemsMigrateToFavorites() {
        db.close()
        context.deleteDatabase(dbName)
        val icon = byteArrayOf(1, 2, 3)
        object : SQLiteOpenHelper(context, dbName, null, 2) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE search_terms (app_key TEXT NOT NULL, term TEXT NOT NULL, PRIMARY KEY (app_key, term))")
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
                fun row(id: Int, type: Int, container: Int, x: Int, y: Int, sx: Int, sy: Int, rank: Int, key: String?, widget: Int?, provider: String?, blob: ByteArray?) =
                    db.insert("home_items", null, ContentValues().apply {
                        put("id", id); put("type", type); put("container", container)
                        put("cell_x", x); put("cell_y", y); put("span_x", sx); put("span_y", sy); put("rank", rank)
                        put("app_key", key); put("widget_id", widget); put("provider", provider); put("icon", blob)
                    })
                row(1, 0, -1, 0, 0, 1, 1, 0, "com.example/.Main#0", null, null, null)
                row(2, 1, -1, 1, 0, 1, 1, 0, null, null, null, null)
                row(3, 0, 2, 0, 0, 1, 1, 0, "com.example/.Other#0", null, null, null)
                row(4, 3, 2, 0, 0, 1, 1, 1, "shortcut:com.example/chat#0", null, null, icon)
                row(5, 2, -1, 0, 1, 4, 2, 0, null, 42, "com.example/.Widget", null)
                row(6, 0, -1, 2, 0, 1, 1, 0, "not a key", null, null, null)
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }.apply { writableDatabase; close() }

        val favorites = HashMap<Int, ContentValues>()
        reopen().readableDatabase.query(Favorites.TABLE_NAME, null, null, null, null, null, null).use { c ->
            while (c.moveToNext()) {
                val values = ContentValues()
                DatabaseUtils.cursorRowToContentValues(c, values)
                favorites[values.getAsInteger(Favorites._ID)] = values
            }
        }
        assertEquals(setOf(1, 2, 3, 4, 5), favorites.keys)

        val app = favorites.getValue(1)
        assertEquals(Favorites.ITEM_TYPE_APPLICATION, app.getAsInteger(Favorites.ITEM_TYPE))
        assertEquals(Favorites.CONTAINER_DESKTOP, app.getAsInteger(Favorites.CONTAINER))
        assertEquals(ComponentName("com.example", "com.example.Main"), Intent.parseUri(app.getAsString(Favorites.INTENT), 0).component)
        assertEquals(0L, app.getAsLong(Favorites.PROFILE_ID))

        val folder = favorites.getValue(2)
        assertEquals(Favorites.ITEM_TYPE_FOLDER, folder.getAsInteger(Favorites.ITEM_TYPE))
        assertEquals(1, folder.getAsInteger(Favorites.CELLX))

        val inFolder = favorites.getValue(3)
        assertEquals(2, inFolder.getAsInteger(Favorites.CONTAINER))

        val shortcut = favorites.getValue(4)
        assertEquals(Favorites.ITEM_TYPE_DEEP_SHORTCUT, shortcut.getAsInteger(Favorites.ITEM_TYPE))
        assertEquals(2, shortcut.getAsInteger(Favorites.CONTAINER))
        assertEquals(1, shortcut.getAsInteger(Favorites.RANK))
        val key = ShortcutKey.fromIntent(Intent.parseUri(shortcut.getAsString(Favorites.INTENT), 0), Process.myUserHandle())
        assertEquals("com.example", key.packageName)
        assertEquals("chat", key.id)
        assertTrue(icon.contentEquals(shortcut.getAsByteArray(Favorites.ICON)))

        val widget = favorites.getValue(5)
        assertEquals(Favorites.ITEM_TYPE_APPWIDGET, widget.getAsInteger(Favorites.ITEM_TYPE))
        assertEquals(42, widget.getAsInteger(Favorites.APPWIDGET_ID))
        // LauncherAppWidgetInfo.onAddToDatabase writes providerName.flattenToString(), the long form.
        assertEquals(
            android.content.ComponentName("com.example", "com.example.Widget"),
            android.content.ComponentName.unflattenFromString(widget.getAsString(Favorites.APPWIDGET_PROVIDER)),
        )
        assertEquals(4, widget.getAsInteger(Favorites.SPANX))
        assertEquals(2, widget.getAsInteger(Favorites.SPANY))

        // The other tables are kept.
        db.replaceSearchTerms("app#0", listOf("x"))
        assertEquals(listOf("x"), db.loadSearchTerms()["app#0"])
    }

    @Test
    fun searchStorePrunesDecayedRecordsOnLoad() {
        val now = LearningScore.DEFAULT_HALF_LIFE_MILLIS * 10
        db.putLearning("か", "old#0", LearningRecord(1.0, 0))
        db.putLearning("か", "new#0", LearningRecord(1.0, now))
        val store = SearchStore(reopen(), direct) { now }
        store.loadBlocking()
        assertEquals(0.0, store.scoreOf("か", "old#0"), 0.0)
        assertEquals(1.0, store.scoreOf("か", "new#0"), 1e-9)
        assertTrue(db.loadLearning()["か"]?.containsKey("old#0") == false)
    }

    @Test
    fun searchStoreRecordsLaunchesWithNormalizedQuery() {
        val store = SearchStore(db, direct) { 1000 }
        store.loadBlocking()
        store.recordLaunch("カ", "app#0")
        store.recordLaunch("か", "app#0")
        assertEquals(2.0, store.scoreOf("か", "app#0"), 1e-9)
        assertEquals(LearningRecord(2.0, 1000), reopen().loadLearning()["か"]?.get("app#0"))
    }

    @Test
    fun searchStoreDropsBlankTerms() {
        val store = SearchStore(db, direct)
        store.loadBlocking()
        store.setTerms("app#0", listOf(" かめら ", "", "かめら", "cam"))
        assertEquals(listOf("かめら", "cam"), store.termsOf("app#0"))
        assertEquals(listOf("かめら", "cam"), reopen().loadSearchTerms()["app#0"])
    }
}
