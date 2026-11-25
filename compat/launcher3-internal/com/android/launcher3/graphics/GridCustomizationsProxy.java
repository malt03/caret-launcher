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
package com.android.launcher3.graphics;

import static com.android.launcher3.BuildConfig.IS_DEBUG_DEVICE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import static java.util.Objects.requireNonNullElse;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherModel;
import com.android.launcher3.preview.LauncherPreviewRenderer;
import com.android.launcher3.preview.PreviewLifecycleObserver;
import com.android.launcher3.preview.PreviewSurfaceRenderer;
import com.android.launcher3.shapes.IconShapeModel;
import com.android.launcher3.shapes.ShapesProvider;
import com.android.launcher3.util.RunnableList;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exposes various launcher grid options and allows the caller to change them.
 * APIs:
 *      /shape_options: List of various available shape options, where each has following fields
 *          shape_key: key of the shape option
 *          title: translated title of the shape option
 *          path: path of the shape, assuming drawn on 100x100 view port
 *          is_default: true if this shape option is currently set to the system
 *
 *     /get_preview: Open a file stream for the grid preview
 *
 * CaretLauncher: no grid options (/list_options, /default_grid) and no hidden labels (the home
 * screen's icons never have labels); the icon shape and themed icons are the app's settings
 * ({@link Delegate}), and the preview is the app's home screen
 * ({@link Delegate#newPreviewRenderer}).
 */
public class GridCustomizationsProxy {

    private static final String TAG = "GridCustomizationsProvider";

    // KEY_NAME is the name of the grid used internally while the KEY_GRID_TITLE is the translated
    // string title of the grid.
    private static final String KEY_NAME = "name";
    // is_default means if a certain option is currently set to the system
    private static final String KEY_IS_DEFAULT = "is_default";
    public static final String KEY_SHAPE_KEY = "shape_key";
    private static final String KEY_SHAPE_TITLE = "shape_title";
    private static final String KEY_PATH = "path";

    private static final String KEY_SHAPE_OPTIONS = "/shape_options";
    // default_grid is for setting grid and shape to system settings
    public static final String KEY_DEFAULT_GRID = "/default_grid";
    public static final String SET_SHAPE = "/shape";

    private static final String METHOD_GET_PREVIEW = "get_preview";
    public static final String METHOD_GET_PREVIEW_BITMAP = "get_preview_bitmap";

    /** These methods are used to set monochrome theme */
    private static final String GET_ICON_THEMED = "/get_icon_themed";
    private static final String SET_ICON_THEMED = "/set_icon_themed";
    public static final String ICON_THEMED = "/icon_themed";

    public static final String BOOLEAN_VALUE = "boolean_value";

    private static final String KEY_SURFACE_PACKAGE = "surface_package";
    private static final String KEY_CALLBACK = "callback";
    public static final String KEY_HIDE_BOTTOM_ROW = "hide_bottom_row";
    public static final String KEY_GRID_NAME = "grid_name";

    // Number of rows updated as part of an update call
    protected static final int UPDATE_SETTING_SUCCESS = 1;
    protected static final int UPDATE_SETTING_FAILURE = 0;

    public static final String KEY_UPDATE_METHOD = "update_method";

    // Set of all active previews used to track duplicate memory allocations
    private final Set<PreviewLifecycleObserver> mActivePreviews =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Context mContext;
    // CaretLauncher: the app's settings and home screen preview (no ThemePreference, LauncherPrefs
    // or InvariantDeviceProfile).
    private final Delegate mDelegate;

    // CaretLauncher: no Dagger; made by the app with its delegate. The previews live as long as the
    // process (no DaggerSingletonTracker).
    public GridCustomizationsProxy(Context context, Delegate delegate) {
        mContext = context;
        mDelegate = delegate;
    }

    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        String path = uri.getPath();
        if (IS_DEBUG_DEVICE) {
            Log.d(TAG, "query: path=" + path);
        }
        if (path == null) {
            return null;
        }

        switch (path) {
            case KEY_SHAPE_OPTIONS: {
                MatrixCursor cursor = new MatrixCursor(new String[]{
                        KEY_SHAPE_KEY, KEY_SHAPE_TITLE, KEY_PATH, KEY_IS_DEFAULT});
                // CaretLauncher: the app's setting (PREF_ICON_SHAPE).
                final String currentShape = mDelegate.getIconShape();
                IconShapeModel[] availableShapes = ShapesProvider.INSTANCE.getIconShapes();

                if (availableShapes.length == 0) {
                    // This is unexpected as we should always provide at least 1 default shape.
                    Log.e(TAG, "query: No icon shape options are available"
                            + ", returning null.");
                    return null;
                } else {
                    Log.d(TAG, "query: Found " + availableShapes.length
                            + " available shape options");
                }

                // Assign first available shape as default if current shape doesn't exist.
                boolean doesCurrentShapeExist = Arrays.stream(availableShapes)
                        .anyMatch(shape -> shape.getKey().equals(currentShape));
                String selectedShape = !TextUtils.isEmpty(currentShape) && doesCurrentShapeExist
                        ? currentShape
                        : availableShapes[0].getKey();

                for (IconShapeModel shape : availableShapes) {
                    cursor.newRow()
                            .add(KEY_SHAPE_KEY, shape.getKey())
                            .add(KEY_SHAPE_TITLE, mContext.getString(shape.getTitleId()))
                            .add(KEY_PATH, shape.getPathString())
                            .add(KEY_IS_DEFAULT, shape.getKey().equals(selectedShape));
                }
                return cursor;
            }
            // CaretLauncher: no grid options (KEY_LIST_OPTIONS).
            case GET_ICON_THEMED:
            case ICON_THEMED: {
                MatrixCursor cursor = new MatrixCursor(new String[]{BOOLEAN_VALUE});
                // CaretLauncher: the app's setting (ThemePreference's MONO_THEME_VALUE).
                boolean monoThemeEnabled = mDelegate.isIconThemed();
                cursor.newRow().add(BOOLEAN_VALUE, monoThemeEnabled ? 1 : 0);
                Log.d(TAG, "query: path=" + path + ", isMonoThemeEnabled=" + monoThemeEnabled);
                return cursor;
            }
            // CaretLauncher: the home screen's icons have no labels to hide
            // (WORKSPACE_ITEMS_LABEL_HIDDEN).
            default: {
                Log.d(TAG, "query: path=" + path + " not found, returning null.");
                return null;
            }
        }
    }

    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs,
            Bundle extras) {
        String path = uri.getPath();
        if (path == null) {
            return 0;
        }

        int result = handleUpdate(path, values, extras);
        if (result != 0) {
            mContext.getContentResolver().notifyChange(uri, null);
        }
        return result;
    }

    public int handleUpdate(@NonNull String path, ContentValues values, @Nullable Bundle extras) {
        switch (path) {
            // CaretLauncher: no grid options (KEY_DEFAULT_GRID).
            case SET_SHAPE:
                // CaretLauncher: the app's setting (PREF_ICON_SHAPE).
                mDelegate.setIconShape(
                        requireNonNullElse(values.getAsString(KEY_SHAPE_KEY), ""));
                return UPDATE_SETTING_SUCCESS;
            case ICON_THEMED:
            case SET_ICON_THEMED: {
                // CaretLauncher: the app's setting (ThemePreference's MONO_THEME_VALUE).
                mDelegate.setIconThemed(values.getAsBoolean(BOOLEAN_VALUE));
                return UPDATE_SETTING_SUCCESS;
            }
            // CaretLauncher: no labels to hide (SET_WORKSPACE_ITEMS_LABEL_HIDDEN).
            default:
                return UPDATE_SETTING_FAILURE;
        }
    }

    public Bundle call(@NonNull String method, String arg, Bundle extras) {
        return switch (method) {
            case METHOD_GET_PREVIEW -> getPreview(extras);
            // CaretLauncher: no bitmap preview (METHOD_GET_PREVIEW_BITMAP), which takes the
            // surface's snapshot with ApiWrapper.captureSnapshot.
            default -> null;
        };
    }

    private synchronized Bundle getPreview(Bundle request) {
        RunnableList lifeCycleTracker = new RunnableList();
        try {
            PreviewSurfaceRenderer renderer = new PreviewSurfaceRenderer(
                    mContext, lifeCycleTracker, request, Binder.getCallingPid(),
                    false /* skip animations */, this /* CaretLauncher */);
            PreviewLifecycleObserver observer =
                    new PreviewLifecycleObserver(lifeCycleTracker, renderer);

            // Destroy previous renderers to avoid any duplicate memory
            mActivePreviews.stream().filter(observer::isSameRenderer).forEach(o ->
                    MAIN_EXECUTOR.execute(o.lifeCycleTracker::executeAllAndDestroy));

            renderer.loadAsync();
            lifeCycleTracker.add(() -> renderer.getHostToken().unlinkToDeath(observer, 0));
            renderer.getHostToken().linkToDeath(observer, 0);

            Bundle result = new Bundle();
            result.putParcelable(KEY_SURFACE_PACKAGE, renderer.getHost().getSurfacePackage());

            mActivePreviews.add(observer);
            lifeCycleTracker.add(() -> mActivePreviews.remove(observer));

            // Wrap the callback in a weak reference. This ensures that the callback is not kept
            // alive due to the Messenger's IBinder
            Messenger messenger = new Messenger(new Handler(
                    UI_HELPER_EXECUTOR.getLooper(),
                    new WeakCallbackWrapper(observer)));

            Message msg = Message.obtain();
            msg.replyTo = messenger;
            result.putParcelable(KEY_CALLBACK, msg);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Unable to generate preview", e);
            MAIN_EXECUTOR.execute(lifeCycleTracker::executeAllAndDestroy);
            return null;
        }
    }

    /**
     * A WeakReference wrapper around Handler.Callback to avoid passing hard-reference over IPC
     * when using a Messenger
     */
    private static class WeakCallbackWrapper implements Handler.Callback {

        private final WeakReference<Handler.Callback> mActual;
        private final Message mCleanupMessage;

        WeakCallbackWrapper(Handler.Callback actual) {
            mActual = new WeakReference<>(actual);
            mCleanupMessage = new Message();
        }

        @Override
        public boolean handleMessage(Message message) {
            Handler.Callback actual = mActual.get();
            return actual != null && actual.handleMessage(message);
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            Handler.Callback actual = mActual.get();
            if (actual != null) {
                actual.handleMessage(mCleanupMessage);
            }
        }
    }

    // CaretLauncher: members CaretLauncher adds.

    /** CaretLauncher: the app's settings and preview, which the preview's renderer is made by. */
    public Delegate getDelegate() {
        return mDelegate;
    }

    /**
     * CaretLauncher: what the app keeps and shows in place of Launcher3's preferences and preview
     * context.
     */
    public interface Delegate {
        /** The icon shape's key (ThemeManager.PREF_ICON_SHAPE). */
        String getIconShape();

        /** Sets the icon shape (ThemeManager.PREF_ICON_SHAPE); an unknown key is ignored. */
        void setIconShape(String key);

        /** Whether the icons are themed (ThemePreference's MONO_THEME_VALUE). */
        boolean isIconThemed();

        /** Themes the icons or not (ThemePreference). */
        void setIconThemed(boolean themed);

        /**
         * The renderer of the home screen's preview (LauncherPreviewRenderer's constructor), made
         * on the main thread in the preview's context.
         */
        LauncherPreviewRenderer newPreviewRenderer(Context context, int workspaceScreenId,
                LauncherModel model, int themeRes);
    }
}
