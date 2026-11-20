package com.android.launcher3.graphics;

import android.content.Context;

import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.model.data.ItemInfoWithIcon;

/**
 * Stand-in for Launcher3's AutomatedIconDelegate. Flags.enableAppAutomationIndicator is disabled
 * in the release config, so no icon is drawn as automated.
 */
public abstract class AutomatedIconDelegate {
    public static FastBitmapDrawable newAutomatedIcon(Context context, ItemInfoWithIcon info,
            int creationFlags) {
        return info.newIcon(context, creationFlags);
    }

    public abstract void startExitAnimation(Runnable onEnd);
}
