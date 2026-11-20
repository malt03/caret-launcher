package com.android.launcher3.model.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.shortcuts.ShortcutKey;

/**
 * Stand-in for Launcher3's WorkspaceItemInfo (android-17.0.0_r1): an app, or an app shortcut in
 * the long-press menu. The app shortcut members are copied from WorkspaceItemInfo.java; changes
 * are marked with "TextLauncher:".
 */
public class WorkspaceItemInfo extends ItemInfoWithIcon {
    private static final String TAG = "WorkspaceItemInfo";

    /** The intent used to start the application. */
    @NonNull
    public Intent intent = new Intent();

    public WorkspaceItemInfo() {
    }

    public WorkspaceItemInfo(ShortcutInfo shortcutInfo, Context context) {
        user = shortcutInfo.getUserHandle();
        itemType = Favorites.ITEM_TYPE_DEEP_SHORTCUT;
        // TextLauncher: no private space (docs/spec.md), so nothing is marked not pinnable here.
        updateFromDeepShortcutInfo(shortcutInfo, context);
    }

    @Override
    public Intent getIntent() {
        return intent;
    }

    /** TextLauncher: only installed apps are shown, so there are no promise icons. */
    public boolean hasPromiseIconUi() {
        return false;
    }

    public void updateFromDeepShortcutInfo(@NonNull final ShortcutInfo shortcutInfo,
            @NonNull final Context context) {
        // {@link ShortcutInfo#getActivity} can change during an update. Recreate the intent
        intent = ShortcutKey.makeIntent(shortcutInfo);
        title = shortcutInfo.getShortLabel();

        CharSequence label = shortcutInfo.getLongLabel();
        if (TextUtils.isEmpty(label)) {
            label = shortcutInfo.getShortLabel();
        }
        try {
            contentDescription = context.getPackageManager().getUserBadgedLabel(label, user);
        } catch (SecurityException e) {
            contentDescription = null;
            Log.e(TAG, "Failed to get content description", e);
        }
        // TextLauncher: disabled shortcuts and person keys are not tracked.
    }

    /** Returns the WorkspaceItemInfo id associated with the deep shortcut. */
    public String getDeepShortcutId() {
        return itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT
                ? getIntent().getStringExtra(ShortcutKey.EXTRA_SHORTCUT_ID) : null;
    }
}
