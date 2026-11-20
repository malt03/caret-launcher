package com.android.launcher3.popup;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.AbstractFloatingViewHelper;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.views.ActivityContext;

/**
 * Stand-in for Launcher3's SystemShortcut (android-17.0.0_r1): an option in the long-press menu,
 * such as App info. The base class and {@link AppInfo} are copied from SystemShortcut.java;
 * changes are marked with "TextLauncher:". The other options (widgets, install, split screen,
 * private space, ...) do not apply to TextLauncher.
 */
public abstract class SystemShortcut<T extends ActivityContext> extends ItemInfo
        implements View.OnClickListener {
    private static final String TAG = "SystemShortcut";

    private final int mIconResId;
    protected final int mLabelResId;
    protected int mAccessibilityActionId;

    protected final T mTarget;
    protected final ItemInfo mItemInfo;
    protected final View mOriginalView;
    public final boolean mIsCollapsible;

    private final AbstractFloatingViewHelper mAbstractFloatingViewHelper;

    public SystemShortcut(int iconResId, int labelResId, T target, ItemInfo itemInfo,
            View originalView) {
        this(iconResId, labelResId, target, itemInfo, originalView,
                AbstractFloatingViewHelper.INSTANCE, /* isCollapsible */ true);
    }

    public SystemShortcut(int iconResId, int labelResId, T target, ItemInfo itemInfo,
            View originalView, AbstractFloatingViewHelper abstractFloatingViewHelper,
            boolean isCollapsible) {
        mIconResId = iconResId;
        mLabelResId = labelResId;
        mAccessibilityActionId = labelResId;
        mTarget = target;
        mItemInfo = itemInfo;
        mOriginalView = originalView;
        mAbstractFloatingViewHelper = abstractFloatingViewHelper;
        mIsCollapsible = isCollapsible;
    }

    public int getIconResId() {
        return mIconResId;
    }

    public int getLabelResId() {
        return mLabelResId;
    }

    public void setIconAndLabelFor(View iconView, TextView labelView) {
        iconView.setBackgroundResource(mIconResId);
        labelView.setText(mLabelResId);
    }

    public void setIconAndContentDescriptionFor(ImageView view) {
        view.setImageResource(mIconResId);
        view.setContentDescription(view.getContext().getText(mLabelResId));
    }

    public AccessibilityNodeInfo.AccessibilityAction createAccessibilityAction(Context context) {
        return new AccessibilityNodeInfo.AccessibilityAction(
                mAccessibilityActionId, context.getText(mLabelResId));
    }

    public boolean hasHandlerForAction(int action) {
        return mAccessibilityActionId == action;
    }

    protected void dismissTaskMenuView() {
        mAbstractFloatingViewHelper.closeOpenViews(mTarget, true,
                AbstractFloatingView.TYPE_ALL & ~AbstractFloatingView.TYPE_REBIND_SAFE);
    }

    /** Stand-in for SystemShortcut.Widgets, which PopupContainerWithArrow looks for by type. */
    public abstract static class Widgets<T extends ActivityContext> extends SystemShortcut<T> {
        public Widgets(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(0, 0, target, itemInfo, originalView);
        }
    }

    public static class AppInfo<T extends ActivityContext> extends SystemShortcut<T> {

        /**
         * TextLauncher: Launcher3 reads the component from the WorkspaceItemInfo; the app's
         * ItemInfo for an app gives it here.
         */
        public interface Target {
            ComponentName getComponent();

            android.os.UserHandle getUser();
        }

        public AppInfo(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(getDrawableId(), R.string.app_info_drop_target_label, target,
                    itemInfo, originalView);
        }

        /**
         * Returns the drawable id for the App Info shortcut.
         */
        public static int getDrawableId() {
            return R.drawable.info_24px;
        }

        @Override
        public void onClick(View view) {
            Rect sourceBounds = Utilities.getViewBounds(view);
            // TextLauncher: no launch animation to wait for; the menu is dismissed right away.
            dismissTaskMenuView();
            startDetailsActivityForInfo(view.getContext(), mItemInfo, sourceBounds);
        }

        /** PackageManagerHelper.startDetailsActivityForInfo */
        private static void startDetailsActivityForInfo(Context context, ItemInfo info,
                Rect sourceBounds) {
            if (info instanceof Target app) {
                try {
                    context.getSystemService(LauncherApps.class).startAppDetailsActivity(
                            app.getComponent(), app.getUser(), sourceBounds, null);
                } catch (SecurityException | ActivityNotFoundException e) {
                    Log.e(TAG, "Unable to launch settings", e);
                }
            }
        }
    }
}
