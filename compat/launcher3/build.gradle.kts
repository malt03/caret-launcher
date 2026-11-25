// Stand-ins for AOSP classes that the code in third_party/ depends on.
// Only the members that the copied code uses are implemented here.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.malt03.caretlauncher.compat.launcher3"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 37 }
    sourceSets {
        named("main") { kotlin.setSrcDirs(listOf("src/main/java")) }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
