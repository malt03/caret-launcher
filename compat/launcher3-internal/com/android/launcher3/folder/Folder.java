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
 * Modified for TextLauncher: a stand-in that keeps only what TextLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.folder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.FocusFinder;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.graphics.PathWrapper;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ClipPathView;

import java.util.ArrayList;
import java.util.List;

/**
 * Stand-in for Launcher3's Folder (android-17.0.0_r1): the open folder. Holds the members the
 * copied folder animation reads; TextLauncher's open folder extends this and fills them in.
 * Background and clip drawing and the keyboard focus handling are copied from Folder.java.
 */
public abstract class Folder extends FrameLayout implements ClipPathView {
    public final ActivityContext mActivityContext;
    public FolderInfo mInfo = new FolderInfo();
    /** No footer and no name in TextLauncher; kept for the animation, never shown. */
    public View mFooter;
    private final View mFolderName;
    protected final FolderPagedView mContent;
    protected FolderIcon mFolderIcon;
    protected final GradientDrawable mBackground = new GradientDrawable();
    private PathWrapper mClipPath;
    /** Set while the close animation runs: the content is drawn outside the clip then. */
    protected boolean mIsAnimatingClosed = false;

    public Folder(Context context, ActivityContext activityContext) {
        super(context);
        mActivityContext = activityContext;
        mContent = new FolderPagedView(context);
        mFooter = new View(context);
        mFolderName = new View(context);
        setWillNotDraw(false);
        // Folder sets focusable in touch mode, so that it takes the focus back from its name
        // field. TextLauncher's folders have no name, and a touch must not take the focus from the
        // search field (docs/spec.md 1), so it is focusable only for keys.
        setFocusable(true);
    }

    /**
     * Folder.onAttachedToWindow's requestFocus. TextLauncher's folder is attached invisible until
     * it is laid out, which would drop the focus again, so this is called once it is shown. Only
     * when opened with keys: opened by touch, the search field keeps the focus and the keyboard
     * stays up.
     */
    protected void requestFocusOnOpen() {
        // requestFocus() causes the focus onto the folder itself, which doesn't cause visual
        // effect but the next arrow key can start the keyboard focus inside of the folder, not
        // the folder itself.
        if (!isInTouchMode()) {
            requestFocus();
        }
    }

    @Override
    public View focusSearch(int direction) {
        // When the folder is focused, further focus search should be within the folder contents.
        return FocusFinder.getInstance().findNextFocus(this, null, direction);
    }

    public FolderPagedView getContent() {
        return mContent;
    }

    public View getFolderName() {
        return mFolderName;
    }

    public FolderIcon getFolderIcon() {
        return mFolderIcon;
    }

    public int getContentAreaHeight() {
        return mContent.getHeight();
    }

    public int getItemCount() {
        return mContent.getChildCount();
    }

    /** Single page: every child is on page 0. */
    public ArrayList<View> getItemsOnPage(int page) {
        ArrayList<View> items = new ArrayList<>();
        for (int i = 0; i < mContent.getChildCount(); i++) items.add(mContent.getChildAt(i));
        return items;
    }

    public List<View> getIconsInReadingOrder() {
        return getItemsOnPage(0);
    }

    @Override
    public Drawable getBackground() {
        return mBackground;
    }

    @Override
    public void setClipPath(PathWrapper clipPath) {
        mClipPath = clipPath;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        mBackground.setBounds(0, 0, getWidth(), getHeight());
        if (mClipPath != null) {
            // clipPath is non-null while clip reveal animation is playing
            int count = canvas.save();
            canvas.clipPath(mClipPath.getPath());
            mBackground.draw(canvas);

            if (!mIsAnimatingClosed) {
                // If animating open, clip the content to the clip path to keep in bounds.
                super.dispatchDraw(canvas);
            }
            canvas.restoreToCount(count);
            if (mIsAnimatingClosed) {
                // If animating closed, avoid clipping content as preview items protrude past clip.
                super.dispatchDraw(canvas);
            }
        } else {
            mBackground.draw(canvas);
            super.dispatchDraw(canvas);
        }
    }
}
