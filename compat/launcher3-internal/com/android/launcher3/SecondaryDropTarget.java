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
 * Modified for TextLauncher: a stand-in that keeps only what TextLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3;

import static com.android.launcher3.accessibility.LauncherAccessibilityDelegate.INVALID;
import static com.android.launcher3.accessibility.LauncherAccessibilityDelegate.UNINSTALL;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_SYSTEM_MASK;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_SYSTEM_NO;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.util.ApplicationInfoWrapper;

import java.net.URISyntaxException;

/**
 * Drop target which provides a secondary option for an item.
 *    For app targets: shows as uninstall
 *    For configurable widgets: shows as setup
 *    For predicted app icons: don't suggest app
 *
 * <p>Stand-in for Launcher3's SecondaryDropTarget (android-17.0.0_r1), made from
 * SecondaryDropTarget.java by deleting what TextLauncher does not use; changes are marked with
 * "TextLauncher:". The button is only UNINSTALL: there is no RECONFIGURE (a widget's settings are
 * in its long-press menu) and no DISMISS_PREDICTION (no predicted apps).
 */
public class SecondaryDropTarget extends ButtonDropTarget implements OnAlarmListener {

    private static final String TAG = "SecondaryDropTarget";

    private static final long CACHE_EXPIRE_TIMEOUT = 5000;
    private final ArrayMap<UserHandle, Boolean> mUninstallDisabledCache = new ArrayMap<>(1);
    private final Alarm mCacheExpireAlarm;
    private boolean mHadPendingAlarm;

    protected int mCurrentAccessibilityAction = -1;

