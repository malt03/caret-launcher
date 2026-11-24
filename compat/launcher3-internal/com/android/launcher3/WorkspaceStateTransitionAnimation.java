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

package com.android.launcher3;

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.app.animation.Interpolators.ZOOM_OUT;
import static com.android.launcher3.LauncherAnimUtils.SCALE_INDEX_WORKSPACE_STATE;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.LauncherAnimUtils.WORKSPACE_SCALE_PROPERTY_FACTORY;
import static com.android.launcher3.anim.PropertySetter.NO_ANIM_PROPERTY_SETTER;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_TRANSLATE;
import static com.android.launcher3.states.StateAnimationConfig.SKIP_SCRIM;

import android.util.FloatProperty;
import android.view.animation.Interpolator;

import com.android.launcher3.LauncherState.ScaleAndTranslation;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.states.SpringLoadedState;
import com.android.launcher3.states.StateAnimationConfig;

/**
 * Manages the animations between each of the workspace states.
 */
/*
 * TextLauncher: derived from WorkspaceStateTransitionAnimation.java (android-17.0.0_r1) by deleting
 * the hotseat (the search field, which the home screen moves with the keyboard), the page
 * indicator, the page alpha and translation (one page), the hint state's spring, the system UI
 * scrim and the ScrimView's colors (the all apps transition dims the home screen); changes are
 * marked with "TextLauncher:".
 */
public class WorkspaceStateTransitionAnimation {

    private static final FloatProperty<Workspace<?>> WORKSPACE_SCALE_PROPERTY =
            WORKSPACE_SCALE_PROPERTY_FACTORY.get(SCALE_INDEX_WORKSPACE_STATE);

    private final Launcher mLauncher;
    private final Workspace<?> mWorkspace;

    private float mNewScale;

    public WorkspaceStateTransitionAnimation(Launcher launcher, Workspace<?> workspace) {
        mLauncher = launcher;
        mWorkspace = workspace;
    }

    public void setState(LauncherState toState) {
        setWorkspaceProperty(toState, NO_ANIM_PROPERTY_SETTER, new StateAnimationConfig());
    }

    /**
     * @see com.android.launcher3.statemanager.StateManager.StateHandler#setStateWithAnimation
     */
    public void setStateWithAnimation(
            LauncherState toState, StateAnimationConfig config, PendingAnimation animation) {
        setWorkspaceProperty(toState, animation, config);
    }

    public float getFinalScale() {
        return mNewScale;
    }

    /**
     * Starts a transition animation for the workspace.
     */
    private void setWorkspaceProperty(LauncherState state, PropertySetter propertySetter,
            StateAnimationConfig config) {
        ScaleAndTranslation scaleAndTranslation = state.getWorkspaceScaleAndTranslation(mLauncher);
        // TextLauncher: no hotseat (getHotseatScaleAndTranslation).
        mNewScale = scaleAndTranslation.scale;
        // TextLauncher: one page, which is not a child of the workspace, and no page alpha
        // (getWorkspacePageAlphaProvider).
        final int childCount = mWorkspace.getPageCount();
        for (int i = 0; i < childCount; i++) {
            applyChildState(state, mWorkspace.getPageAt(i), i, propertySetter, config);
        }

        Interpolator scaleInterpolator = config.getInterpolator(ANIM_WORKSPACE_SCALE, ZOOM_OUT);
        // TextLauncher: no hint state, so no spring (getSpringScaleAnimator).
        propertySetter.setFloat(mWorkspace, WORKSPACE_SCALE_PROPERTY, mNewScale,
                scaleInterpolator);

        // TextLauncher: no hotseat (its scale, alpha and accessibility) and no page indicator.

        Interpolator translationInterpolator =
                config.getInterpolator(ANIM_WORKSPACE_TRANSLATE, ZOOM_OUT);
        propertySetter.setFloat(mWorkspace, VIEW_TRANSLATE_X,
                scaleAndTranslation.translationX, translationInterpolator);
        propertySetter.setFloat(mWorkspace, VIEW_TRANSLATE_Y,
                scaleAndTranslation.translationY, translationInterpolator);
        // TextLauncher: one page, so no page translations (getWorkspacePageTranslationProvider),
        // no hotseat, and centerSpringLoadedStateVertically is off in the release.

        if (!config.hasAnimationFlag(SKIP_SCRIM)) {
            setScrim(propertySetter, state, config);
        }
    }

    public void setScrim(PropertySetter propertySetter, LauncherState state,
            StateAnimationConfig config) {
        // TextLauncher: the workspace drag scrim is a view behind the home screen (DragLayer's
        // getWorkspaceDragScrim), faded with the state's alpha. No system UI scrim, and the
        // ScrimView's colors are the all apps transition's and the folders'.
        propertySetter.setViewAlpha(mLauncher.getDragLayer().getWorkspaceDragScrim(),
                state.getWorkspaceBackgroundAlpha(mLauncher), LINEAR);
    }

    public void applyChildState(LauncherState state, CellLayout cl, int childIndex) {
        applyChildState(state, cl, childIndex, NO_ANIM_PROPERTY_SETTER,
                new StateAnimationConfig());
    }

    // TextLauncher: no page alpha (PageAlphaProvider), as there is one page, and no edit mode
    // state (EditModeState).
    private void applyChildState(LauncherState state, CellLayout cl, int childIndex,
            PropertySetter propertySetter, StateAnimationConfig config) {
        float springLoadedProgress = (state instanceof SpringLoadedState) ? 1f : 0f;
        propertySetter.setFloat(cl,
                CellLayout.SPRING_LOADED_PROGRESS, springLoadedProgress, ZOOM_OUT);
    }

    // TextLauncher: one page (applyPageTranslation), and no hint state's spring
    // (getWorkspaceSpringScaleAnimator, getSpringScaleAnimator).
}
