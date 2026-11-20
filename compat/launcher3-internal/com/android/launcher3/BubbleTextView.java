package com.android.launcher3;

import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.popup.IconViewController;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.SafeCloseable;

/**
 * Stand-in for Launcher3's BubbleTextView (android-17.0.0_r1): a TextView that shows an icon.
 * Members are copied from BubbleTextView.java; changes are marked with "TextLauncher:".
 *
 * TextLauncher: the home screen's icons have no label, so the app's icon view draws its icon
 * itself; the text is only used where Launcher3 inflates a BubbleTextView from a layout, as in the
 * long-press menu's rows (layout/system_shortcut_content.xml). No notification dots.
 */
public class BubbleTextView extends TextView implements DraggableView, IconViewController {
    public static final int DISPLAY_WORKSPACE = 0;
    public static final int DISPLAY_FOLDER = 2;
    public static final int DISPLAY_SEARCH_RESULT_APP_ROW = 9;

    private static final int TEXT_ALPHA_CHANNEL_COUNT = 2;
    private static final int TEXT_ALPHA_INDEX_CONTAINER_VISIBILITY = 0;
    private static final int TEXT_ALPHA_INDEX_TRANSITION = 1;

    private int mTextColor;
    private ColorStateList mTextColorStateList;
    private boolean mHideBadge = false;

    private final AnimatedFloat mTextAlpha = new AnimatedFloat(() -> {
        if (mTextColorStateList != null) {
            setTextColor(mTextColorStateList);
        } else {
            super.setTextColor(getModifiedColor());
        }
    }, 1f);
    private final MultiPropertyFactory<AnimatedFloat> mTextAlphaMultiPropertyFactory =
            new MultiPropertyFactory<>(
                    mTextAlpha,
                    AnimatedFloat.VALUE,
                    TEXT_ALPHA_CHANNEL_COUNT,
                    (a, b) -> a * b,
                    1f);

    public BubbleTextView(Context context) {
        this(context, null, 0);
    }

    public BubbleTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /** TextLauncher: the icon size; the app's icon view gives it. */
    public int getIconSize() {
        return 0;
    }

    /** TextLauncher: the icon; the app's icon view draws and returns it. */
    public FastBitmapDrawable getIcon() {
        return null;
    }

    public int getIconDisplay() {
        return DISPLAY_WORKSPACE;
    }

    public void setHideBadge(boolean hideBadge) {
        mHideBadge = hideBadge;
    }

    public void clearPressedBackground() {
        setPressed(false);
    }

    /**
     * Sets the visibility of the text container for a BubbleTextView
     *
     * @param visible defines whether we should make the container visible or invisible.
     */
    public void setContainerTextVisibility(boolean visible) {
        mTextAlphaMultiPropertyFactory.get(TEXT_ALPHA_INDEX_CONTAINER_VISIBILITY).setValue(
                visible ? 1f : 0f);
    }

    /**
     * Gets the alpha for text inside a BubbleTextView.
     *
     * @return a MultiProperty that has the alpha for the text.
     */
    @androidx.annotation.NonNull
    @Override
    public MultiPropertyFactory<AnimatedFloat>.MultiProperty getFloatingViewTextAlpha() {
        return mTextAlphaMultiPropertyFactory.get(TEXT_ALPHA_INDEX_TRANSITION);
    }

    @Override
    public void setTextColor(int color) {
        mTextColor = color;
        mTextColorStateList = null;
        super.setTextColor(getModifiedColor());
    }

    @Override
    public void setTextColor(ColorStateList colors) {
        // TextLauncher: no app title contrast tiles.
        mTextColor = colors.getDefaultColor();
        mTextColorStateList = colors;

        // mTextAlpha could be null if setTextColor is called before BubbleTextView is initialized.
        if (mTextAlpha != null && Float.compare(mTextAlpha.value, 1f) == 0) {
            super.setTextColor(colors);
        } else {
            super.setTextColor(getModifiedColor());
        }
    }

    private int getModifiedColor() {
        if (mTextAlpha != null && mTextAlpha.value == 0f) {
            // Special case to prevent text shadows in high contrast mode
            return Color.TRANSPARENT;
        }
        return setColorAlphaBound(mTextColor, Math.round(Color.alpha(mTextColor)
                * (mTextAlpha != null ? mTextAlpha.value : 1f)));
    }

    /** TextLauncher: no notification dots. */
    @Override
    public void setForceHideDot(boolean forceHideDot) { }

    /** TextLauncher: the app's icon view hides and shows its icon. */
    @Override
    public void setIconVisible(boolean visible) { }

    @Override
    public int getIconHeight() {
        FastBitmapDrawable icon = getIcon();
        if (icon == null) {
            return 0;
        }

        return icon.getBounds().height();
    }

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    /**
     * Returns the bounds of the icon. TextLauncher: never laid out horizontally; the icon is at
     * the top padding, like a vertical BubbleTextView.
     */
    public void getIconBounds(int iconSize, Rect outBounds) {
        outBounds.set(0, 0, iconSize, iconSize);
        outBounds.offset((getWidth() - iconSize) / 2, getPaddingTop());
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        getIconBounds(getIconSize(), bounds);
    }

    @Override
    public void getSourceVisualDragBounds(Rect bounds) {
        getIconBounds(getIconSize(), bounds);
    }

    @Override
    public SafeCloseable prepareDrawDragView() {
        resetIconScale();
        // TextLauncher: no notification dots to hide.
        return () -> {
        };
    }

    private void resetIconScale() {
        FastBitmapDrawable icon = getIcon();
        if (icon != null) {
            icon.resetScale();
        }
    }
}
