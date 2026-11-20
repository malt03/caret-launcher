package com.android.launcher3;

import android.content.Context;
import android.view.View;

import com.android.launcher3.util.MultiPropertyFactory;

/**
 * Stand-in for Launcher3's BubbleTextView: an app icon view. TextLauncher's icons have no label,
 * so the text alpha property is a no-op.
 */
public abstract class BubbleTextView extends View
        implements com.android.launcher3.dragndrop.DraggableView {
    public static final int DISPLAY_WORKSPACE = 0;
    public static final int DISPLAY_FOLDER = 2;
    public static final int DISPLAY_SEARCH_RESULT_APP_ROW = 9;
    private final MultiPropertyFactory<View> mTextAlpha =
            new MultiPropertyFactory<>(this, new android.util.FloatProperty<View>("textAlpha") {
                @Override
                public void setValue(View view, float value) { }

                @Override
                public Float get(View view) {
                    return 1f;
                }
            }, 1, (a, b) -> a * b);

    public BubbleTextView(Context context) {
        super(context);
    }

    public abstract int getIconSize();

    public abstract com.android.launcher3.icons.FastBitmapDrawable getIcon();

    public int getIconDisplay() {
        return DISPLAY_WORKSPACE;
    }

    public void clearPressedBackground() {
        setPressed(false);
    }

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    /**
     * Returns the bounds of the icon. TextLauncher: never laid out horizontally; the icon is at
     * the top padding, like a vertical BubbleTextView.
     */
    public void getIconBounds(int iconSize, android.graphics.Rect outBounds) {
        outBounds.set(0, 0, iconSize, iconSize);
        outBounds.offset((getWidth() - iconSize) / 2, getPaddingTop());
    }

    @Override
    public void getWorkspaceVisualDragBounds(android.graphics.Rect bounds) {
        getIconBounds(getIconSize(), bounds);
    }

    @Override
    public void getSourceVisualDragBounds(android.graphics.Rect bounds) {
        getIconBounds(getIconSize(), bounds);
    }

    @Override
    public com.android.launcher3.util.SafeCloseable prepareDrawDragView() {
        resetIconScale();
        // TextLauncher: no notification dots to hide.
        return () -> {
        };
    }

    private void resetIconScale() {
        com.android.launcher3.icons.FastBitmapDrawable icon = getIcon();
        if (icon != null) {
            icon.resetScale();
        }
    }

    public MultiPropertyFactory<View>.MultiProperty getFloatingViewTextAlpha() {
        return mTextAlpha.get(0);
    }
}
