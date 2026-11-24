/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.provider

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/**
 * A set of utility methods for Launcher DB used for DB updates and migration.
 *
 * Stand-in for Launcher3's LauncherDbUtils (android-17.0.0_r1), made from LauncherDbUtils.kt by
 * deleting the workspace screen and table utilities and the migrations of the legacy shortcuts
 * and the backup icons, which TextLauncher's database (the app's LauncherDatabase) does not need.
 */
object LauncherDbUtils {
    const val TAG = "LauncherDbUtils"

    /**
     * Returns a string which can be used as a where clause for DB query to match the given itemId
     */
    @JvmStatic fun itemIdMatch(itemId: Int): String = "_id=$itemId"

    // TextLauncher: no workspace screens (selectionForWorkspaceScreen) or queryIntArray.

    @JvmStatic
    fun tableExists(db: SQLiteDatabase, tableName: String): Boolean =
        db.query(
                /* distinct = */ true,
                /* table = */ "sqlite_master",
                /* columns = */ arrayOf("tbl_name"),
                /* selection = */ "tbl_name = ?",
                /* selectionArgs = */ arrayOf(tableName),
                /* groupBy = */ null,
                /* having = */ null,
                /* orderBy = */ null,
                /* limit = */ null,
                /* cancellationSignal = */ null,
            )
            .use { c ->
                return c.count > 0
            }

    // TextLauncher: no table copies (dropTable, copyTable, shiftWorkspaceByXCells), legacy
    // shortcuts (migrateLegacyShortcuts) or backup icons (updateBackupIcons).

    /** Utility class to simplify managing sqlite transactions */
    class SQLiteTransaction(val db: SQLiteDatabase) : AutoCloseable {
        init {
            db.beginTransaction()
        }

        fun commit() = db.setTransactionSuccessful()

        override fun close() = db.endTransaction()
    }

    /** Utility method to simplify iterating over cursor */
    fun Cursor.asSequence() = generateSequence { if (moveToNext()) this else null }
}
