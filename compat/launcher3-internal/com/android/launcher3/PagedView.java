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
