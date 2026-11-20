package com.android.launcher3.apppairs;

import android.content.Context;
import android.view.View;

import com.android.launcher3.BubbleTextView;

/** Stand-in for Launcher3's AppPairIcon. TextLauncher has no app pairs; never instantiated. */
public abstract class AppPairIcon extends View {
    public AppPairIcon(Context context) {
        super(context);
    }

    public abstract BubbleTextView getTitleTextView();

    /** AppPairIcon.getIconDrawableArea, for folder/PreviewItemManager. */
    public abstract IconDrawableArea getIconDrawableArea();

    /** Stand-in for AppPairIconDrawableBackground's view. */
    public interface IconDrawableArea {
        android.graphics.drawable.Drawable getDrawable();
    }
}
