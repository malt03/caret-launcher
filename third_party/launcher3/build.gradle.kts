// TextLauncher: build file for files copied from packages/apps/Launcher3 (not copied from upstream).
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.android.launcher3"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 33 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("src"))
            kotlin.setSrcDirs(listOf("src"))
            // Resources the copied code needs, picked out of Launcher3 res/ (see UPSTREAM.md).
            res.setSrcDirs(listOf("res-textlauncher"))
            manifest.srcFile("TextLauncherManifest.xml")
        }
    }
}

dependencies {
    implementation(libs.androidx.core)
    api(project(":launcher3-compat"))
    api(project(":iconloaderlib"))
}
