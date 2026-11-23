/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.graphics;

import android.content.Context;

import com.android.launcher3.icons.DotRenderer.IconShapeInfo;
import com.android.launcher3.icons.IconShape;

/**
 * Stand-in for Launcher3's ThemeManager. Holds the icon state (theme on/off and shape) and the
 * folder shape, which TextLauncher derives from the selected icon shape the same way
 * ThemeManager.parseIconState does (folderRadiusRatio).
 */
public class ThemeManager {
    public static final Instance INSTANCE = new Instance();

    private ShapeDelegate mFolderShape = new ShapeDelegate.Circle();

    public ShapeDelegate getFolderShape() {
        return mFolderShape;
    }

    /** Only read by DragView for spring icons, which TextLauncher does not use. */
    public ShapeDelegate getIconShape() {
        return mFolderShape;
    }

    private boolean mIconThemeEnabled = false;
    private IconShape mIconShapeData;

    public boolean isIconThemeEnabled() {
        return mIconThemeEnabled;
    }

    /** ThemeManager.getIconShapeData().getValue(): the selected icon shape at the icon size. */
    public IconShape getIconShapeData() {
        return mIconShapeData;
    }

    /**
     * IconState.iconShapeInfo: where the notification dot goes on the icon shape.
     * TextLauncher: from the selected shape's path (IconShape), as Launcher3 does with the shape's
     * ShapeDelegate path.
     */
    public IconShapeInfo getIconShapeInfo() {
        return mIconShapeData != null
                ? IconShapeInfo.fromPath(mIconShapeData.path, mIconShapeData.pathSize)
                : IconShapeInfo.DEFAULT;
    }

    /**
     * TextLauncher: the icon state comes from Wallpaper & style (docs/spec.md 7.3) through the
     * app's icon loader, which sets it here.
     */
    public void setIconState(boolean iconThemeEnabled,
            IconShape iconShapeData) {
        mIconThemeEnabled = iconThemeEnabled;
        mIconShapeData = iconShapeData;
    }

    /** TextLauncher: ThemeManager.parseIconState's folder shape for the given radius ratio. */
    public void setFolderRadiusRatio(float folderRadius) {
        mFolderShape = folderRadius == 1f
                ? new ShapeDelegate.Circle()
                : new ShapeDelegate.RoundedSquare(folderRadius);
    }

    /** Stand-in for DaggerSingletonObject. */
    public static class Instance {
        private final ThemeManager mManager = new ThemeManager();

        public ThemeManager get(Context context) {
            return mManager;
        }
    }
}
