// Tools for the store screenshots (scripts/store-screenshots.py), not part of the app: an APK whose
// instrumentation targets itself, so running it leaves the launcher as it is.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.malt03.caretlauncher.storetools"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    defaultConfig {
        applicationId = "com.malt03.caretlauncher.storetools"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "1"
    }
}
