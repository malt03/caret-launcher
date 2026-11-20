package com.android.launcher3.shortcuts;

import static com.android.launcher3.Flags.blurOnMoreSurfaces;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Themes;

/**
 * Stand-in for Launcher3's DeepShortcutView (android-17.0.0_r1): a row in the long-press menu.
 * The members that system shortcut rows use are copied from DeepShortcutView.java; app shortcuts
 * (applyShortcutInfo, the add button) are left out, since TextLauncher's menu shows only its own
 * options.
 */
public class DeepShortcutView extends FrameLayout {

    private static final Point sTempPoint = new Point();

    private final Drawable mTransparentDrawable = new ColorDrawable(Color.TRANSPARENT);

    private BubbleTextView mBubbleText;
    private View mIconView;

    public DeepShortcutView(Context context) {
        this(context, null, 0);
    }

    public DeepShortcutView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeepShortcutView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBubbleText = findViewById(R.id.bubble_text);
        mBubbleText.setHideBadge(true);
        mIconView = findViewById(R.id.icon);
        tryUpdateTextBackground();
    }

    @Override
    public void setBackground(Drawable background) {
        super.setBackground(background);
        tryUpdateTextBackground();
    }

    @Override
    public void setBackgroundResource(int resid) {
        super.setBackgroundResource(resid);
        tryUpdateTextBackground();
    }

    /**
     * Updates the text background to match the shape of this background (when applicable).
     */
    private void tryUpdateTextBackground() {
        if (mBubbleText == null) {
            return;
        }
        Drawable background = getBackground();
        if (blurOnMoreSurfaces() && background instanceof LayerDrawable l) {
            // When we use blur, we have a LayerDrawable of
            // [BackgroundBlurDrawable, GradientDrawable] as the background.
            background = l.getDrawable(1);
        }
        if (!(background instanceof GradientDrawable surfaceColor)) {
            return;
        }

        int color = Themes.getAttrColor(getContext(), android.R.attr.colorControlHighlight);
        GradientDrawable backgroundMask = new GradientDrawable();
        backgroundMask.setColor(color);
        backgroundMask.setShape(GradientDrawable.RECTANGLE);
        if (surfaceColor.getCornerRadii() != null) {
            backgroundMask.setCornerRadii(surfaceColor.getCornerRadii());
        } else {
            backgroundMask.setCornerRadius(surfaceColor.getCornerRadius());
        }

        RippleDrawable drawable = new RippleDrawable(ColorStateList.valueOf(color),
                mTransparentDrawable, backgroundMask);
        mBubbleText.setBackground(drawable);
    }

    public BubbleTextView getBubbleText() {
        return mBubbleText;
    }

    public void setWillDrawIcon(boolean willDraw) {
        mIconView.setVisibility(willDraw ? View.VISIBLE : View.INVISIBLE);
    }

    public boolean willDrawIcon() {
        return mIconView.getVisibility() == View.VISIBLE;
    }

    /**
     * Returns the position of the center of the icon relative to the container.
     */
    public Point getIconCenter() {
        sTempPoint.y = sTempPoint.x = getMeasuredHeight() / 2;
        if (Utilities.isRtl(getResources())) {
            sTempPoint.x = getMeasuredWidth() - sTempPoint.x;
        }
        return sTempPoint;
    }

    public View getIconView() {
        return mIconView;
    }
}
