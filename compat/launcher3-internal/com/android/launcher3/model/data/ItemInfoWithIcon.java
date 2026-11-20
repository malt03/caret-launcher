package com.android.launcher3.model.data;

import static com.android.launcher3.icons.BitmapInfo.FLAG_THEMED;

import android.content.Context;

import com.android.launcher3.Flags;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.IconShape;
import com.android.launcher3.icons.cache.CacheLookupFlag;

/**
 * Stand-in for Launcher3's ItemInfoWithIcon (android-17.0.0_r1). Only what copied code reads; the
 * method bodies are copied from ItemInfoWithIcon.java, changes are marked with "TextLauncher:".
 */
public class ItemInfoWithIcon extends ItemInfo {
    public static final int FLAG_NOT_PINNABLE = 1 << 13;
    public static final int FLAG_AUTOMATED = 1 << 18;

    public int runtimeStatusFlags = 0;

    /** A bitmap version of the icon. */
    public BitmapInfo bitmap = BitmapInfo.LOW_RES_INFO;

    public CacheLookupFlag getMatchingLookupFlag() {
        return bitmap.getMatchingLookupFlag();
    }

    public FastBitmapDrawable newIcon(Context context) {
        return newIcon(context, 0);
    }

    public FastBitmapDrawable newIcon(Context context, int creationFlags) {
        ThemeManager themeManager = ThemeManager.INSTANCE.get(context);
        IconShape iconShape = null;
        // TextLauncher: no file system items.
        if (supportsCustomShapes(creationFlags)) {
            iconShape = themeManager.getIconShapeData();
        }
        if (!themeManager.isIconThemeEnabled()) {
            creationFlags &= ~FLAG_THEMED;
        }
        FastBitmapDrawable drawable = bitmap.newIcon(context, creationFlags, iconShape);
        drawable.setDisabled(isDisabled());
        return drawable;
    }

    /**
     * Returns true if the current BitmapInfo can support cropping to custom icon shapes.
     */
    public boolean supportsCustomShapes(int creationFlags) {
        return Flags.enableLauncherIconShapes()
                && (creationFlags & FLAG_THEMED) != 0
                && bitmap.isFullBleed();
    }

    /** TextLauncher: only installed apps are shown, so nothing is pending or downloading. */
    public boolean shouldShowPendingIcon() {
        return false;
    }
}
