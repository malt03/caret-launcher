/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.model;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.os.Trace;
import android.util.Log;

import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.data.AppsListData;
import com.android.launcher3.model.data.WorkspaceData;
import com.android.launcher3.util.LooperExecutor;


import java.util.concurrent.Executor;

/**
 * Binds the results of {@link com.android.launcher3.model.LoaderTask} to the Callbacks objects.
 *
 * <p>Stand-in for Launcher3's BaseLauncherBinder (android-17.0.0_r1), made from
 * BaseLauncherBinder.java by deleting Dagger, the extra items, the string cache and the widgets;
 * changes are marked with "CaretLauncher:".
 */
public class BaseLauncherBinder {

    protected static final String TAG = "LauncherBinder";

    protected final LooperExecutor mUiExecutor;

    private final Context mContext;
    private final LauncherModel mModel;
    protected final BgDataModel mBgDataModel;
    private final AllAppsList mBgAllAppsList;

    final Callbacks[] mCallbacksList;

    private int mMyBindingId;

    // CaretLauncher: no Dagger.
    public BaseLauncherBinder(
            Context context,
            LauncherModel model,
            BgDataModel dataModel,
            AllAppsList allAppsList,
            Callbacks[] callbacksList) {
        mUiExecutor = MAIN_EXECUTOR;
        mContext = context;
        mModel = model;
        mBgDataModel = dataModel;
        mBgAllAppsList = allAppsList;
        mCallbacksList = callbacksList;
    }

    /**
     * Binds all loaded data to actual views on the main thread.
     */
    public void bindWorkspace(boolean incrementBindId, boolean isBindSync) {
        Trace.beginSection("BaseLauncherBinder#bindWorkspace");
        try {
            // Save a copy of all the bg-thread collections
            WorkspaceData itemsIdMap;
            // CaretLauncher: no extra items or string cache.

            synchronized (mBgDataModel) {
                itemsIdMap = mBgDataModel.itemsIdMap.copy();
                if (incrementBindId) {
                    mBgDataModel.lastBindId++;
                    mBgDataModel.lastLoadId = mModel.getLastLoadId();
                }
                mMyBindingId = mBgDataModel.lastBindId;
            }

            for (Callbacks cb : mCallbacksList) {
                cb.bindCompleteModelAsync(itemsIdMap, isBindSync);
            }

            // CaretLauncher: no string cache or extra items.
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Binds the all apps results from LoaderTask to the callbacks UX.
     */
    public void bindAllApps() {
        // shallow copy
        AppsListData data = mBgAllAppsList.getImmutableData();
        executeCallbacksTask(c -> c.bindAllApplications(
                data.getApps(), data.getFlags(), data.getPackageUserKeyToUidMap()),
                mUiExecutor);
    }

    // CaretLauncher: no widgets model (bindWidgets).

    protected void executeCallbacksTask(CallbackTask task, Executor executor) {
        executor.execute(() -> {
            if (mMyBindingId != mBgDataModel.lastBindId) {
                Log.d(TAG, "Too many consecutive reloads, skipping obsolete data-bind");
                return;
            }
            for (Callbacks cb : mCallbacksList) {
                task.execute(cb);
            }
        });
    }

    // CaretLauncher: no Dagger (BaseLauncherBinderFactory).
}
