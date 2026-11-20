package com.malt03.textlauncher.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malt03.textlauncher.search.LearningRecord
import com.malt03.textlauncher.search.LearningScore
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

    @Test
    fun homeRowsSurviveReopen() {
        val rows = listOf(
            HomeRow(1, HomeRow.TYPE_APP, HomeRow.CONTAINER_HOME, 0, 0, 1, 1, 0, "app#0", null, null),
            HomeRow(2, HomeRow.TYPE_FOLDER, HomeRow.CONTAINER_HOME, 1, 0, 1, 1, 0, null, null, null),
            HomeRow(3, HomeRow.TYPE_APP, 2, 0, 0, 1, 1, 0, "other#0", null, null),
            HomeRow(4, HomeRow.TYPE_WIDGET, HomeRow.CONTAINER_HOME, 0, 1, 4, 2, 0, null, 42, "pkg/.Widget"),
        )
        db.replaceHomeRows(rows)
        assertEquals(rows.sortedBy { it.id }, reopen().loadHomeRows().sortedBy { it.id })
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
