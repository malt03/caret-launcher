/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.launcher3.popup

import android.view.View

/**
 * Controller interface for popups. It handles actions for the popups such as showing and dismissing
 * popups.
 */
// TextLauncher: T is not bound to a Context and an ActivityContext, as the Launcher stand-in is not
// a Context (Launcher.getPopupControllerForAppIcons gives a PopupController<Launcher>).
interface PopupController<T> {
    /**
     * Shows the popup when called.
     *
     * @return Popup which handles drag related actions due to showing the popup.
     */
    fun show(view: View): Popup?

    /** Dismisses the popup when called. */
    fun dismiss()

    // TextLauncher: no factory (PopupControllerFactory); the home screen makes its controllers
    // (PopupControllerAppIcons, PopupControllerForExtraHomeScreenItems).
}
