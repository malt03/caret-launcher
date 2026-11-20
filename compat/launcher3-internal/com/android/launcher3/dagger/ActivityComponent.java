package com.android.launcher3.dagger;

import com.android.launcher3.dragndrop.DragController;

/**
 * Stand-in for Launcher3's Dagger activity component. Only what DragController, DragView and the long-press menu read.
 * There is no system (cross-window) drag. The app may override the icon loader for DragView's
 * spring icons.
 */
public class ActivityComponent {
    private com.android.launcher3.popup.PopupDataProvider mPopupDataProvider;

    /** TextLauncher: the popup data provider is made by the app, which knows the context. */
    public void setPopupDataProvider(com.android.launcher3.popup.PopupDataProvider provider) {
        mPopupDataProvider = provider;
    }

    public com.android.launcher3.popup.PopupDataProvider getPopupDataProvider() {
        return mPopupDataProvider;
    }

    public DragController.SystemDragHandler getSystemDragController() {
        return null;
    }

    /** Returns no full drawable by default, so DragView shows the preview bitmap without springs. */
    public IconLoader getIconLoader() {
        return (info, width, height, shouldThemeIcon) -> null;
    }

    /** Stand-in for the icon loader DragView uses for spring icons. */
    public interface IconLoader {
        FullDrawable getFullDrawable(
                com.android.launcher3.model.data.ItemInfo info, int width, int height,
                boolean shouldThemeIcon);
    }

    /** The adaptive icon and badge returned for spring icons. */
    public static class FullDrawable {
        public android.graphics.drawable.AdaptiveIconDrawable icon;
        public android.graphics.drawable.Drawable badge;
    }
}
