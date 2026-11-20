package com.android.launcher3.popup

import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener

/**
 * Stand-in for Launcher3's PopupItemDragHandler: drags an app shortcut out of the long-press
 * menu. The app implements it the way LauncherPopupItemDragHandler does.
 */
interface PopupItemDragHandler : OnLongClickListener, OnTouchListener
