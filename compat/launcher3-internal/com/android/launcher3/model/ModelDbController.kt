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
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.annotation.WorkerThread
import com.android.launcher3.LauncherSettings.Favorites
import java.util.concurrent.atomic.AtomicInteger

/**
 * Utility class which maintains an instance of Launcher database and provides utility methods
 * around it.
 *
 * Stand-in for Launcher3's ModelDbController (android-17.0.0_r1) with the item ids of its
 * DatabaseHelper (initIds, generateNewItemId, insertAndCheck, updateMaxId). TextLauncher: the app's
 * database ([openHelper]) holds the favorites table among its other tables and creates and
 * upgrades it; there is one database for every grid (GridSizeMigration moves the items), no
 * restore, no default layout and no ghost widget cleanup.
 */
class ModelDbController(private val openHelper: SQLiteOpenHelper) {

    /** DatabaseHelper.mMaxItemId */
    private val maxItemId = AtomicInteger(-1)

    /** DatabaseHelper.initIds, from createDbIfNotExists. */
    @Synchronized
    private fun createDbIfNotExists() {
        // In the case where neither onCreate nor onUpgrade gets called, we read the maxId from
        // the DB here
        if (maxItemId.get() < 0) maxItemId.compareAndSet(-1, initializeMaxItemId(openHelper.writableDatabase))
    }

    @WorkerThread
    fun query(
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        createDbIfNotExists()
        return openHelper.writableDatabase.query(
            Favorites.TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder,
        )
    }

    /** DatabaseHelper.insertAndCheck */
    @WorkerThread
    fun insert(initialValues: ContentValues?): Int {
        createDbIfNotExists()
        if (initialValues == null) {
            throw RuntimeException("Error: attempting to insert null values")
        }
        if (!initialValues.containsKey(Favorites._ID)) {
            throw RuntimeException("Error: attempting to add item without specifying an id")
        }

        updateMaxId(initialValues.getAsInteger(Favorites._ID))
        return openHelper.writableDatabase.insert(Favorites.TABLE_NAME, null, initialValues).toInt()
    }

    @WorkerThread
    fun delete(selection: String?, selectionArgs: Array<String>?): Int {
        createDbIfNotExists()
        return openHelper.writableDatabase.delete(Favorites.TABLE_NAME, selection, selectionArgs)
    }

    @WorkerThread
    fun update(values: ContentValues, selection: String?, selectionArgs: Array<String>?): Int {
        createDbIfNotExists()
        return openHelper.writableDatabase.update(Favorites.TABLE_NAME, values, selection, selectionArgs)
    }

    /**
     * DatabaseHelper.generateNewItemId. TextLauncher: called on the main thread, where the home
     * model is changed; the ids were read when the model was loaded.
     */
    fun generateNewItemId(): Int {
        createDbIfNotExists()
        if (maxItemId.get() < 0) {
            throw RuntimeException("Error: max item id was not initialized")
        }
        return maxItemId.incrementAndGet()
    }

    @WorkerThread
    fun newTransaction(): SQLiteTransaction {
        createDbIfNotExists()
        return SQLiteTransaction(openHelper.writableDatabase)
    }

    /** DatabaseHelper.createEmptyDB: the favorites are deleted. */
    @WorkerThread
    fun createEmptyDB() {
        createDbIfNotExists()
        openHelper.writableDatabase.delete(Favorites.TABLE_NAME, null, null)
    }

    // TextLauncher: members TextLauncher adds.

    /** DatabaseHelper.updateMaxId: updates the max ID counter to include the provided id. */
    private fun updateMaxId(id: Int) {
        maxItemId.accumulateAndGet(id, Math::max)
    }

    /** DatabaseHelper.initializeMaxItemId with LauncherDbUtils' getMaxId. */
    private fun initializeMaxItemId(db: SQLiteDatabase): Int =
        db.rawQuery("SELECT MAX(${Favorites._ID}) FROM ${Favorites.TABLE_NAME}", null).use { c ->
            // get the result
            val id = if (c.moveToNext()) c.getInt(0) else -1
            if (id == -1) 0 else id
        }

    /** LauncherDbUtils.SQLiteTransaction */
    class SQLiteTransaction(val db: SQLiteDatabase) : AutoCloseable {
        init {
            db.beginTransaction()
        }

        fun commit() = db.setTransactionSuccessful()

        override fun close() = db.endTransaction()
    }
}
