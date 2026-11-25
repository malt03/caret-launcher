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
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3;

import static com.android.launcher3.icons.BitmapInfo.FLAG_NO_BADGE;
import static com.android.launcher3.icons.BitmapInfo.FLAG_SKIP_USER_BADGE;
import static com.android.launcher3.icons.BitmapInfo.FLAG_THEMED;
import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;
import static com.android.launcher3.icons.cache.CacheLookupFlag.DEFAULT_LOOKUP_FLAG;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.icu.text.MessageFormat;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.accessibility.BaseAccessibilityDelegate;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.BitmapInfo.DrawableCreationFlags;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.icons.DotRenderer.IconShapeInfo;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.IconCache.ItemInfoUpdateReceiver;
import com.android.launcher3.icons.PlaceHolderDrawableDelegate;
import com.android.launcher3.icons.cache.CacheLookupFlag;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.popup.IconViewController;
import com.android.launcher3.popup.Popup;
import com.android.launcher3.popup.PopupController;
import com.android.launcher3.util.CancellableTask;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;

import java.util.HashMap;
import java.util.Locale;

/**
 * TextView that draws a bubble behind the text. We cannot use a LineBackgroundSpan
 * because we want to make the bubble taller than the text and TextView's clip is
 * too aggressive.
 *
 * <p>Stand-in for Launcher3's BubbleTextView (android-17.0.0_r1), made from BubbleTextView.java
 * by deleting what CaretLauncher does not have (the device profile's sizes, the taskbar, two-line
 * labels, app title contrast tiles, archived, pending and automated icons, minimal popups, custom
 * touch handling and the long-press helper); changes are marked with "CaretLauncher:".
 *
 * <p>CaretLauncher: the home screen's icons (the app's AppIconView) have no label; the app says so
 * with getCellSpecMaxTextLineCount and applyLabel. The search results' icons and the long-press
 * menu's rows show their text.
 */
