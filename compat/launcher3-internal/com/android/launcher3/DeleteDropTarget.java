/*
 * Copyright (C) 2011 The Android Open Source Project
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
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.Preconditions;

/**
 * Stand-in for Launcher3's DeleteDropTarget (android-17.0.0_r1), made from DeleteDropTarget.java
 * by deleting what CaretLauncher does not use (home screen files, undo, logging);
 * changes are marked with "CaretLauncher:".
 */
public class DeleteDropTarget extends ButtonDropTarget {

    public DeleteDropTarget(Context context) {
        this(context, null, 0);
    }

    public DeleteDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeleteDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setDrawable(R.drawable.ic_remove_no_shadow);
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        super.onDragStart(dragObject, options);
        setTextBasedOnDragSource(dragObject.dragInfo);
    }

    /**
     * @return true for items that should have a "Remove" action in accessibility.
     */
    private boolean supportsAccessibilityDrop(ItemInfo info, View view) {
        return UtilitiesKt.isPersistedModelItem(info);
    }

    @Override
    public int getSupportedAccessibilityAction(ItemInfo info, View view) {
        if (supportsAccessibilityDrop(info, view)) {
            return getAccessibilityAction();
        }
        return LauncherAccessibilityDelegate.INVALID;
    }

    @Override
    public int getAccessibilityAction() {
        return LauncherAccessibilityDelegate.REMOVE;
    }

    @Override
    protected void setupItemInfo(ItemInfo info) {}

    @Override
    protected boolean supportsDrop(ItemInfo info) {
        return true;
    }

    /**
     * Set the drop target's text to either "Remove", "Delete permanently", "Move to trash" or
     * "Cancel" depending on the drag item.
     */
    private void setTextBasedOnDragSource(ItemInfo item) {
        if (!TextUtils.isEmpty(mText)) {
            int resId;
            if (canRemove(item)) {
                // CaretLauncher: no home screen files.
                resId = R.string.remove_drop_target_label;
            } else {
                resId = android.R.string.cancel;
            }
            mText = getResources().getString(resId);
            setContentDescription(mText);
            requestLayout();
        }
    }

    private boolean canRemove(ItemInfo item) {
        return item.id != ItemInfo.NO_ID;
    }

    @Override
    public void onDrop(DragObject d, DragOptions options) {
        // CaretLauncher: no undo (DropTargetHandler.prepareToUndoDelete).
        super.onDrop(d, options);
    }

    @Override
    public void completeDrop(DragObject d) {
        ItemInfo item = d.dragInfo;
        if (canRemove(item)) {
            mDropTargetHandler.onDeleteComplete(item, /* view */ null);
        } else if (mText == getResources().getText(R.string.remove_drop_target_label)) {
            Log.wtf("b/379606516", "If the drop target text is 'remove', then"
                    + " users should always be able to delete the item from launcher's db."
                    + " Invalid drag ItemInfo: " + item);
        }
    }

    /**
     * Removes the item from the workspace. If the view is not null, it also removes the view.
     */
    @Override
    public void onAccessibilityDrop(View view, ItemInfo item, int action) {
        Preconditions.assertTrue(action == getAccessibilityAction());
        // Remove the item from launcher and the db, we can ignore the containerInfo in this call
        // because we already remove the drag view from the folder (if the drag originated from
        // a folder) in Folder.beginDrag()
        if (canRemove(item)) {
            // CaretLauncher: no undo (DropTargetHandler.prepareToUndoDelete).
            mDropTargetHandler.onDeleteComplete(item, view);
        }
    }
}
