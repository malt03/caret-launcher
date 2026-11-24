/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.anim.PropertyListBuilder;
import com.android.launcher3.celllayout.CellPosMapper.CellPos;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.IModelWriter;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.PopupContainer;
import com.android.launcher3.dragndrop.LauncherDragController;
import com.android.launcher3.popup.AppWidgetSystemShortcuts;
import com.android.launcher3.popup.FolderSystemShortcuts;
import com.android.launcher3.popup.PopupController;
import com.android.launcher3.popup.PopupDataRepository;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.popup.WorkspaceLongPressOptions;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.statemanager.StatefulContainer;
import com.android.launcher3.states.RotationHelper;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.ContextTracker;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.ViewEx;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ScrimView;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Stand-in for Launcher3's Launcher (android-17.0.0_r1). As in Launcher3, the launcher is the
 * ActivityContext of the home screen, so copied code can use {@link #getLauncher(Context)} and
 * {@code instanceof Launcher}. TextLauncher's home screen extends this; the launcher is not an
 * Activity here, and only the members copied code uses are declared.
 */
public abstract class Launcher implements StatefulContainer<LauncherState> {
    private static final String TAG = "Launcher";

    @VisibleForTesting public static final int NEW_APPS_ANIMATION_DELAY = 500;

    private static final float BOUNCE_ANIMATION_TENSION = 1.3f;

    /**
     * TextLauncher: a ContextTracker rather than ContextTracker.ActivityTracker, since the launcher
     * is not a BaseActivity; the home is started as {@link #isStarted()} says. The home screen
     * calls handleCreate, handleNewIntent and onContextDestroyed where Launcher3's Launcher does.
     */
    public static final ContextTracker<Launcher> ACTIVITY_TRACKER = new LauncherTracker();

    /** TextLauncher: ContextTracker.ActivityTracker for a launcher that is not an activity. */
    private static final class LauncherTracker extends ContextTracker<Launcher> {
        @Override
        public boolean isHomeStarted(Launcher launcher) {
            return launcher.isStarted();
        }
    }

    private final DragLayer mDragLayer;
    // TextLauncher: the workspace is the home screen's (getWorkspace), made once this launcher is.
    private final Hotseat mHotseat;
    private final ScrimView mScrimView;
    private final StatsLogManager mStatsLogManager = new StatsLogManager();

    // TextLauncher: made here, as the launcher is not an Activity (Launcher.onCreate).
    private final StateManager<LauncherState, Launcher> mStateManager =
            new StateManager<>(this, NORMAL);

    public Launcher(DragLayer dragLayer, Hotseat hotseat, ScrimView scrimView) {
        mDragLayer = dragLayer;
        mHotseat = hotseat;
        mScrimView = scrimView;
    }

    public static Launcher getLauncher(Context context) {
        return (Launcher) ActivityContext.lookupContext(context);
    }

    @Override
    public DragLayer getDragLayer() {
        return mDragLayer;
    }

    /**
     * TextLauncher: the home screen's workspace, which is made with this launcher as its context
     * (Workspace's constructor looks the launcher up).
     */
    public abstract Workspace<?> getWorkspace();

    public boolean isHotseatLayout(View layout) {
        // TODO: Remove this method
        return mHotseat != null && (layout == mHotseat);
    }

    /**
     * Creates and adds new folder to CellLayout. TextLauncher: the home screen makes the folder's
     * views (FolderIcon.inflateFolderAndIcon), adds them (Workspace.addInScreen) and writes the
     * folder to the model.
     */
    public abstract FolderIcon addFolder(CellLayout layout, int container, final int screenId,
            int cellX, int cellY);

    /**
     * Launcher.isTouchInProgress. TextLauncher: the home screen's root view says it, as it gets the
     * touches.
     */
    public abstract boolean isTouchInProgress();

    /**
     * ItemInflater.inflateItem: the view of an item dropped from elsewhere on the page.
     * TextLauncher: the home screen inflates it (its HomeItemInflater).
     */
    public abstract View inflateItem(ItemInfo info, CellLayout parent, int container);

    public CellLayout getCellLayout(int container, int screenId) {
        // TextLauncher: no hotseat.
        return getWorkspace().getScreenWithId(screenId);
    }

    /** TextLauncher: the home screen's drop target bar. */
    public abstract DropTargetBar getDropTargetBar();

    // TextLauncher: made on first use (Launcher.setupViews makes them), as the home screen makes
    // the drag controller after this launcher.
    private PopupController<Launcher> mPopupControllerForHomeScreenItems;
    private PopupController<Launcher> mPopupControllerForAppIcons;

    public PopupController<Launcher> getPopupControllerForHomeScreenItems() {
        if (mPopupControllerForHomeScreenItems == null) {
            // TextLauncher: LauncherModelModule's mappers, without Dagger.
            mPopupControllerForHomeScreenItems =
                    PopupController.PopupControllerFactory.createPopupController(
                            new PopupDataRepository(Set.of(FolderSystemShortcuts.INSTANCE,
                                    AppWidgetSystemShortcuts.INSTANCE)),
                            (LauncherDragController) getDragController());
        }
        return mPopupControllerForHomeScreenItems;
    }

    public PopupController<Launcher> getPopupControllerForAppIcons() {
        if (mPopupControllerForAppIcons == null) {
            mPopupControllerForAppIcons =
                    PopupController.PopupControllerFactory.createPopupController();
        }
        return mPopupControllerForAppIcons;
    }

    @Override
    public View.OnLongClickListener getAllAppsItemLongClickListener() {
        return ItemLongClickListener.INSTANCE_ALL_APPS;
    }

    /**
     * @return a stream of supported system shortcuts.
     */
    // TextLauncher: the home screen gives its options (docs/spec.md 5.3): App info, and its own.
    @Override
    public abstract Stream<SystemShortcut.Factory> getSupportedShortcuts(ItemInfo itemInfo);

    /**
     * TextLauncher: the widget menu's "Settings" (AppWidgetSystemShortcuts): the home screen starts
     * the widget's configuration (Launcher.setWaitingForResult and
     * LauncherWidgetHolder.startConfigActivity with REQUEST_RECONFIGURE_APPWIDGET).
     */
    public abstract void startWidgetReconfigure(
            com.android.launcher3.widget.LauncherAppWidgetHostView view);

    public Hotseat getHotseat() {
        return mHotseat;
    }

    @Override
    public ScrimView getScrimView() {
        return mScrimView;
    }

    @Override
    public StateManager<LauncherState, Launcher> getStateManager() {
        return mStateManager;
    }

    /**
     * TextLauncher: the home screen gives the handlers (Launcher.collectStateHandlers: the all apps
     * transition and the depth controller).
     */
    @Override
    public abstract void collectStateHandlers(List<StateHandler<LauncherState>> out);

    /** TextLauncher: no overview, so the background app state is the home screen. */
    @Override
    public LauncherState getBackgroundAppState() {
        return NORMAL;
    }

    /** TextLauncher: no forced invisibility (Launcher.shouldAnimateStateChange). */
    @Override
    public boolean shouldAnimateStateChange() {
        return isStarted();
    }

    /**
     * Starts the item's activity: an app or an app shortcut.
     * TextLauncher: the home screen starts it (Launcher.startActivitySafely and
     * ActivityContext.startActivitySafely).
     */
    public abstract RunnableList startActivitySafely(View v, Intent intent, ItemInfo item);

    // TextLauncher: made on first use (Launcher.onCreate makes it), as the home screen that
    // extends this gives the context its actions' labels need.
    private LauncherAccessibilityDelegate mAccessibilityDelegate;

    @Override
    public LauncherAccessibilityDelegate getAccessibilityDelegate() {
        if (mAccessibilityDelegate == null) {
            mAccessibilityDelegate = createAccessibilityDelegate();
        }
        return mAccessibilityDelegate;
    }

    /** BaseActivity.isStarted: whether the home activity is started. */
    public abstract boolean isStarted();

    /** TextLauncher: no pending activity requests (mPendingRequestArgs). */
    public boolean isWorkspaceLocked() {
        return isWorkspaceLoading();
    }

    /**
     * TextLauncher: the home screen says it (ModelCallbacks.getWorkspaceLoading): from the start
     * of a load or rebind until its items are bound.
     */
    public abstract boolean isWorkspaceLoading();

    public boolean isDraggingEnabled() {
        // We prevent dragging when we are loading the workspace as it is possible to pick up a view
        // that is subsequently removed from the workspace in startBinding().
        return !isWorkspaceLoading();
    }

    /** TextLauncher: no logging; see {@link StatsLogManager}. */
    public StatsLogManager getStatsLogManager() {
        return mStatsLogManager;
    }

    /** TextLauncher: the app's home model (LauncherModel). */
    public abstract LauncherModel getModel();

    /** TextLauncher: the home model's writer (ActivityContext.getModelWriter). */
    @Override
    public abstract IModelWriter getModelWriter();

    /** TextLauncher: the home screen's widget host (LauncherWidgetHolder). */
    public abstract AppWidgetHost getAppWidgetHolder();

    /**
     * Unbinds the view for the specified item, and removes the item and all its children.
     *
     * @param v the view being removed.
     * @param itemInfo the {@link ItemInfo} for this view.
     * @param deleteFromDb whether or not to delete this item from the db.
     */
    public boolean removeItem(View v, final ItemInfo itemInfo, boolean deleteFromDb) {
        return removeItem(v, itemInfo, deleteFromDb, null);
    }

    /**
     * Unbinds the view for the specified item, and removes the item and all its children.
     *
     * @param v the view being removed.
     * @param itemInfo the {@link ItemInfo} for this view.
     * @param deleteFromDb whether or not to delete this item from the db.
     * @param reason the resaon for removal.
     */
    public boolean removeItem(View v, final ItemInfo itemInfo, boolean deleteFromDb,
            @Nullable final String reason) {
        if (itemInfo instanceof WorkspaceItemInfo) {
            if (!removeItemFromCollectionIfNecessary(itemInfo, deleteFromDb)) {
                getWorkspace().removeWorkspaceItem(v);
            }
            if (deleteFromDb) {
                getModelWriter().deleteItemFromDatabase(itemInfo, reason);
            }
        } else if (itemInfo instanceof FolderInfo ci) {
            // TextLauncher: FolderInfo is the only CollectionInfo.
            if (!removeItemFromCollectionIfNecessary(itemInfo, deleteFromDb)) {
                getWorkspace().removeWorkspaceItem(v);
            }
            if (deleteFromDb) {
                getModelWriter().deleteCollectionAndContentsFromDatabase(ci);
            }
        } else if (itemInfo instanceof LauncherAppWidgetInfo) {
            final LauncherAppWidgetInfo widgetInfo = (LauncherAppWidgetInfo) itemInfo;
            getWorkspace().removeWorkspaceItem(v);
            if (deleteFromDb) {
                getModelWriter().deleteWidgetInfo(widgetInfo, getAppWidgetHolder(), reason);
            }
        } else {
            return false;
        }
        return true;
    }

    /**
     * Removes an item from a collection if the item is in a collection. In the case of collection
     * being folder, we simply remove the item from the folder.
     *
     * @param itemInfo the {@link ItemInfo} for the view we're looking to remove from a collection
     * @param deleteFromDb whether or not to delete this item from the db.
     *
     * @return true if we removed an item from a collection, false otherwise.
     */
    private boolean removeItemFromCollectionIfNecessary(ItemInfo itemInfo, boolean deleteFromDb) {
        View collectionIcon = getWorkspace().getViewByItemId(itemInfo.container);
        if (collectionIcon instanceof FolderIcon folderIcon) {
            // Remove the shortcut from the folder before removing it from launcher
            Folder folder = folderIcon.getFolder();
            folder.removeFolderContent(true, itemInfo);
            return true;
        }
        // TextLauncher: no app pairs.
        return false;
    }

    /**
     * TextLauncher: WidgetManagerHelper.getLauncherAppWidgetInfo, with the spans the widget takes
     * on the home grid (LauncherAppWidgetProviderInfo.initSpans), for the widget resize frame.
     */
    @Nullable
    public abstract LauncherAppWidgetProviderInfo getLauncherAppWidgetInfo(
            @Nullable AppWidgetProviderInfo info);

    /**
     * TextLauncher: a widget's resize frame opened or closed. While it is open the home screen is
     * in edit mode (docs/spec.md 5.4).
     */
    public void onWidgetResizeFrameOpenChanged(boolean open) {}

    /** TextLauncher: the home activity's RotationHelper. */
    public abstract RotationHelper getRotationHelper();

    /** Activity.getIntent of the home activity. */
    public abstract Intent getIntent();

    /** Activity.setIntent of the home activity. */
    public abstract void setIntent(Intent intent);

    /**
     * TextLauncher: StateManager.goToState(NORMAL, animated) with a success callback: the home
     * screen leaves the search results and the all apps list, and runs {@code onDone} once it is
     * shown (at once when it already is).
     */
    public abstract void goToNormalState(boolean animated, @Nullable Runnable onDone);

    /**
     * Launcher.addPendingItem: a widget or shortcut dropped from the widget picker
     * (Workspace.onDropExternal) at {@code cell}, with the spans it takes.
     * TextLauncher: one page and no hotseat, so the screen id and container are kept as given.
     */
    public abstract void addPendingItem(PendingAddItemInfo info, int container, int screenId,
            int[] cell, int spanX, int spanY);

    /**
     * The empty space's long-press menu (WorkspaceTouchListener), at the point in the drag layer's
     * coordinates.
     */
    public void showDefaultOptions(float x, float y) {
        Rect pos = new Rect();
        pos.offsetTo((int) x, (int) y);
        pos.inset(-20, -20);
        PopupContainer.Companion.showForMenuItems(
                this,
                getRootView(),
                WorkspaceLongPressOptions.getAll(asContext()), // TextLauncher: not a Context.
                pos
        );
    }

    /** Inflates the binds the provided item using animation */
    public void inflateAndBindItemWithAnimation(ItemInfo info) {
        // TextLauncher: the home screen inflates it (inflateItem).
        bindInflatedItems(
                Collections.singletonList(Pair.create(info, inflateItem(info,
                        getWorkspace().getScreenWithId(info.screenId), info.container))),
                new AnimatorSet());
    }

    /**
     * Bind all the items in the map, ignoring any null views
     *
     * @param boundAnim if non-null, uses it to create and play the bounce animation for added views
     */
    public void bindInflatedItems(
            List<Pair<ItemInfo, View>> shortcuts, @Nullable AnimatorSet boundAnim) {
        // Get the list of added items and intersect them with the set of items here
        Workspace<?> workspace = getWorkspace();
        int newItemsScreenId = -1;
        int index = 0;
        View lastAddedView = null;
        for (Pair<ItemInfo, View> e : shortcuts) {
            final ItemInfo item = e.first;

            // Remove colliding items.
            CellPos presenterPos = getCellPosMapper().mapModelToPresenter(item);
            if (item.container == CONTAINER_DESKTOP) {
                CellLayout cl = workspace.getScreenWithId(presenterPos.screenId);
                if (cl != null && cl.isOccupied(presenterPos.cellX, presenterPos.cellY)) {
                    View occupiedView = cl.getChildAt(presenterPos.cellX, presenterPos.cellY);
                    Object tag = occupiedView == null ? null : occupiedView.getTag();
                    String desc = "Collision while binding workspace item: " + item
                            + ". Collides with " + tag;
                    // TextLauncher: not a studio build (FeatureFlags.IS_STUDIO_BUILD).
                    // b/417021465 - Avoid mutating the model in the UI layer.
                    Log.e(TAG, desc, new Exception());
                    continue;
                }
            }

            View view = e.second;
            if (view == null) {
                continue;
            }
            // TextLauncher: the home screen's widget views are attached to its host as they are
            // made (LauncherWidgetHolder.attachViewToHostAndGetAttachedView).
            workspace.addInScreenFromBind(view, item);
            lastAddedView = view;
            if (boundAnim != null) {
                // Animate all the applications up now
                view.setAlpha(0f);
                view.setScaleX(0f);
                view.setScaleY(0f);
                boundAnim.play(createNewAppBounceAnimation(view, index++));
                newItemsScreenId = presenterPos.screenId;
            }
        }

        // Animate to the correct page.
        if (boundAnim != null && newItemsScreenId > -1) {
            final Runnable startBounceAnimRunnable = boundAnim::start;

            // Schedule accessibility action.
            final View a11yTarget = lastAddedView;
            boundAnim.addListener(forEndCallback((success) -> {
                // TextLauncher: isStarted for the top resumed activity (LauncherUiState).
                if (success
                        && isInState(NORMAL)
                        && isStarted()
                        && a11yTarget.isAttachedToWindow()
                        && a11yTarget.getVisibility() == View.VISIBLE) {
                    ViewEx.performAccessibilityActionOnViewTree(a11yTarget);
                }
            }));

            // TextLauncher: one page, which scrolls to show the new item instead of snapping to
            // its page.
            workspace.post(() -> a11yTarget.requestRectangleOnScreen(
                    new Rect(0, 0, a11yTarget.getWidth(), a11yTarget.getHeight()), false));
            workspace.postDelayed(startBounceAnimRunnable, NEW_APPS_ANIMATION_DELAY);
        }
        workspace.requestLayout();
    }

    private ValueAnimator createNewAppBounceAnimation(View v, int i) {
        // TextLauncher: ItemInstallQueue's NEW_SHORTCUT_BOUNCE_DURATION and
        // NEW_SHORTCUT_STAGGER_DELAY (no ItemInstallQueue).
        ValueAnimator bounceAnim = new PropertyListBuilder().alpha(1).scale(1).build(v)
                .setDuration(450);
        bounceAnim.setStartDelay(i * 85);
        bounceAnim.setInterpolator(new OvershootInterpolator(BOUNCE_ANIMATION_TENSION));
        return bounceAnim;
    }

    protected LauncherAccessibilityDelegate createAccessibilityDelegate() {
        return new LauncherAccessibilityDelegate(this);
    }

    /**
     * TextLauncher: AppInfo.makeWorkspaceItem, for an app of the all apps list added to the home
     * screen: the home screen's own item for the app (AppInfo is not a WorkspaceItemFactory here).
     */
    public abstract WorkspaceItemInfo makeWorkspaceItem(AppInfo appInfo);

    /** Activity.getRootView: the home activity's root view, which holds the popups. */
    public abstract View getRootView();

    /** TextLauncher: StateManager's isInState(NORMAL), for WorkspaceTouchListener. */
    public abstract boolean isInNormalState();

    /**
     * TextLauncher: whether the empty space's menu has "Apps list": not with two panels, where the
     * list is always shown (docs/spec.md 5.5).
     */
    public abstract boolean canOpenAllApps();

    /** TextLauncher: StateManager.goToState(ALL_APPS), the menu's "Apps list". */
    public abstract void openAllApps();

    /**
     * TextLauncher: NotificationDotsPreference.NotificationAccessConfirmation, the dialog that takes
     * the user to the notification access settings (the menu's "Notification dots").
     */
    public abstract void showNotificationAccessConfirmation();

    @Override
    public DropTargetHandler getDropTargetHandler() {
        return new DropTargetHandler(this);
    }

    private final RunnableList mResumedCallbacks = new RunnableList();

    /**
     * BaseActivity.addEventCallback. TextLauncher: only {@link BaseActivity#EVENT_RESUMED}, which
     * the home screen dispatches with {@link #onResumed()}.
     */
    public void addEventCallback(int event, Runnable callback) {
        if (event != BaseActivity.EVENT_RESUMED) {
            throw new IllegalArgumentException("Unsupported event " + event);
        }
        mResumedCallbacks.add(callback);
    }

    /** TextLauncher: BaseActivity.onResume's dispatchActivityEvent(EVENT_RESUMED). */
    public void onResumed() {
        mResumedCallbacks.executeAllAndClear();
    }

    /** No wallpaper zoom: it needs a system permission. */
    public DepthController getDepthController() {
        return null;
    }

    /** Stand-in for statehandlers.DepthController. */
    public static class DepthController {
        public MultiPropertyFactory<?>.MultiProperty getFolderZoom() {
            return null;
        }
    }
}