    public SecondaryDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SecondaryDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mCacheExpireAlarm = new Alarm();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mHadPendingAlarm) {
            mCacheExpireAlarm.setAlarm(CACHE_EXPIRE_TIMEOUT);
            mCacheExpireAlarm.setOnAlarmListener(this);
            mHadPendingAlarm = false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mCacheExpireAlarm.alarmPending()) {
            mCacheExpireAlarm.cancelAlarm();
            mCacheExpireAlarm.setOnAlarmListener(null);
            mHadPendingAlarm = true;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setupUi(UNINSTALL);
    }

    protected void setupUi(int action) {
        if (action == mCurrentAccessibilityAction) {
            return;
        }
        mCurrentAccessibilityAction = action;

        if (action == UNINSTALL) {
            setDrawable(R.drawable.ic_uninstall_no_shadow);
            updateText(R.string.uninstall_drop_target_label);
        }
    }

    @Override
    public void onAlarm(Alarm alarm) {
        mUninstallDisabledCache.clear();
    }

    @Override
    protected void setupItemInfo(ItemInfo info) {
        int buttonType = getButtonType(info, getViewUnderDrag(info));
        if (buttonType != INVALID) {
            setupUi(buttonType);
        }
    }

    @Override
    protected boolean supportsDrop(ItemInfo info) {
        return getButtonType(info, getViewUnderDrag(info)) != INVALID;
    }

    private int getButtonType(ItemInfo info, View view) {
        // TextLauncher: no RECONFIGURE for a widget (its settings are in its long-press menu; a
        // widget has no uninstall target below), and no predicted items.

        Boolean uninstallDisabled = mUninstallDisabledCache.get(info.user);
        if (uninstallDisabled == null) {
            UserManager userManager =
                    (UserManager) getContext().getSystemService(Context.USER_SERVICE);
            Bundle restrictions = userManager.getUserRestrictions(info.user);
            uninstallDisabled = restrictions.getBoolean(UserManager.DISALLOW_APPS_CONTROL, false)
                    || restrictions.getBoolean(UserManager.DISALLOW_UNINSTALL_APPS, false);
            mUninstallDisabledCache.put(info.user, uninstallDisabled);
        }
        // Cancel any pending alarm and set cache expiry after some time
        mCacheExpireAlarm.setAlarm(CACHE_EXPIRE_TIMEOUT);
        mCacheExpireAlarm.setOnAlarmListener(this);
        if (uninstallDisabled) {
            return INVALID;
        }
        // TextLauncher: no private space (Flags.enablePrivateSpace).

        if (info instanceof ItemInfoWithIcon) {
            ItemInfoWithIcon iconInfo = (ItemInfoWithIcon) info;
            if ((iconInfo.runtimeStatusFlags & FLAG_SYSTEM_MASK) != 0
                    && (iconInfo.runtimeStatusFlags & FLAG_SYSTEM_NO) == 0) {
                return INVALID;
            }
        }
        if (getUninstallTarget(getContext(), info) == null) {
            return INVALID;
        }
        return UNINSTALL;
    }

    /**
     * @return the component name that should be uninstalled or null.
     */
    public static ComponentName getUninstallTarget(Context context, ItemInfo item) {
        Intent intent = null;
        UserHandle user = null;
        if (item != null &&
                item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
            intent = item.getIntent();
            user = item.user;
        }
        if (intent != null) {
            LauncherActivityInfo info = context.getSystemService(LauncherApps.class)
                    .resolveActivity(intent, user);
            if (info != null
                    && (info.getApplicationInfo().flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                return info.getComponentName();
            }
        }
        return null;
    }

    @Override
    public void onDrop(DragObject d, DragOptions options) {
        // Defer onComplete
        d.dragSource = new DeferredOnComplete(d.dragSource, getContext());

        super.onDrop(d, options);
    }

    @Override
    public void completeDrop(final DragObject d) {
        ComponentName target = performDropAction(getViewUnderDrag(d.dragInfo), d.dragInfo);
        mDropTargetHandler.onSecondaryTargetCompleteDrop(target, d);
    }

    private View getViewUnderDrag(ItemInfo info) {
        return mDropTargetHandler.getViewUnderDrag(info);
    }

    /**
     * Performs the drop action and returns the target component for the dragObject or null if
     * the action was not performed.
     */
    protected ComponentName performDropAction(View view, ItemInfo info) {
        return performDropAction(view, info, mCurrentAccessibilityAction);
    }

    private ComponentName performDropAction(View view, ItemInfo info, int action) {
        // TextLauncher: no RECONFIGURE.
        return performUninstall(getContext(), getUninstallTarget(getContext(), info), info);
    }

    /**
     * Performs uninstall and returns the target component for the {@link ItemInfo} or null if
     * the uninstall was not performed.
     */
    public static ComponentName performUninstall(Context context, @Nullable ComponentName cn,
            ItemInfo info) {
        if (cn == null) {
            // TextLauncher: no toast (uninstall_system_app_text), as the button is only shown for
            // the items with an uninstall target (getButtonType).
            return null;
        }
        try {
            Intent i = Intent.parseUri(context.getString(R.string.delete_package_intent), 0)
                    .setData(Uri.fromParts("package", cn.getPackageName(), cn.getClassName()))
                    .putExtra(Intent.EXTRA_USER, info.user);
            context.startActivity(i);
            FileLog.d(TAG, "start uninstall activity from drop target " + cn.getPackageName());
            return cn;
        } catch (URISyntaxException e) {
            Log.e(TAG, "Failed to parse intent to start drop target uninstall activity for"
                    + " item=" + info);
            return null;
        }
    }

    /**
     * A wrapper around {@link DragSource} which delays the {@link #onDropCompleted} action until
     * {@link #onLauncherResume}
     */
    protected class DeferredOnComplete implements DragSource {

        private final DragSource mOriginal;
        private final Context mContext;

        protected String mPackageName;
        private DragObject mDragObject;

        public DeferredOnComplete(DragSource original, Context context) {
            mOriginal = original;
            mContext = context;
        }

        @Override
        public void onDropCompleted(View target, DragObject d,
                boolean success) {
            mDragObject = d;
        }

        public void onLauncherResume() {
            // TextLauncher: ApplicationInfoWrapper.getInfo is not limited to the installed apps
            // here (it keeps an app uninstalled with its data), so isInstalled says whether the
            // uninstall went through.
            if (!new ApplicationInfoWrapper(mContext, mPackageName, mDragObject.dragInfo.user)
                    .isInstalled()) {
                mDragObject.dragSource = mOriginal;
                mOriginal.onDropCompleted(SecondaryDropTarget.this, mDragObject, true);
            } else {
                sendFailure();
            }
        }

        public void sendFailure() {
            mDragObject.dragSource = mOriginal;
            mDragObject.cancelled = true;
            mOriginal.onDropCompleted(SecondaryDropTarget.this, mDragObject, false);
        }
    }
}
