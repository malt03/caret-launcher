/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.model.data;

import android.os.UserHandle;

/**
 * Represents a {@link Package} in the widget tray section.
 *
 * <p>Stand-in for Launcher3's PackageItemInfo (android-17.0.0_r1): a package's icon and label for
 * IconCache (the shortcut badge). TextLauncher: no widget categories.
 */
public class PackageItemInfo extends ItemInfoWithIcon {

    /**
     * A package name of the {@link PackageItemInfo} represents.
     */
    public final String packageName;

    /**
     * Represents the widget category shown in the widget picker for which all widgets contained in
     * this package are being grouped together.
     */
    public final int widgetCategory;

    public PackageItemInfo(String packageName, UserHandle user) {
        this(packageName, com.android.launcher3.widget.WidgetSections.NO_CATEGORY, user);
    }

    public PackageItemInfo(String packageName, int widgetCategory, UserHandle user) {
        this.packageName = packageName;
        this.widgetCategory = widgetCategory;
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PackageItemInfo that = (PackageItemInfo) o;
        return java.util.Objects.equals(packageName, that.packageName)
                && java.util.Objects.equals(user, that.user)
                && widgetCategory == that.widgetCategory;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(packageName, user, widgetCategory);
    }
}
