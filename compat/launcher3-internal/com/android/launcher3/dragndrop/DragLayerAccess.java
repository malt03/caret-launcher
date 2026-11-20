package com.android.launcher3.dragndrop;

/**
 * TextLauncher: Launcher3's DragLayer lives in this package and calls package-private
 * DragController members. The BaseDragLayer stand-in, which does DragLayer's job, goes through here.
 */
public final class DragLayerAccess {
    private DragLayerAccess() {}

    public static void onDeferredEndDrag(DragController controller, DragView dragView) {
        controller.onDeferredEndDrag(dragView);
    }
}
