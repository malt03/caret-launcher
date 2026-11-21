/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.icons.BitmapInfo.FLAG_NO_BADGE;
import static com.android.launcher3.icons.BitmapInfo.FLAG_THEMED;
import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;
import static com.android.launcher3.icons.cache.CacheLookupFlag.DEFAULT_LOOKUP_FLAG;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.IconCache.ItemInfoUpdateReceiver;
import com.android.launcher3.icons.cache.CacheLookupFlag;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.IconViewController;
import com.android.launcher3.util.CancellableTask;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.SafeCloseable;

/**
 * Stand-in for Launcher3's BubbleTextView (android-17.0.0_r1): a TextView that shows an icon.
 * Members are copied from BubbleTextView.java; changes are marked with "TextLauncher:".
 *
 * TextLauncher: the home screen's icons have no label, so the app's icon view draws its icon
 * itself; the text is only used where Launcher3 inflates a BubbleTextView from a layout, as in the
 * long-press menu's rows (layout/system_shortcut_content.xml). No notification dots.
 */
public class BubbleTextView extends TextView implements ItemInfoUpdateReceiver, DraggableView,
        Reorderable, IconViewController {
    public static final int DISPLAY_WORKSPACE = 0;
    public static final int DISPLAY_ALL_APPS = 1;
    public static final int DISPLAY_FOLDER = 2;
    public static final int DISPLAY_SEARCH_RESULT_APP_ROW = 9;

    private static final int TEXT_ALPHA_CHANNEL_COUNT = 2;
    private static final int TEXT_ALPHA_INDEX_CONTAINER_VISIBILITY = 0;
    private static final int TEXT_ALPHA_INDEX_TRANSITION = 1;

    public static final int DISPLAY_SHORTCUT_POPUP = 4;
    public static final int DISPLAY_TASKBAR = 5;

    private int mTextColor;
    private ColorStateList mTextColorStateList;
    private boolean mHideBadge = false;

    private final boolean mLayoutHorizontal;
    private int mIconSize;
    private final int mDisplay;
    private FastBitmapDrawable mIcon;
    private boolean mIsIconVisible = true;
    private boolean mCenterVertically;

    private boolean mHighResUpdateInProgress = false;
    private CancellableTask mIconLoadRequest;

    private float mScaleForReorderBounce = 1f;

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);

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
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.BubbleTextView, defStyle, 0);
        mLayoutHorizontal = a.getBoolean(R.styleable.BubbleTextView_layoutHorizontal, false);
        mDisplay = a.getInteger(R.styleable.BubbleTextView_iconDisplay, DISPLAY_WORKSPACE);
        // TextLauncher: the default size comes from the device profile in Launcher3; the views
        // that draw an icon here give its size (iconSizeOverride, or getIconSize in the app).
        mIconSize = a.getDimensionPixelSize(R.styleable.BubbleTextView_iconSizeOverride, 0);
        a.recycle();
    }

    /**
     * TextLauncher: views created in code give their icon size, which Launcher3 takes from the
     * device profile.
     */
    public BubbleTextView(Context context, int iconSize) {
        this(context, iconSize, DISPLAY_WORKSPACE);
    }

    /** TextLauncher: also the display (iconDisplay in the layouts of Launcher3). */
    public BubbleTextView(Context context, int iconSize, int display) {
        super(context, null, 0);
        mLayoutHorizontal = false;
        mDisplay = display;
        mIconSize = iconSize;
    }

    /**
     * Resets the view so it can be recycled.
     *
     * <p>TextLauncher: no dots, minimal popups or line indicators.
     */
    public void reset() {
        setBackground(null);

        setTag(null);
        if (mIconLoadRequest != null) {
            mIconLoadRequest.cancel();
            mIconLoadRequest = null;
        }
        // Reset any shifty arrangements in case animation is disrupted.
        setPivotY(0);
        setAlpha(1);
        setScaleY(1);
        setTranslationY(0);
        setMaxLines(1);
        setVisibility(VISIBLE);
    }

    @UiThread
    public void applyFromApplicationInfo(AppInfo info) {
        applyIconAndLabel(info);
        setItemInfo(info);

        // Verify high res immediately
        verifyHighRes();

        // TextLauncher: no notification dots or download states.
    }

    /**
     * Apply label and tag using a generic {@link ItemInfoWithIcon}
     */
    @UiThread
    public void applyFromItemInfoWithIcon(ItemInfoWithIcon info) {
        applyIconAndLabel(info);
        // We don't need to check the info since it's not a WorkspaceItemInfo
        setItemInfo(info);

        // Verify high res immediately
        verifyHighRes();

        // TextLauncher: no download states.
    }

    /**
     * Applies the item info if it is same as what the view is pointing to currently.
     *
     * <p>TextLauncher: relayout is not held off while the icon is applied.
     */
    @Override
    public void reapplyItemInfo(ItemInfoWithIcon info) {
        if (getTag() == info) {
            mIconLoadRequest = null;
            mHighResUpdateInProgress = true;

            // Optimization: Starting in N, pre-uploads the bitmap to RenderThread.
            info.bitmap.icon.prepareToDraw();

            if (info instanceof AppInfo) {
                applyFromApplicationInfo((AppInfo) info);
            } else if (info instanceof WorkspaceItemInfo) {
                applyFromWorkspaceItem((WorkspaceItemInfo) info);
            } else if (info != null) {
                applyFromItemInfoWithIcon(info);
            }

            mHighResUpdateInProgress = false;
        }
    }

    /**
     * Verifies that the current icon is high-res otherwise posts a request to load the icon.
     */
    public void verifyHighRes() {
        CacheLookupFlag expectedFlag = DEFAULT_LOOKUP_FLAG.withThemeIcon(shouldUseTheme());
        if (getTag() instanceof ItemInfoWithIcon info && !mHighResUpdateInProgress
                && info.getMatchingLookupFlag().isVisuallyLessThan(expectedFlag)) {
            if (mIconLoadRequest != null) {
                mIconLoadRequest.cancel();
            }
            mIconLoadRequest = LauncherAppState.getInstance(getContext()).getIconCache()
                    .updateIconInBackground(getContext().getMainExecutor(), BubbleTextView.this,
                            info, expectedFlag);
        }
    }

    public int getIconSize() {
        return mIconSize;
    }

    /** Returns the icon for this view. */
    public FastBitmapDrawable getIcon() {
        return mIcon;
    }

    public int getIconDisplay() {
        return mDisplay;
    }

    public void applyFromWorkspaceItem(WorkspaceItemInfo info) {
        applyIconAndLabel(info);
        setItemInfo(info);

        // NOTE: We do not verify high res immediately for workspace items as doing so would result
        // in eager loading of icons for foldered items which should instead be lazily loaded on
        // first open.
        // TextLauncher: no notification dots or download states.
    }

    protected void setItemInfo(ItemInfoWithIcon itemInfo) {
        setTag(itemInfo);
    }

    /** TextLauncher: no automated or pending icons, so always BubbleTextView.setStandardIcon. */
    public void applyIconAndLabel(ItemInfoWithIcon info) {
        FastBitmapDrawable iconDrawable =
                info.newIcon(getContext(), getIconCreationFlagsForInfo(info));
        setIcon(iconDrawable);
        applyLabel(info);
    }

    /**
     * Returns the creation flags for the icon of the given info.
     */
    public int getIconCreationFlagsForInfo(ItemInfoWithIcon info) {
        // Set nonPendingIcon acts as a restart which should refresh the flag state when applicable.
        int flags = shouldUseTheme() ? FLAG_THEMED : 0;
        // Remove badge on icons smaller than 48dp.
        if (mHideBadge) {
            flags |= FLAG_NO_BADGE;
        }
        return flags;
    }

    protected boolean shouldUseTheme() {
        return mDisplay == DISPLAY_WORKSPACE || mDisplay == DISPLAY_FOLDER
                || mDisplay == DISPLAY_TASKBAR;
    }

    public void applyLabel(ItemInfo info) {
        CharSequence label = info.title;
        if (label != null) {
            setText(label);
        }
        if (info.contentDescription != null) {
            setContentDescription(info.contentDescription);
        }
    }

    protected void setIcon(FastBitmapDrawable icon) {
        if (mIsIconVisible) {
            applyCompoundDrawables(icon);
        }
        mIcon = icon;
        if (mIcon != null) {
            mIcon.setVisible(getWindowVisibility() == VISIBLE && isShown(), false);
        }
    }

    protected void applyCompoundDrawables(Drawable icon) {
        if (icon == null) {
            // Icon can be null when we use the BubbleTextView for text only.
            return;
        }

        icon.setBounds(0, 0, mIconSize, mIconSize);

        updateIcon(icon);
    }

    private void updateIcon(Drawable newIcon) {
        if (mLayoutHorizontal) {
            setCompoundDrawablesRelative(newIcon, null, null, null);
        } else {
            setCompoundDrawables(null, newIcon, null, null);
        }
    }

    protected boolean shouldIgnoreTouchDown(MotionEvent event) {
        if (mDisplay == DISPLAY_TASKBAR) {
            // Allow touching within padding on taskbar, given icon sizes are smaller.
            return false;
        }
        return event.getY() < getPaddingTop()
                || event.getX() < getPaddingLeft()
                || event.getY() > getHeight() - getPaddingBottom()
                || event.getX() > getWidth() - getPaddingRight();
    }

    /**
     * Sets whether to vertically center the content.
     */
    public void setCenterVertically(boolean centerVertically) {
        mCenterVertically = centerVertically;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (mCenterVertically) {
            Paint.FontMetrics fm = getPaint().getFontMetrics();
            int cellHeightPx = mIconSize + getCompoundDrawablePadding() +
                    (int) Math.ceil(fm.bottom - fm.top) * getCellSpecMaxTextLineCount();
            setPadding(getPaddingLeft(), (height - cellHeightPx) / 2, getPaddingRight(),
                    getPaddingBottom());
        }
        // TextLauncher: no app contrast tiles and no two-line labels.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * @return The number of lines the that the cell spec associated with the BubbleTextView
     * declared it can support.
     *
     * TextLauncher: 1 as with enableScalabilityForDesktopExperience disabled; protected so that a
     * view without a label can say 0.
     */
    protected int getCellSpecMaxTextLineCount() {
        return 1;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawDotIfNecessary(canvas);
    }

    /** TextLauncher: no notification dots. */
    protected void drawDotIfNecessary(Canvas canvas) { }

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
    @NonNull
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

    @Override
    public void setIconVisible(boolean visible) {
        mIsIconVisible = visible;
        if (!mIsIconVisible) {
            resetIconScale();
        }
        Drawable icon = getIconOrTransparentColor();
        applyCompoundDrawables(icon);
    }

    private Drawable getIconOrTransparentColor() {
        return mIsIconVisible ? mIcon : new ColorDrawable(Color.TRANSPARENT);
    }

    @Override
    public int getIconHeight() {
        FastBitmapDrawable icon = getIcon();
        if (icon == null) {
            return 0;
        }

        return icon.getBounds().height();
    }

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
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
