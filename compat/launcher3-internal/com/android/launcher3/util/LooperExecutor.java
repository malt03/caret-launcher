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
 * limitations under the License
 */
/*
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.util;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Extension of AbstractListeningExecutorService which executed on a provided looper.
 *
 * <p>Stand-in for Launcher3's LooperExecutor (android-17.0.0_r1). CaretLauncher: an
 * AbstractExecutorService (no Guava), whose thread starts when it is first used, and no priority
 * elevation.
 */
public class LooperExecutor extends AbstractExecutorService {
    private final String mName;
    private final int mPriority;
    private Handler mHandler;

    LooperExecutor(Looper looper) {
        mName = looper.getThread().getName();
        mPriority = Process.THREAD_PRIORITY_DEFAULT;
        mHandler = new Handler(looper);
    }

    LooperExecutor(String name) {
        this(name, Process.THREAD_PRIORITY_DEFAULT);
    }

    LooperExecutor(String name, int priority) {
        mName = name;
        mPriority = priority;
    }

    @Override
    public void execute(Runnable runnable) {
        if (getHandler().getLooper() == Looper.myLooper()) {
            runnable.run();
        } else {
            getHandler().post(runnable);
        }
    }

    /** Same as execute, but never runs the action inline. */
    public void post(Runnable runnable) {
        getHandler().post(runnable);
    }

    public synchronized Handler getHandler() {
        if (mHandler == null) {
            HandlerThread thread = new HandlerThread(mName, mPriority);
            thread.start();
            mHandler = new Handler(thread.getLooper());
        }
        return mHandler;
    }

    /** Returns the thread for this executor */
    public Thread getThread() {
        return getLooper().getThread();
    }

    /** Returns the looper for this executor */
    public Looper getLooper() {
        return getHandler().getLooper();
    }

    /** Not supported and throws an exception when used. */
    @Override
    public void shutdown() {
        throw new UnsupportedOperationException();
    }

    /** Not supported and throws an exception when used. */
    @Override
    public List<Runnable> shutdownNow() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    /** Not supported and throws an exception when used. */
    @Override
    public boolean awaitTermination(long l, TimeUnit timeUnit) {
        throw new UnsupportedOperationException();
    }
}