public class BubbleTextView extends TextView implements ItemInfoUpdateReceiver,
        // CaretLauncher: no floating icon views, Poppable or custom touch delegates.
        DraggableView, Reorderable, IconViewController {

    public static final int DISPLAY_WORKSPACE = 0;
    public static final int DISPLAY_ALL_APPS = 1;
    public static final int DISPLAY_FOLDER = 2;
    public static final int DISPLAY_TASKBAR = 5;
    public static final int DISPLAY_SEARCH_RESULT = 6;
    public static final int DISPLAY_SEARCH_RESULT_SMALL = 7;
    public static final int DISPLAY_SEARCH_RESULT_APP_ROW = 9;

    private float mScaleForReorderBounce = 1f;

    private static final Property<BubbleTextView, Float> DOT_SCALE_PROPERTY
            = new Property<BubbleTextView, Float>(Float.TYPE, "dotScale") {
        @Override
        public Float get(BubbleTextView bubbleTextView) {
            return bubbleTextView.mDotParams.scale;
        }

        @Override
        public void set(BubbleTextView bubbleTextView, Float value) {
            bubbleTextView.mDotParams.scale = value;
            bubbleTextView.invalidate();
        }
    };

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    private FastBitmapDrawable mIcon;
    private boolean mCenterVertically;

    protected int mDisplay;

    private boolean mLayoutHorizontal;
    private final boolean mIsRtl;
    private final int mIconSize;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHideBadge = false;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mSkipUserBadge = false;
    @ViewDebug.ExportedProperty(category = "launcher")
    protected boolean mIsIconVisible = true;
    @ViewDebug.ExportedProperty(category = "launcher")
    private int mTextColor;
    @ViewDebug.ExportedProperty(category = "launcher")
    private ColorStateList mTextColorStateList;
    @ViewDebug.ExportedProperty(category = "launcher")
    private DotInfo mDotInfo;
    private final DotRenderer mDotRenderer;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    protected final DotRenderer.DrawParams mDotParams;
    private Animator mDotScaleAnim;
    private boolean mForceHideDot;

    private static final int TEXT_ALPHA_CHANNEL_COUNT = 2;
    private static final int TEXT_ALPHA_INDEX_CONTAINER_VISIBILITY = 0;
    private static final int TEXT_ALPHA_INDEX_TRANSITION = 1;

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

    private CancellableTask mIconLoadRequest;

    private boolean mHighResUpdateInProgress = false;

    private boolean mIsShowingMinimalPopup;

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
        mIsRtl = (getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL);
        mCenterVertically = a.getBoolean(R.styleable.BubbleTextView_centerVertically, false);

        mDisplay = a.getInteger(R.styleable.BubbleTextView_iconDisplay, DISPLAY_WORKSPACE);
        // CaretLauncher: no device profile for the text size, the drawable padding and the
        // default icon size; the views that draw an icon give its size (iconSizeOverride, or the
        // constructor with the icon size).
        final int defaultIconSize = 0;


        mIconSize = a.getDimensionPixelSize(R.styleable.BubbleTextView_iconSizeOverride,
                defaultIconSize);
        a.recycle();

        mDotParams = new DotRenderer.DrawParams();
        mDotParams.setDotColor(Themes.getAttrColor(context, R.attr.notificationDotColor));

        if (mDisplay == DISPLAY_ALL_APPS) {
            // CaretLauncher: the view's icon size, which Launcher3 takes from the device profile.
            mDotRenderer = new DotRenderer(mIconSize);

            // Do not use normalized info, as we account for normalization in iconBounds
            mDotParams.shapeInfo = IconShapeInfo.DEFAULT;
        } else {
            // CaretLauncher: the view's icon size, which Launcher3 takes from the device profile.
            mDotRenderer = new DotRenderer(mIconSize);
            // CaretLauncher: ThemeManager gives the shape info itself (no IconState).
            mDotParams.shapeInfo = ThemeManager.INSTANCE.get(context).getIconShapeInfo();
        }

        setEllipsize(TruncateAt.END);
        // CaretLauncher: no mActivity field (getActivityAccessibilityDelegate).
        setAccessibilityDelegate(getActivityAccessibilityDelegate(context));

        setContainerTextVisibility(mDisplay != DISPLAY_TASKBAR);
    }

    public void setHideBadge(boolean hideBadge) {
        mHideBadge = hideBadge;
    }

    public void setSkipUserBadge(boolean skipUserBadge) {
        mSkipUserBadge = skipUserBadge;
    }

    /**
     * Resets the view so it can be recycled.
     */
    public void reset() {
        mDotInfo = null;
        cancelDotScaleAnim();
        mDotParams.scale = 0f;
        mForceHideDot = false;
        setBackground(null);
        configureMinimalPopup(false);
        // CaretLauncher: no taskbar line indicators.

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

    private void cancelDotScaleAnim() {
        if (mDotScaleAnim != null) {
            mDotScaleAnim.cancel();
        }
    }

    public void animateDotScale(float... dotScales) {
        cancelDotScaleAnim();
        mDotScaleAnim = ObjectAnimator.ofFloat(this, DOT_SCALE_PROPERTY, dotScales);
        mDotScaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDotScaleAnim = null;
            }
        });
        mDotScaleAnim.start();
    }

    @Override
    public void setAccessibilityDelegate(AccessibilityDelegate delegate) {
        if (delegate instanceof BaseAccessibilityDelegate) {
            super.setAccessibilityDelegate(delegate);
        } else {
            // NO-OP
            // Workaround for b/129745295 where RecyclerView is setting our Accessibility
            // delegate incorrectly. There are no cases when we shouldn't be using the
            // LauncherAccessibilityDelegate for BubbleTextView.
        }
    }

    @UiThread
    public void applyFromWorkspaceItem(WorkspaceItemInfo info) {
        applyIconAndLabel(info);
        setItemInfo(info);

        // NOTE: We do not verify high res immediately for workspace items as doing so would result
        // in eager loading of icons for foldered items which should instead be lazily loaded on
        // first open.

        applyDotState(info, false /* animate */);
        // CaretLauncher: no download states.
    }

    @UiThread
    public void applyFromApplicationInfo(AppInfo info) {
        applyIconAndLabel(info);
        setItemInfo(info);

        // Verify high res immediately
        verifyHighRes();

        applyDotState(info, false /* animate */);
        // CaretLauncher: no download states.
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

        // CaretLauncher: no download states.
    }

    protected void setItemInfo(ItemInfoWithIcon itemInfo) {
        setTag(itemInfo);
    }

    // CaretLauncher: no automated or pending icons, so this is always setStandardIcon (for other
    // icons hasPendingAnimationCompleted is true) without the automated icon's exit animation.
    @VisibleForTesting
    @UiThread
    public void applyIconAndLabel(ItemInfoWithIcon info) {
        FastBitmapDrawable iconDrawable =
                info.newIcon(getContext(), getIconCreationFlagsForInfo(info));
        setIcon(iconDrawable);
        applyLabel(info);
    }

    /**
     * Returns the creation flags to be used when generating icons for this view
     */
    @DrawableCreationFlags
    public int getIconCreationFlagsForInfo(ItemInfoWithIcon info) {
        // Set nonPendingIcon acts as a restart which should refresh the flag state when applicable.
        int flags = shouldUseTheme() ? FLAG_THEMED : 0;
        // Remove badge on icons smaller than 48dp. Except for in 2026 refresh which uses 40dp
        // for small icon.
        if (mHideBadge || mDisplay == DISPLAY_SEARCH_RESULT_SMALL) {
            flags |= FLAG_NO_BADGE;
        }
        if (mSkipUserBadge) {
            flags |= FLAG_SKIP_USER_BADGE;
        }
        return flags;
    }

    protected boolean shouldUseTheme() {
        return mDisplay == DISPLAY_WORKSPACE || mDisplay == DISPLAY_FOLDER
                || mDisplay == DISPLAY_TASKBAR;
    }

    /**
     * @return The number of lines the that the cell spec associated with the BubbleTextView
     * declared it can support.
     */
    // CaretLauncher: protected, so that a view without a label (the app's AppIconView) can say 0.
    protected int getCellSpecMaxTextLineCount() {
        // CaretLauncher: enableScalabilityForDesktopExperience is disabled in the release config.
        return 1;
    }

    @UiThread
    public void applyLabel(ItemInfo info) {
        // CaretLauncher: no archived apps.
        applyLabel(info.title, info.contentDescription,
                false, info.isDisabled());
    }

    private void applyLabel(@Nullable CharSequence label, @Nullable CharSequence contentDescription,
            boolean isTextWithArchivingIcon, boolean isItemDisabled) {
        if (label != null) {
            // CaretLauncher: no two-line labels and no archived apps.
            setText(label);
        }
        if (contentDescription != null) {
            setContentDescription(isItemDisabled
                    ? getContext().getString(R.string.disabled_app_label, contentDescription)
                    : contentDescription);
        }
    }

    /** Returns the icon for this view. */
    public FastBitmapDrawable getIcon() {
        return mIcon;
    }

    /**
     * Returns true if the touch down at the provided position be ignored
     */
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

    public void clearPressedBackground() {
        setPressed(false);
    }

    @Override
    public void onDraw(Canvas canvas) {
        // CaretLauncher: no selection highlight (keyboard focus draws an outline) and no taskbar.
        super.onDraw(canvas);
        drawDotIfNecessary(canvas);
    }

    /**
     * Draws the notification dot in the top right corner of the icon bounds.
     *
     * @param canvas The canvas to draw to.
     */
    protected void drawDotIfNecessary(Canvas canvas) {
        if (!mForceHideDot && (hasDot() || mDotParams.scale > 0)) {
            getIconBounds(mDotParams.iconBounds);
            Utilities.scaleRectAboutCenter(mDotParams.iconBounds, ICON_VISIBLE_AREA_FACTOR);
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            canvas.translate(scrollX, scrollY);
            mDotRenderer.draw(canvas, mDotParams);
            canvas.translate(-scrollX, -scrollY);
        }
    }

    @Override
    public void setForceHideDot(boolean forceHideDot) {
        if (mForceHideDot == forceHideDot) {
            return;
        }
        mForceHideDot = forceHideDot;

        if (forceHideDot) {
            invalidate();
        } else if (hasDot()) {
            animateDotScale(0, 1);
        }
    }

    @VisibleForTesting
    public boolean getForceHideDot() {
        return mForceHideDot;
    }

    public boolean hasDot() {
        return mDotInfo != null;
    }

    /**
     * Get the icon bounds on the view depending on the layout type.
     */
    public void getIconBounds(Rect outBounds) {
        getIconBounds(mIconSize, outBounds);
    }

    /**
     * Get the icon bounds on the view depending on the layout type.
     */
    public void getIconBounds(int iconSize, Rect outBounds) {
        outBounds.set(0, 0, iconSize, iconSize);
        if (mLayoutHorizontal) {
            int top = (getHeight() - iconSize) / 2;
            if (mIsRtl) {
                outBounds.offsetTo(getWidth() - iconSize - getPaddingRight(), top);
            } else {
                outBounds.offsetTo(getPaddingLeft(), top);
            }
        } else {
            outBounds.offset((getWidth() - iconSize) / 2, getPaddingTop());
        }
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
        // CaretLauncher: no app title contrast tiles and no two-line labels.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void setTextColor(int color) {
        mTextColor = color;
        mTextColorStateList = null;
        super.setTextColor(getModifiedColor());
    }

    @Override
    public void setTextColor(ColorStateList colors) {
        // CaretLauncher: no app title contrast tiles.
        mTextColor = colors.getDefaultColor();
        mTextColorStateList = colors;

        // mTextAlpha could be null if setTextColor is called before BubbleTextView is initialized.
        if (mTextAlpha != null && Float.compare(mTextAlpha.value, 1f) == 0) {
            super.setTextColor(colors);
        } else {
            super.setTextColor(getModifiedColor());
        }
    }

    @Override
    public int getIconHeight() {
        if (mIcon == null) {
            return 0;
        }

        return mIcon.getBounds().height();
    }

    private int getModifiedColor() {
        if (mTextAlpha != null && mTextAlpha.value == 0f) {
            // Special case to prevent text shadows in high contrast mode
            return Color.TRANSPARENT;
        }
        return setColorAlphaBound(mTextColor, Math.round(Color.alpha(mTextColor)
                * (mTextAlpha != null ? mTextAlpha.value : 1f)));
    }

    public void applyDotState(ItemInfo itemInfo, boolean animate) {
        if (mIcon != null) {
            boolean wasDotted = mDotInfo != null;
            // CaretLauncher: no mActivity field; the ActivityContext is looked up here.
            mDotInfo = ActivityContext.lookupContext(getContext()).getDotInfoForItem(itemInfo);
            boolean isDotted = mDotInfo != null;
            float newDotScale = isDotted ? 1f : 0;
            if (wasDotted || isDotted) {
                // Animate when a dot is first added or when it is removed.
                if (animate && (wasDotted ^ isDotted) && isShown()) {
                    animateDotScale(newDotScale);
                } else {
                    cancelDotScaleAnim();
                    mDotParams.scale = newDotScale;
                    invalidate();
                }
            }
            if (!TextUtils.isEmpty(itemInfo.contentDescription)) {
                if (itemInfo.isDisabled()) {
                    setContentDescription(getContext().getString(R.string.disabled_app_label,
                            itemInfo.contentDescription));
                // CaretLauncher: no archived apps.
                } else if (hasDot()) {
                    int count = mDotInfo.getNotificationCount();
                    setContentDescription(
                            getAppLabelPluralString(itemInfo.contentDescription.toString(), count));
                } else {
                    setContentDescription(itemInfo.contentDescription);
                }
            }
        }
    }

    /**
     * Sets the icon for this view based on the layout direction.
     */
    protected void setIcon(FastBitmapDrawable icon) {
        if (mIsIconVisible) {
            applyCompoundDrawables(icon);
        }
        mIcon = icon;
        if (mIcon != null) {
            mIcon.setVisible(getWindowVisibility() == VISIBLE && isShown(), false);
            mIcon.setHoverScaleEnabledForDisplay(mDisplay != DISPLAY_TASKBAR);
        }
    }

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

    protected void applyCompoundDrawables(Drawable icon) {
        if (icon == null) {
            // Icon can be null when we use the BubbleTextView for text only.
            return;
        }

        // CaretLauncher: relayout is not held off while the icon is applied.

        icon.setBounds(0, 0, mIconSize, mIconSize);

        updateIcon(icon);

        // If the current icon is a placeholder color, animate its update.
        if (mIcon != null
                && (mIcon.getDelegate() instanceof PlaceHolderDrawableDelegate delegate)
                && mHighResUpdateInProgress) {
            delegate.animateIconUpdate(icon);
        }
    }

    /**
     * Applies the item info if it is same as what the view is pointing to currently.
     */
    @Override
    public void reapplyItemInfo(ItemInfoWithIcon info) {
        if (getTag() == info) {
            mIconLoadRequest = null;
            // CaretLauncher: relayout is not held off while the icon is applied.
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

    public boolean isDisplaySearchResult() {
        return mDisplay == DISPLAY_SEARCH_RESULT
                || mDisplay == DISPLAY_SEARCH_RESULT_SMALL
                || mDisplay == DISPLAY_SEARCH_RESULT_APP_ROW;
    }

    /**
     * Starts a long press action and returns the corresponding pre-drag condition
     */
    public DragOptions.PreDragCondition startLongPressAction(PopupController<?> popupController) {
        Popup popup = popupController.show(this);
        return popup != null ? popup.createPreDragCondition() : null;
    }

    public int getIconDisplay() {
        return mDisplay;
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

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        getIconBounds(mIconSize, bounds);
    }

    public void getSourceVisualDragBounds(Rect bounds) {
        getIconBounds(mIconSize, bounds);
    }

    @Override
    public SafeCloseable prepareDrawDragView() {
        resetIconScale();
        setForceHideDot(true);
        return () -> {
        };
    }

    private void resetIconScale() {
        if (mIcon != null) {
            mIcon.resetScale();
        }
    }

    private void updateIcon(Drawable newIcon) {
        if (mLayoutHorizontal) {
            setCompoundDrawablesRelative(newIcon, null, null, null);
        } else {
            setCompoundDrawables(null, newIcon, null, null);
        }
    }

    private String getAppLabelPluralString(String appName, int notificationCount) {
        MessageFormat icuCountFormat = new MessageFormat(
                getResources().getString(R.string.dotted_app_label),
                Locale.getDefault());
        HashMap<String, Object> args = new HashMap();
        args.put("app_name", appName);
        args.put("count", notificationCount);
        return icuCountFormat.format(args);
    }

    /**
     * Configures the BubbleTextView on long click disabling animations and hiding system shortcuts.
     *
     * @param shouldDisableAnimationAndShortcuts {@code true} to show the minimal popup and not show
     * long press animation and system shortcuts.
     * {@code false} to show long press animation and system shortcuts.
     */
    public void configureMinimalPopup(boolean shouldDisableAnimationAndShortcuts) {
        mIsShowingMinimalPopup = shouldDisableAnimationAndShortcuts;
    }

    public boolean getShowingMinimalPopup() {
        return mIsShowingMinimalPopup;
    }

    public boolean canShowLongPressPopup() {
        return getTag() instanceof ItemInfo && ShortcutUtil.supportsShortcuts((ItemInfo) getTag());
    }

    // CaretLauncher: members CaretLauncher adds.

    /**
     * The accessibility delegate of the view's ActivityContext (mActivity in Launcher3), or none
     * for a view made outside a launcher context.
     */
    private static AccessibilityDelegate getActivityAccessibilityDelegate(Context context) {
        ActivityContext activity = ActivityContext.lookupContextNoThrow(context);
        return activity != null ? activity.getAccessibilityDelegate() : null;
    }

    /**
     * The iconDisplay of the long-press menu's rows (shortcut_popup in the BubbleTextView
     * attributes), which Launcher3 has no constant for.
     */
    public static final int DISPLAY_SHORTCUT_POPUP = 4;

    /**
     * For views created in code, which give their icon size; Launcher3 takes it from the device
     * profile.
     */
    public BubbleTextView(Context context, int iconSize) {
        this(context, iconSize, DISPLAY_WORKSPACE);
    }

    /**
     * Also the display (iconDisplay in Launcher3's layouts). Sets up the view as the constructor
     * with attributes does, with the given icon size and display and no layout attributes.
     */
    public BubbleTextView(Context context, int iconSize, int display) {
        super(context, null, 0);
        mIsRtl = (getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL);
        mDisplay = display;
        mIconSize = iconSize;

        mDotParams = new DotRenderer.DrawParams();
        mDotParams.setDotColor(Themes.getAttrColor(context, R.attr.notificationDotColor));
        mDotRenderer = new DotRenderer(mIconSize);
        if (mDisplay == DISPLAY_ALL_APPS) {
            // Do not use normalized info, as we account for normalization in iconBounds
            mDotParams.shapeInfo = IconShapeInfo.DEFAULT;
        } else {
            mDotParams.shapeInfo = ThemeManager.INSTANCE.get(context).getIconShapeInfo();
        }
        setAccessibilityDelegate(getActivityAccessibilityDelegate(context));
    }
}
