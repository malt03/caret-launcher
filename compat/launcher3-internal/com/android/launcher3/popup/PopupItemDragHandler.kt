package com.android.launcher3.popup

import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener

/**
 * Stand-in for Launcher3's PopupItemDragHandler. TextLauncher does not drag app shortcuts out of
 * the menu (the home screen holds apps and folders only), so no handler is set.
 */
interface PopupItemDragHandler : OnLongClickListener, OnTouchListener
