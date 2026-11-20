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
