// TextLauncher: build file for Launcher3's modules/widgetpicker (android-17.0.0_r1), whose sources
// are copied as is. Written from upstream's Android.bp and build.gradle.kts (which needs the
// platform tree), tracked as ref= in UPSTREAM.md. Dagger runs with KSP, as kapt does not work with
// AGP's built-in Kotlin.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.android.launcher3.widgetpicker"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 37 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("src"))
            kotlin.setSrcDirs(listOf("src"))
            res.setSrcDirs(listOf("res"))
            manifest.srcFile("AndroidManifest.xml")
        }
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.dagger)
    ksp(libs.dagger.compiler)

    // Compose UI dependencies
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    // Other UI dependencies
    implementation(libs.androidx.compose.material3.window.size.cls)
    implementation(libs.androidx.window)

    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
}
