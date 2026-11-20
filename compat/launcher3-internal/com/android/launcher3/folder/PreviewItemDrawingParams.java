package com.android.launcher3.folder;

/**
 * Stand-in for Launcher3's PreviewItemDrawingParams, used by the copied
 * ClippedFolderIconLayoutRule. Keeps the position and scale; the animation, drawable and item
 * fields are left out. TextLauncher adds public getters.
 */
public class PreviewItemDrawingParams {
    float index;
    float transX;
    float transY;
    float scale;

    PreviewItemDrawingParams(float transX, float transY, float scale) {
        this.transX = transX;
        this.transY = transY;
        this.scale = scale;
    }

    public void update(float transX, float transY, float scale) {
        this.transX = transX;
        this.transY = transY;
        this.scale = scale;
    }

    public PreviewItemDrawingParams() {
        this(0, 0, 1);
    }

    public float getTransX() {
        return transX;
    }

    public float getTransY() {
        return transY;
    }

    public float getScale() {
        return scale;
    }
}
