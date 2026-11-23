/*
 * Copyright (C) 2009 The Android Open Source Project
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

/**
 * Represents a widget (either instantiated or about to be) in the Launcher.
 *
 * <p>Stand-in for Launcher3's LauncherAppWidgetInfo (android-17.0.0_r1). TextLauncher: the home
 * screen's widget item (the app's WidgetItemInfo) extends it; nothing of it is ported yet.
 */
public class LauncherAppWidgetInfo extends ItemInfo {
}
