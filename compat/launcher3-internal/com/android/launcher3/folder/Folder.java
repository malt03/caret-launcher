package com.android.launcher3.folder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
 * Background and clip drawing are copied from Folder.java.
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
