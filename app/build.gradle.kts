plugins {
    alias(libs.plugins.android.application)
}

/**
 * The app's version: "major.minor.patch". The version code is worked out from it
 * (major * 1,000,000 + minor * 1,000 + patch, so 1.0.0 is 1000000), so that raising the name
 * raises the code. The minor and patch numbers stay below 1,000.
 */
val appVersionName = "1.0.0"
val appVersionCode = appVersionName.split(".").map(String::toInt).let { (major, minor, patch) ->
    require(minor < 1_000 && patch < 1_000) { "minor and patch must be below 1000: $appVersionName" }
    major * 1_000_000 + minor * 1_000 + patch
}

android {
    namespace = "com.malt03.textlauncher"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.malt03.caretlauncher"
        minSdk = 37
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // BaseActivity: the lifecycle and saved state the activity provides to Compose.
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.dynamicanimation)
    implementation(project(":launcher3"))
    implementation(project(":intentresolver"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}