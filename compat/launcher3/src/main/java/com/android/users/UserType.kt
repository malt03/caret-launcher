/*
 * Copyright (C) 2026 The Android Open Source Project
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
 * Modified for TextLauncher: the upstream enum without its Parcelable part.
 * See docs/launcher3-porting.md and third_party/systemui/UPSTREAM.md.
 */

package com.android.users

/**
 * Stand-in for frameworks/libs/systemui usertypelib (android-17.0.0_r1).
 *
 * Upstream declares this enum as @Parcelize Parcelable. The parcelize compiler plugin is not
 * applied under AGP 9 built-in Kotlin here, and nothing in third_party/ parcels it, so the
 * Parcelable part is left out.
 */
enum class UserType {
    MAIN,
    WORK,
    CLONED,
    PRIVATE,
    SYSTEM_HEADLESS,
}
