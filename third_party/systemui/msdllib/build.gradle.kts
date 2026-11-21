// TextLauncher: build file for frameworks/libs/systemui/msdllib (not copied from upstream).
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.google.android.msdl"
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
            manifest.srcFile("TextLauncherManifest.xml")
        }
    }
}

dependencies {
    implementation(libs.androidx.annotation)
}
