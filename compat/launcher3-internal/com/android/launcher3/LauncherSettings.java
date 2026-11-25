/*
 * Copyright (C) 2008 The Android Open Source Project
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
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import androidx.annotation.NonNull;

import com.android.launcher3.icons.cache.CacheLookupFlag;

import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * Stand-in for Launcher3's LauncherSettings. Only the item types, the favorites table (the home
 * screen's database) and the lookup flag that copied code reads.
 */
public class LauncherSettings {
    /** Values from LauncherSettings.Favorites (android-17.0.0_r1). */
    public static final class Favorites implements BaseColumns {
        /** The time of the last update to this row. Type: INTEGER */
        public static final String MODIFIED = "modified";

        /** Descriptive name of the gesture that can be displayed to the user. Type: TEXT */
        public static final String TITLE = "title";

        /**
         * The Intent URL of the gesture, describing what it points to. This value is given to
         * {@link android.content.Intent#parseUri(String, int)} to create an Intent that can be
         * launched. Type: TEXT
         */
        public static final String INTENT = "intent";

        /** The type of the gesture. Type: INTEGER */
        public static final String ITEM_TYPE = "itemType";

        /** The icon of the gesture. Type: BLOB */
        public static final String ICON = "icon";

        public static final String TABLE_NAME = "favorites";

        /** The container holding the favorite. Type: INTEGER */
        public static final String CONTAINER = "container";

        /** The screen holding the favorite (if container is CONTAINER_DESKTOP). Type: INTEGER */
        public static final String SCREEN = "screen";

        /** The X coordinate of the cell holding the favorite. Type: INTEGER */
        public static final String CELLX = "cellX";

        /** The Y coordinate of the cell holding the favorite. Type: INTEGER */
        public static final String CELLY = "cellY";

        /** The X span of the cell holding the favorite. Type: INTEGER */
        public static final String SPANX = "spanX";

        /** The Y span of the cell holding the favorite. Type: INTEGER */
        public static final String SPANY = "spanY";

        /** The profile id of the item in the cell. Type: INTEGER */
        public static final String PROFILE_ID = "profileId";

        /** The appWidgetId of the widget. Type: INTEGER */
        public static final String APPWIDGET_ID = "appWidgetId";

        /** The ComponentName of the widget provider. Type: STRING */
        public static final String APPWIDGET_PROVIDER = "appWidgetProvider";

        /** Boolean indicating that his item was restored and not yet successfully bound. */
        public static final String RESTORED = "restored";

        /**
         * Indicates the position of the item inside an auto-arranged view like folder or hotseat.
         * Type: INTEGER
         */
        public static final String RANK = "rank";

        /** Stores general flag based options for {@link ItemInfo}s. Type: INTEGER */
        public static final String OPTIONS = "options";

        /** Stores the source container that the widget was added from. Type: INTEGER */
        public static final String APPWIDGET_SOURCE = "appWidgetSource";

        public static void addTableToDb(SQLiteDatabase db, long myProfileId, boolean optional) {
            addTableToDb(db, myProfileId, optional, TABLE_NAME);
        }

        public static void addTableToDb(SQLiteDatabase db, long myProfileId, boolean optional,
                String tableName) {
            db.execSQL("CREATE TABLE " + (optional ? " IF NOT EXISTS " : "") + tableName + " ("
                    + getJoinedColumnsToTypes(myProfileId) + ");");
        }

        // LinkedHashMap maintains Order of Insertion
        @NonNull
        public static LinkedHashMap<String, String> getColumnsToTypes(long profileId) {
            final LinkedHashMap<String, String> columnsToTypes = new LinkedHashMap<>();
            columnsToTypes.put(_ID, "INTEGER PRIMARY KEY");
            columnsToTypes.put(TITLE, "TEXT");
            columnsToTypes.put(INTENT, "TEXT");
            columnsToTypes.put(CONTAINER, "INTEGER");
            columnsToTypes.put(SCREEN, "INTEGER");
            columnsToTypes.put(CELLX, "INTEGER");
            columnsToTypes.put(CELLY, "INTEGER");
            columnsToTypes.put(SPANX, "INTEGER");
            columnsToTypes.put(SPANY, "INTEGER");
            columnsToTypes.put(ITEM_TYPE, "INTEGER");
            columnsToTypes.put(APPWIDGET_ID, "INTEGER NOT NULL DEFAULT -1");
            columnsToTypes.put(ICON, "BLOB");
            columnsToTypes.put(APPWIDGET_PROVIDER, "TEXT");
            columnsToTypes.put(MODIFIED, "INTEGER NOT NULL DEFAULT 0");
            columnsToTypes.put(RESTORED, "INTEGER NOT NULL DEFAULT 0");
            columnsToTypes.put(PROFILE_ID, "INTEGER DEFAULT " + profileId);
            columnsToTypes.put(RANK, "INTEGER NOT NULL DEFAULT 0");
            columnsToTypes.put(OPTIONS, "INTEGER NOT NULL DEFAULT 0");
            columnsToTypes.put(APPWIDGET_SOURCE, "INTEGER NOT NULL DEFAULT -1");
            return columnsToTypes;
        }

        private static String getJoinedColumnsToTypes(long profileId) {
            return getColumnsToTypes(profileId)
                    .entrySet()
                    .stream()
                    .map(it -> it.getKey() + " " + it.getValue())
                    .collect(Collectors.joining(", "));
        }

        public static final int CONTAINER_UNKNOWN = -1;
        public static final int CONTAINER_DESKTOP = -100;
        public static final int CONTAINER_HOTSEAT = -101;
        public static final int CONTAINER_HOTSEAT_PREDICTION = -103;
        public static final int CONTAINER_ALL_APPS = -104;
        public static final int CONTAINER_SHORTCUTS = -107;
        public static final int CONTAINER_WIDGETS_PREDICTION = -111;
        public static final int CONTAINER_WIDGETS_TRAY = -105;
        public static final int CONTAINER_BOTTOM_WIDGETS_TRAY = -112;
        public static final int CONTAINER_PIN_WIDGETS = -113;

        public static final int ITEM_TYPE_APPLICATION = 0;
        public static final int ITEM_TYPE_FOLDER = 2;
        public static final int ITEM_TYPE_APPWIDGET = 4;
        public static final int ITEM_TYPE_CUSTOM_APPWIDGET = 5;
        public static final int ITEM_TYPE_DEEP_SHORTCUT = 6;
        public static final int ITEM_TYPE_APP_GROUP = 10;
        public static final int ITEM_TYPE_CUSTOM_VIEW = 14;

        public static final CacheLookupFlag DESKTOP_ICON_FLAG =
                CacheLookupFlag.DEFAULT_LOOKUP_FLAG.withThemeIcon();
    }
}
