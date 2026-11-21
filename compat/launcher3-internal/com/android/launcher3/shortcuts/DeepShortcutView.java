/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Modified for TextLauncher: a stand-in that keeps only what TextLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.shortcuts;

import static com.android.launcher3.AbstractFloatingView.TYPE_FOLDER;

import static com.android.launcher3.Flags.blurOnMoreSurfaces;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_NOT_PINNABLE;

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
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BubbleTextHolder;
import com.android.launcher3.folder.Folder;

/**
 * Stand-in for Launcher3's DeepShortcutView (android-17.0.0_r1): a row in the long-press menu.
 * The members are copied from DeepShortcutView.java; changes are marked with "TextLauncher:".
 * TextLauncher: what DeepShortcutView asks Launcher for (adding to the workspace, the badged
 * item) goes to the activity context's {@link WorkspaceHost}.
 */
public class DeepShortcutView extends FrameLayout implements BubbleTextHolder {

    /** TextLauncher: the Launcher calls DeepShortcutView makes. */
    public interface WorkspaceHost {
        /**
         * Launcher.getModel().updateAndBindWorkspaceItem: the item to place on the home screen for
         * the shortcut, with its badged icon.
         */
        WorkspaceItemInfo makeWorkspaceItem(WorkspaceItemInfo info, ShortcutInfo detail);

        /** LauncherAccessibilityDelegate.addToWorkspace */
        boolean addToWorkspace(ItemInfo item);
    }

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

    @Override
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
        if (ac instanceof WorkspaceHost host && isPinnable(container)) {
            setupAddButton();
            setAddButtonClickListener(host, info, container);
        }
    }

    private boolean isPinnable(PopupContainerWithArrow container) {
        if (!(container.getOriginalIcon() instanceof BubbleTextView bbtv)) {
            return false;
        }
        boolean isPinnable = false;
        if (bbtv.getTag() instanceof ItemInfoWithIcon infoWithIcon) {
            isPinnable = (infoWithIcon.runtimeStatusFlags & FLAG_NOT_PINNABLE) == 0;
        }
        return isPinnable;
    }

    private void setupAddButton() {
        mAddButton.setVisibility(VISIBLE);
        mBubbleText.setPadding(
                mBubbleText.getPaddingStart(),
                mBubbleText.getPaddingTop(),
                (int) getResources().getDimension(R.dimen.deep_shortcut_text_end_padding),
                mBubbleText.getPaddingBottom());
    }

    private void setAddButtonClickListener(WorkspaceHost host, WorkspaceItemInfo info,
            PopupContainerWithArrow container) {
        mAddButton.setOnClickListener(v -> {
            // TextLauncher: no stats logging.
            host.addToWorkspace(getFinalInfo());
            // If we have an open folder, don't animate the popup closing.
            // TextLauncher: the activity context instead of the Launcher.
            Folder folder = AbstractFloatingView.getOpenView(
                    ActivityContext.lookupContext(getContext()), TYPE_FOLDER);
            container.close(folder == null);
            if (folder != null) {
                folder.close(true);
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAddButton != null) {
            mAddButton.setOnClickListener(null);
        }
    }

    /**
     * Returns the shortcut info that is suitable to be added on the homescreen
     */
    public WorkspaceItemInfo getFinalInfo() {
        // TextLauncher: the host makes the badged copy.
        ActivityContext ac = ActivityContext.lookupContext(getContext());
        return ((WorkspaceHost) ac).makeWorkspaceItem(mInfo, mDetail);
    }

    public View getIconView() {
        return mIconView;
    }

    public ShortcutInfo getDetail() {
        return mDetail;
    }
}
