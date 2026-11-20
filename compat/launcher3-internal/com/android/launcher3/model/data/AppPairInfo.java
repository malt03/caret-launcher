package com.android.launcher3.model.data;

import java.util.function.Predicate;

/** Stand-in for Launcher3's AppPairInfo. TextLauncher has no app pairs; never instantiated. */
public class AppPairInfo extends ItemInfo {
    public boolean anyMatch(Predicate<ItemInfo> matcher) {
        return false;
    }
}
