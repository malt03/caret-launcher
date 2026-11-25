// CaretLauncher: build file for frameworks/libs/systemui/iconloaderlib (not copied from upstream).
// Only src/ (iconloader_base) is used; src_full_lib/ needs system APIs.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.android.launcher3.icons"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 37 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        named("main") {
            java.setSrcDirs(listOf("src"))
            kotlin.setSrcDirs(listOf("src"))
            res.setSrcDirs(listOf("res"))
            manifest.srcFile("CaretLauncherManifest.xml")
        }
    }
}

dependencies {
    implementation(libs.androidx.core)
    api(project(":launcher3-compat"))
}
