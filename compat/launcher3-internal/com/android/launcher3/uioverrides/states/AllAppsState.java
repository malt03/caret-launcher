/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.uioverrides.states;

import static com.android.launcher3.Utilities.shouldReduceWorkspaceBlurUsage;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_ALLAPPS;

import android.graphics.Color;

import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ScrimColors;

import java.util.concurrent.TimeUnit;

/**
 * Definition for AllApps state
 */
public class AllAppsState extends LauncherState {

    private static final int STATE_FLAGS =
            FLAG_WORKSPACE_INACCESSIBLE | FLAG_CLOSE_POPUPS | FLAG_HOTSEAT_INACCESSIBLE;
    private static final long BACK_CUJ_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);


    public AllAppsState(int id) {
        super(id, LAUNCHER_STATE_ALLAPPS, STATE_FLAGS);
    }

    @Override
    public int getTransitionDuration(ActivityContext context, boolean isToState) {
        if (Flags.allAppsSurface() && !isToState) {
            // TODO(b/414847564): Temporary workaround for no state transition during the swipe.
            return 100;
        }
        return isToState
                ? context.getDeviceProfile().getAllAppsProfile().getOpenDuration()
                : context.getDeviceProfile().getAllAppsProfile().getCloseDuration();
    }

    // TextLauncher: no jank monitor (onBackStarted, onBackInvoked, onBackCancelled,
    // onBackAnimationCompleted).

    @Override
    public String getDescription(Launcher launcher) {
        // TextLauncher: ActivityAllAppsContainerView.getDescription without tabs or the search
        // results (TextLauncher's search results are not the all apps list's).
        return launcher.asContext().getString(R.string.all_apps_button_label);
    }

    @Override
    public int getTitle() {
        return R.string.all_apps_list_label;
    }

    @Override
    public float getVerticalProgress(Launcher launcher) {
        return 0f;
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        // TextLauncher: the Launcher stand-in is not a Context.
        final float scale = shouldReduceWorkspaceBlurUsage(launcher.asContext())
                ? NO_SCALE
                : launcher.getDeviceProfile().getWorkspaceProfile().getWorkspaceContentScale();
        return new ScaleAndTranslation(scale, NO_OFFSET, NO_OFFSET);
    }

    @Override
    protected float getDepthUnchecked(ActivityContext context) {
        return shouldReduceWorkspaceBlurUsage(context.asContext())
                ? 0f
                : context.getDeviceProfile().getBottomSheetProfile().getBottomSheetDepth();
    }

    // TextLauncher: the depth controller blurs the wallpaper only (shouldBlurWorkspace).

    // TextLauncher: no launcher UI state (getVisibleElements) or floating search bar
    // (getFloatingSearchBarRestingMargin*, shouldFloatingSearchBarUsePillWhenUnfocused).

    @Override
    public ScrimColors getWorkspaceScrimColor(Launcher launcher) {
        if (Flags.allAppsSurface()) {
            // No scrim.
            return super.getWorkspaceScrimColor(launcher);
        }
        // TextLauncher: the Launcher stand-in is not a Context.
        int backgroundColor = Themes.getAttrColor(launcher.asContext(), R.attr.allAppsScrimColor);
        return new ScrimColors(backgroundColor, /* foregroundColor */ Color.TRANSPARENT);
    }
}
