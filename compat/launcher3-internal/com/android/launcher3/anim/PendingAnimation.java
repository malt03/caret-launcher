/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3.anim;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.util.FloatProperty;

import java.util.function.Consumer;

/**
 * Stand-in for Launcher3's PendingAnimation (android-17.0.0_r1): utility class to keep track of a
 * running animation. It plays the animators it is given together, each with the same duration.
 *
 * TextLauncher: the members of AnimatedPropertySetter that the state handlers use are in this
 * class (it has no PropertySetter), and there is no AnimatorPlaybackController: a swipe moves the
 * states' properties itself (AllAppsSwipeController).
 */
public class PendingAnimation {

    // AnimatedPropertySetter
    protected final AnimatorSet mAnim = new AnimatorSet();
    protected ValueAnimator mProgressAnimator;

    private final long mDuration;

    public PendingAnimation(long  duration) {
        mDuration = duration;
    }

    public long getDuration() {
        return mDuration;
    }

    /**
     * Utility method to sent an interpolator on an animation and add it to the list
     */
    public void add(Animator anim, TimeInterpolator interpolator) {
        anim.setInterpolator(interpolator);
        add(anim);
    }

    public void add(Animator a) {
        // TextLauncher: no SpringProperty or animation holders.
        mAnim.play(a.setDuration(mDuration));
    }

    public <T> void addFloat(T target, FloatProperty<T> property, float from, float to,
            TimeInterpolator interpolator) {
        Animator anim = ObjectAnimator.ofFloat(target, property, from, to);
        anim.setInterpolator(interpolator);
        add(anim);
    }

    /**
     * Creates and returns the underlying AnimatorSet
     */
    public AnimatorSet buildAnim() {
        if (mAnim.getChildAnimations().isEmpty()) {
            // Add a placeholder animation to that the duration is respected
            add(ValueAnimator.ofFloat(0, 1).setDuration(mDuration));
        }
        // AnimatedPropertySetter.buildAnim
        // Add progress animation to the end, so that frame callback is called after all the other
        // animation update.
        if (mProgressAnimator != null) {
            add(mProgressAnimator);
            mProgressAnimator = null;
        }
        return mAnim;
    }

    // AnimatedPropertySetter

    public <T> Animator setFloat(T target, FloatProperty<T> property, float value,
            TimeInterpolator interpolator) {
        if (property.get(target) == value) {
            // TextLauncher: PropertySetter.NO_OP.
            return null;
        }
        Animator anim = ObjectAnimator.ofFloat(target, property, value);
        anim.setInterpolator(interpolator);
        add(anim);
        return anim;
    }

    public void addEndListener(Consumer<Boolean> listener) {
        // TextLauncher: AnimatorListeners.forEndCallback, inline.
        getProgressAnimator().addListener(new AnimatorListenerAdapter() {
            boolean mDispatched = false;
            boolean mCancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mDispatched) {
                    mDispatched = true;
                    listener.accept(!mCancelled);
                }
            }
        });
    }

    private ValueAnimator getProgressAnimator() {
        if (mProgressAnimator == null) {
            mProgressAnimator = ValueAnimator.ofFloat(0, 1);
        }
        return mProgressAnimator;
    }

    /**
     * @see AnimatorSet#addListener(Animator.AnimatorListener)
     */
    public void addListener(Animator.AnimatorListener listener) {
        mAnim.addListener(listener);
    }
}
