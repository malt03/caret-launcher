package com.android.launcher3.preview;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.annotation.UiThread;

import com.android.launcher3.views.ActivityContext;

import java.util.concurrent.CompletableFuture;

/**
 * Stand-in for Launcher3's LauncherPreviewRenderer (android-17.0.0_r1): what
 * PreviewSurfaceRenderer uses of it. The app's subclass renders TextLauncher's home screen, which
 * is not Launcher3's workspace and hotseat; it is made by GridCustomizationsProxy.Delegate in
 * place of the constructor. Like Launcher3's, it is the ActivityContext of the preview's views
 * (BaseContext).
 */
public abstract class LauncherPreviewRenderer extends ContextThemeWrapper
        implements ActivityContext {

    public final CompletableFuture<View> initialRender = new CompletableFuture<>();

    protected LauncherPreviewRenderer(Context context, int themeRes) {
        super(context, themeRes);
    }

    /**
     * Hides the components in the bottom row.
     *
     * @param hide True to hide and false to show.
     */
    @UiThread
    public abstract void hideBottomRow(boolean hide);

    /** BaseContext.onViewDestroyed: the preview's views are gone. */
    public abstract void onViewDestroyed();
}
