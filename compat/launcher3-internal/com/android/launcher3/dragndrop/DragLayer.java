package com.android.launcher3.dragndrop;

import android.content.Context;

import com.android.launcher3.views.BaseDragLayer;

/**
 * Stand-in for Launcher3's DragLayer. TextLauncher's drag layer is a {@link BaseDragLayer}, which
 * holds the DragLayer members it needs; this class only gives copied code DragLayer's names
 * (DragLayer.LayoutParams in popup/ArrowPopup.java).
 */
public class DragLayer extends BaseDragLayer<Object> {
    public DragLayer(Context context) {
        super(context);
    }
}
