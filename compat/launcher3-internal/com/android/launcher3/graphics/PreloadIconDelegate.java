package com.android.launcher3.graphics;

import android.content.Context;

import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.model.data.ItemInfoWithIcon;

/**
 * Stand-in for Launcher3's PreloadIconDelegate. TextLauncher shows installed apps only, so no
 * item is pending (ItemInfoWithIcon.shouldShowPendingIcon is always false).
 */
public class PreloadIconDelegate {
    public static FastBitmapDrawable newPendingIcon(ItemInfoWithIcon info, Context context,
            int creationFlags) {
        return info.newIcon(context, creationFlags);
    }
}
