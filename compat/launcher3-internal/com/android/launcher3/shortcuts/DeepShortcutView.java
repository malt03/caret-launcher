package com.android.launcher3.shortcuts;

import static com.android.launcher3.Flags.blurOnMoreSurfaces;

import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;

/**
 * Stand-in for Launcher3's DeepShortcutView (android-17.0.0_r1): a row in the long-press menu.
 * The members are copied from DeepShortcutView.java; changes are marked with "TextLauncher:".
 * TextLauncher: app shortcuts are not pinned to the home screen (it holds apps and folders only),
 * so the add button stays hidden and getFinalInfo is left out.
 */
public class DeepShortcutView extends FrameLayout {

    private static final Point sTempPoint = new Point();

    private final Drawable mTransparentDrawable = new ColorDrawable(Color.TRANSPARENT);

    private BubbleTextView mBubbleText;
    private View mIconView;
    private ImageView mAddButton;

    private WorkspaceItemInfo mInfo;
    private ShortcutInfo mDetail;

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
        mAddButton = findViewById(R.id.deep_shortcut_add_button);
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

    public void applyShortcutInfo(WorkspaceItemInfo info, ShortcutInfo detail,
            PopupContainerWithArrow container, ActivityContext ac) {
        mInfo = info;
        mDetail = detail;
        mBubbleText.applyFromWorkspaceItem(info);
        mIconView.setBackground(mBubbleText.getIcon());

        // Use the long label as long as it exists and fits.
        CharSequence longLabel = mDetail.getLongLabel();
        int availableWidth = mBubbleText.getWidth() - mBubbleText.getTotalPaddingLeft()
                - mBubbleText.getTotalPaddingRight();
        boolean usingLongLabel = !TextUtils.isEmpty(longLabel)
                && mBubbleText.getPaint().measureText(longLabel.toString()) <= availableWidth;
        mBubbleText.setText(usingLongLabel ? longLabel : mDetail.getShortLabel());

        // TODO: Add the click handler to this view directly and not the child view.
        mBubbleText.setOnClickListener(container.getItemClickListener());
        mBubbleText.setOnLongClickListener(container.getItemDragHandler());
        mBubbleText.setOnTouchListener(container.getItemDragHandler());
        // TextLauncher: no add button (pinning to the home screen).
    }

    public View getIconView() {
        return mIconView;
    }

    public ShortcutInfo getDetail() {
        return mDetail;
    }
}
