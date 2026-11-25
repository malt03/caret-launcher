// TextLauncher: build file for the parts of packages/modules/IntentResolver that TextLauncher
// copies (not copied from upstream; see UPSTREAM.md).
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.android.intentresolver"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 37 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        named("main") {
            // The copied files keep upstream's paths (java/src, java/res); the stand-ins are in
            // compat/intentresolver, and the extracted values in res-textlauncher.
            java.setSrcDirs(listOf("java/src", "../../compat/intentresolver"))
            kotlin.setSrcDirs(listOf("java/src", "../../compat/intentresolver"))
            res.setSrcDirs(listOf("java/res", "res-textlauncher", "../../compat/intentresolver/res"))
            manifest.srcFile("TextLauncherManifest.xml")
        }
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.recyclerview)
}
