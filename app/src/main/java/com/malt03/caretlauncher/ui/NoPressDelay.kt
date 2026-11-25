package com.malt03.caretlauncher.ui

import android.content.Context
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView

/*
 * Containers that show pressed state on touch down right away.
 *
 * Scrolling containers delay a child's pressed state by the tap timeout so that a scroll does not
 * flash it. Launcher3 turns this off for its icon containers (CellLayout and
 * ShortcutAndWidgetContainer return false from shouldDelayChildPressedState), so the icon's click
 * feedback starts immediately. A child's pressed state is delayed if any ancestor asks for it,
 * so every scrolling ancestor of an icon needs this.
 */

class NoPressDelayScrollView(context: Context) : NestedScrollView(context) {
    override fun shouldDelayChildPressedState(): Boolean = false
}

class NoPressDelayRecyclerView(context: Context) : RecyclerView(context) {
    override fun shouldDelayChildPressedState(): Boolean = false
}
