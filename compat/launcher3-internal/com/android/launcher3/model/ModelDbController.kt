/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Modified for TextLauncher: a stand-in that keeps only what TextLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */
package com.android.launcher3.model

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.util.Log
import androidx.annotation.WorkerThread
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.*
import com.android.launcher3.pm.UserCache
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction
import com.android.launcher3.provider.LauncherDbUtils.tableExists
import com.android.launcher3.util.SQLiteTable
import com.android.launcher3.util.WriteProtectedSQLiteTable
import java.util.concurrent.atomic.AtomicInteger

/**
 * Utility class which maintains an instance of Launcher database and provides utility methods
 * around it.
 *
 * Stand-in for Launcher3's ModelDbController (android-17.0.0_r1), made from ModelDbController.kt by
 * deleting Dagger, the restore, the empty database flag, the default layout, the database for each
 * grid and its migration (attemptMigrateDb), and the ghost widget cleanup; changes are marked with
 * "TextLauncher:". The app's database ([openHelper]) holds the favorites table among its other
 * tables and creates and upgrades it; the item ids are DatabaseHelper's (at the end of the class).
 * The grid migration is the app's GridSizeMigration.
 */
class ModelDbController(
    context: Context,
    // TextLauncher: the app's database in place of DatabaseHelper (createDatabaseHelper).
    private val openHelper: SQLiteOpenHelper,
) {

    // TextLauncher: no Dagger.
    private val userCache: UserCache = UserCache.INSTANCE.get(context)

    @Synchronized
    private fun createDbIfNotExists() {
        // TextLauncher: the app's database is opened once; no restore task or empty database flag.
        if (maxItemId.get() < 0) {
            // Table creation sometimes fails silently, which leads to a crash loop.
            // This way, we will try to create a table every time after crash, so the device
            // would eventually be able to recover.
            if (!tableExists(openHelper.readableDatabase, Favorites.TABLE_NAME)) {
                Log.e(TAG, "Tables are missing after onCreate has been called. Trying to recreate")
                // This operation is a no-op if the table already exists.
                Favorites.addTableToDb(
                    openHelper.writableDatabase,
                    getSerialNumberForUser(Process.myUserHandle()),
                    true, /* optional */
                )
            }
            initIds()
        }
    }

    /** Refer [SQLiteDatabase.query] */
    @WorkerThread
    fun query(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor =
        getTable().query(projection, selection, selectionArgs, sortOrder).apply {
            extras = Bundle().apply { putString(EXTRA_DB_NAME, openHelper.databaseName) }
        }

    /** Refer [SQLiteDatabase.insert] */
    @WorkerThread
    fun insert(initialValues: ContentValues?): Int {
        createDbIfNotExists()
        return insertAndCheck(openHelper.writableDatabase, initialValues)
    }

    /** Refer [SQLiteDatabase.delete] */
    @WorkerThread
    fun delete(selection: String?, selectionArgs: Array<String>?): Int =
        getTable().delete(selection, selectionArgs)

    /** Refer [SQLiteDatabase.update] */
    @WorkerThread
    fun update(values: ContentValues, selection: String?, selectionArgs: Array<String>?): Int =
        getTable().update(values, selection, selectionArgs)

    // TextLauncher: no empty database flag (clearEmptyDbFlag) or test ids (updateMaxIdForTest).

    /**
     * Generates an id to be used for new item in the favorites table. TextLauncher: called on the
     * main thread, where the home model is changed; the ids were read when the model was loaded.
     */
    fun generateNewItemId(): Int {
        createDbIfNotExists()
        if (maxItemId.get() < 0) {
            throw RuntimeException("Error: max item id was not initialized")
        }
        return maxItemId.incrementAndGet()
    }

    // TextLauncher: one page (newScreenId).

    /** Creates an empty DB clearing all existing data */
    @WorkerThread
    fun createEmptyDB() {
        createDbIfNotExists()
        // TextLauncher: DatabaseHelper.createEmptyDB, in the app's database whose other tables
        // stay; no empty database flag.
        openHelper.writableDatabase.delete(Favorites.TABLE_NAME, null, null)
        maxItemId.set(initializeMaxItemId(openHelper.writableDatabase))
    }

    // TextLauncher: the app's HomeWidgetHost removes the ghost widgets (removeGhostWidgets).

    /** Returns a new [SQLiteTransaction] */
    @WorkerThread
    fun newTransaction(): SQLiteTransaction {
        createDbIfNotExists()
        return SQLiteTransaction(openHelper.writableDatabase)
    }

    // TextLauncher: the app's GridSizeMigration migrates the grid in the one database
    // (resetLauncherDb, shouldResetDb, attemptMigrateDb).

    /** Returns the underlying model database */
    @Deprecated("Use [getTable] instead")
    val db: SQLiteDatabase
        get() {
            createDbIfNotExists()
            return openHelper.writableDatabase
        }

    /** Returns the underlying [SQLiteTable] for making persistent model changes */
    fun getTable(): SQLiteTable {
        createDbIfNotExists()
        return WriteProtectedSQLiteTable(openHelper.writableDatabase, TABLE_NAME)
    }

    // TextLauncher: no default layout (loadDefaultFavoritesIfNecessary, getDefaultLayoutParser) or
    // empty database flag (emptyDbCreatedKey, getEmptyDbCreatedKey).

    /** Returns the serial number for the provided user */
    fun getSerialNumberForUser(user: UserHandle): Long {
        return userCache.getSerialNumberForUser(user)
    }

    // ---- TextLauncher: DatabaseHelper's item ids ----

    /** DatabaseHelper.mMaxItemId */
    private val maxItemId = AtomicInteger(-1)

    /** DatabaseHelper.initIds */
    private fun initIds() {
        // In the case where neither onCreate nor onUpgrade gets called, we read the maxId from
        // the DB here
        maxItemId.compareAndSet(-1, initializeMaxItemId(openHelper.writableDatabase))
    }

    /** DatabaseHelper.insertAndCheck */
    private fun insertAndCheck(db: SQLiteDatabase, values: ContentValues?): Int {
        if (values == null) {
            throw RuntimeException("Error: attempting to insert null values")
        }
        if (!values.containsKey(Favorites._ID)) {
            throw RuntimeException("Error: attempting to add item without specifying an id")
        }

        updateMaxId(values.getAsInteger(Favorites._ID))
        return db.insert(Favorites.TABLE_NAME, null, values).toInt()
    }

    /** DatabaseHelper.updateMaxId: updates the max ID counter to include the provided id */
    private fun updateMaxId(id: Int) {
        maxItemId.accumulateAndGet(id, Math::max)
    }

    /** DatabaseHelper.initializeMaxItemId with its getMaxId. */
    private fun initializeMaxItemId(db: SQLiteDatabase): Int =
        db.rawQuery("SELECT MAX($_ID) FROM $TABLE_NAME", null).use { c ->
            // get the result
            val id = if (c.moveToNext()) c.getInt(0) else -1
            if (id == -1) 0 else id
        }

    companion object {
        private const val TAG = "ModelDbController"

        const val EXTRA_DB_NAME: String = "db_name"

        // TextLauncher: no layout provider (getLayoutUri).
    }
}
