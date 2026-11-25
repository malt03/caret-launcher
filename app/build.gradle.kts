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
/**
 * The Apache License 2.0 terms (section 4): the APK carries the license and the NOTICE, in
 * assets/licenses/. The NOTICE is the app's own NOTICE, then third_party/NOTICE with the libraries
 * the variant bundles listed after it (all under the Apache License 2.0; check the license of a
 * library before adding it).
 */
abstract class LicenseAssetsTask : DefaultTask() {
    @get:InputFile
    abstract val license: RegularFileProperty

    @get:InputFile
    abstract val notice: RegularFileProperty

    @get:InputFile
    abstract val thirdPartyNotice: RegularFileProperty

    @get:Input
    abstract val libraries: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private val separator =
        "-------------------------------------------------------------------------------\n\n"

    @TaskAction
    fun write() {
        val dir = outputDir.get().dir("licenses").asFile
        dir.deleteRecursively()
        dir.mkdirs()
        license.get().asFile.copyTo(dir.resolve("LICENSE"))
        dir.resolve("NOTICE").writeText(
            notice.get().asFile.readText().trimEnd() + "\n\n" + separator +
                thirdPartyNotice.get().asFile.readText().trimEnd() + "\n\n" + separator +
                "Libraries bundled in the app:\n\n" +
                libraries.get().joinToString("\n") { "  $it" } + "\n",
        )
    }
}

androidComponents {
    onVariants { variant ->
        val task = tasks.register<LicenseAssetsTask>("${variant.name}LicenseAssets") {
            license.set(rootProject.file("LICENSE"))
            notice.set(rootProject.file("NOTICE"))
            thirdPartyNotice.set(rootProject.file("third_party/NOTICE"))
            libraries.set(
                variant.runtimeConfiguration.incoming.resolutionResult.rootComponent.map { root ->
                    val seen = mutableSetOf<org.gradle.api.artifacts.result.ResolvedComponentResult>()
                    fun visit(component: org.gradle.api.artifacts.result.ResolvedComponentResult) {
                        if (!seen.add(component)) return
                        component.dependencies
                            .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
                            .forEach { visit(it.selected) }
                    }
                    visit(root)
                    seen.mapNotNull { it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier }
                        .map { "${it.group}:${it.module}:${it.version}" }
                        .sorted()
                },
            )
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, LicenseAssetsTask::outputDir)
    }
}
