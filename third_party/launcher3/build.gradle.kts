// TextLauncher: build file for files copied from packages/apps/Launcher3 (not copied from upstream).
plugins {
    alias(libs.plugins.android.library)
    // The widget resize frame (widget/resize/) is Compose, as in Launcher3.
    alias(libs.plugins.compose.compiler)
    // Dagger for the widget picker's graph (LauncherWidgetPickerModule), as in Launcher3.
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.android.launcher3"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 37 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        named("main") {
            // Stand-ins that need copied Launcher3 classes are compiled into this module.
            java.setSrcDirs(listOf("src", "shared/src", "dagger/src", "../../compat/launcher3-internal"))
            kotlin.setSrcDirs(listOf("src", "modules/concurrent/src", "../../compat/launcher3-internal"))
            // Resources the copied code needs, picked out of Launcher3 res/ (see UPSTREAM.md).
            res.setSrcDirs(listOf("res-textlauncher"))
            manifest.srcFile("TextLauncherManifest.xml")
        }
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.window)
    implementation(libs.dagger)
    ksp(libs.dagger.compiler)
    api(project(":widgetpicker"))
    api(project(":launcher3-compat"))
    api(project(":iconloaderlib"))
    api(project(":msdllib"))
}
