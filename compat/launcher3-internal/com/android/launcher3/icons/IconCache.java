package com.android.launcher3.icons;

import com.android.launcher3.icons.cache.CacheLookupFlag;
import com.android.launcher3.model.data.ItemInfoWithIcon;

import java.util.concurrent.Executor;

/**
 * Stand-in for Launcher3's IconCache. TextLauncher loads every icon at full resolution with its
 * theme icon (icons/AppIconLoader in the app), so there is never a higher-res icon to fetch.
 */
public class IconCache {
    /** IconCache.ItemInfoUpdateReceiver */
    public interface ItemInfoUpdateReceiver {
        void reapplyItemInfo(ItemInfoWithIcon info);
    }

    public void updateIconInBackground(Executor uiExecutor, ItemInfoUpdateReceiver caller,
            ItemInfoWithIcon info, CacheLookupFlag lookupFlag) { }
}
