/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.launcher3;

import android.content.Context;
import android.view.ViewGroup;

/**
 * Stand-in for Launcher3's PagedView, for keyboard/ViewGroupFocusHelper.java, which skips the
 * focus outline during page transitions and offsets it by the page scroll. TextLauncher has no
 * paged views (the home grid scrolls vertically and folders have one page), so nothing extends
 * this; it only gives the copied code the names it checks for.
 */
public abstract class PagedView<T> extends ViewGroup {
    public PagedView(Context context) {
        super(context);
    }

    /** PagedView.isPageInTransition */
    public abstract boolean isPageInTransition();

    /** PagedView.getScrollForPage */
    public abstract int getScrollForPage(int index);
}
