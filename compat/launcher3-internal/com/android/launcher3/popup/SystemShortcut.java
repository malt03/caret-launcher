/*
 * The upstream file has no license header; Launcher3 is licensed as below.
 *
 * Copyright (C) The Android Open Source Project
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
import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.AbstractFloatingViewHelper;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.views.ActivityContext;

/**
 * Represents a system shortcut for a given app. The shortcut should have a label and icon, and an
 * onClickListener that depends on the item that the shortcut services.
 *
 * Example system shortcuts, defined as inner classes, include Widgets and AppInfo.
 *
 * <p>Stand-in for Launcher3's SystemShortcut (android-17.0.0_r1), made from SystemShortcut.java by
 * deleting what CaretLauncher does not use (every factory and option but App info); changes
 * are marked with "CaretLauncher:".
 *
 * @param <T> extends {@link ActivityContext}
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

    /** @return the resource id of the icon **/
    public int getIconResId() {
        return mIconResId;
    }

    /** @return the resource id of the label **/
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

    public interface Factory<T extends ActivityContext> {

        @Nullable
        SystemShortcut<T> getShortcut(T context, ItemInfo itemInfo, @NonNull View originalView);
    }

    // CaretLauncher: App info is the only factory CaretLauncher uses (no WIDGETS, INSTALL,
    // DONT_SUGGEST_APP, ...); the launcher adds its own options (Launcher.getSupportedShortcuts).

    public static final Factory<ActivityContext> APP_INFO = AppInfo::new;

    public static class AppInfo<T extends ActivityContext> extends SystemShortcut<T> {

        public AppInfo(T target, ItemInfo itemInfo, @NonNull View originalView) {
            super(getDrawableId(), R.string.app_info_drop_target_label, target,
                    itemInfo, originalView);
        }

        /**
         * @return drawable for App Info shortcut icon
         */
        public static int getDrawableId() {
            return R.drawable.info_24px;
        }

        @Override
        public void onClick(View view) {
            Rect sourceBounds = Utilities.getViewBounds(view);
            // CaretLauncher: no launch animation to wait for; the menu is dismissed right away.
            dismissTaskMenuView();
            startDetailsActivityForInfo(view.getContext(), mItemInfo,
                    sourceBounds); // CaretLauncher: see startDetailsActivityForInfo below; no log.
        }
    }

    protected void dismissTaskMenuView() {
        mAbstractFloatingViewHelper.closeOpenViews(mTarget, true,
                AbstractFloatingView.TYPE_ALL & ~AbstractFloatingView.TYPE_REBIND_SAFE);
    }

    // CaretLauncher: members CaretLauncher adds.

    /**
     * PackageManagerHelper.startDetailsActivityForInfo, which CaretLauncher does not have: opens the
     * app's details in Settings. Every item here is an installed app, so there is no install
     * session or widget case, and no launch options.
     */
    private static void startDetailsActivityForInfo(Context context, ItemInfo info,
            Rect sourceBounds) {
        ComponentName componentName = info.getTargetComponent();
        if (componentName != null) {
            try {
                context.getSystemService(LauncherApps.class).startAppDetailsActivity(
                        componentName, info.user, sourceBounds, null);
            } catch (SecurityException | ActivityNotFoundException e) {
                Log.e(TAG, "Unable to launch settings", e);
            }
        }
    }
}
