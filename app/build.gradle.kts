import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.play.publisher)
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

/**
 * The release signing key: the upload key of Google Play App Signing, kept outside the repository.
 * scripts/build-release.sh asks for it and its passwords and hands them to this build only, in the
 * environment (CARET_SIGNING_STORE_FILE, CARET_SIGNING_STORE_PASSWORD, CARET_SIGNING_KEY_ALIAS,
 * CARET_SIGNING_KEY_PASSWORD), so they are never written down. A release APK or bundle cannot be
 * made without them (see the check below); the rest of the release build (R8, the license assets)
 * can.
 */
val releaseSigning = listOf("STORE_FILE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
    .associateWith { providers.environmentVariable("CARET_SIGNING_$it").orNull?.takeIf(String::isNotEmpty) }
    .takeIf { values -> values.values.all { it != null } }
    ?.mapValues { it.value!! }

android {
    namespace = "com.malt03.caretlauncher"
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

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getValue("STORE_FILE"))
                storePassword = releaseSigning.getValue("STORE_PASSWORD")
                keyAlias = releaseSigning.getValue("KEY_ALIAS")
                keyPassword = releaseSigning.getValue("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
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

    playConfigs {
        // The debug build has the same application ID; only the release variant talks to Google Play.
        register("debug") { enabled.set(false) }
    }
}

/**
 * Google Play (Gradle Play Publisher): the store listing in src/main/play is published with
 * scripts/publish-listing.sh. The service account key stays outside the repository; the script hands
 * its contents to this build only, in ANDROID_PUBLISHER_CREDENTIALS, which the plugin reads.
 */
play {
    defaultToAppBundles.set(true)
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
// A release APK or bundle is only made signed: without the key, packaging it stops with how to
// make it, instead of leaving an APK that cannot be installed or uploaded.
val isReleaseSigned = releaseSigning != null
tasks.matching { it.name in setOf("packageRelease", "signReleaseBundle", "packageReleaseUniversalApk") }
    .configureEach {
        val signed = isReleaseSigned
        doFirst {
            if (!signed) {
                throw GradleException(
                    "The release is signed with the upload key: build it with scripts/build-release.sh, " +
                        "which asks for the key and its passwords.",
                )
            }
        }
    }

/**
 * The Apache License 2.0 terms (section 4): the APK carries the license and the NOTICE, in
 * assets/licenses/, worked out again from the dependencies at every build:
 *
 * - LICENSE: the Apache License 2.0, which the app, the AOSP code it copies and every library it
 *   bundles are under
 * - NOTICE: the app's NOTICE, third_party/NOTICE (the AOSP code), the libraries the variant bundles
 *   with the license their POM (or a parent POM) declares, and the NOTICE files the libraries carry
 *   in their Java resources
 *
 * A library whose POM declares no license, or one that is not the Apache License 2.0, stops the
 * build: its license has to be checked, and the app's LICENSE and NOTICE may need more.
 */
abstract class LicenseAssetsTask : DefaultTask() {
    @get:InputFile
    abstract val license: RegularFileProperty

    @get:InputFile
    abstract val notice: RegularFileProperty

    @get:InputFile
    abstract val thirdPartyNotice: RegularFileProperty

    /** The bundled libraries, as group:name:version. */
    @get:Input
    abstract val libraries: ListProperty<String>

    /** The libraries' POMs and their parents'. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val poms: ConfigurableFileCollection

    /** The libraries' Java resources, as "group:name:version<TAB>path". */
    @get:Input
    abstract val javaResources: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val javaResourceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private val separator =
        "-------------------------------------------------------------------------------\n\n"

    private class Pom(val parent: String?, val licenses: List<String>)

    @TaskAction
    fun write() {
        val pomsByCoordinates = poms.files.associate { file ->
            val pom = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val project = pom.documentElement
            fun child(element: org.w3c.dom.Element, name: String): org.w3c.dom.Element? =
                (0 until element.childNodes.length).map(element.childNodes::item)
                    .firstOrNull { it is org.w3c.dom.Element && it.tagName == name } as org.w3c.dom.Element?
            fun text(element: org.w3c.dom.Element?, name: String) = element?.let { child(it, name) }?.textContent?.trim()
            val parent = child(project, "parent")
            val parentCoordinates = parent?.let { "${text(it, "groupId")}:${text(it, "artifactId")}:${text(it, "version")}" }
            val coordinates = "${text(project, "groupId") ?: text(parent, "groupId")}:" +
                "${text(project, "artifactId")}:${text(project, "version") ?: text(parent, "version")}"
            val licenses = child(project, "licenses")?.getElementsByTagName("license")?.let { list ->
                (0 until list.length).map { list.item(it) as org.w3c.dom.Element }
                    .map { listOfNotNull(text(it, "name"), text(it, "url")).joinToString(" ") }
            }.orEmpty()
            coordinates to Pom(parentCoordinates, licenses)
        }
        fun licensesOf(coordinates: String): List<String> {
            var pom = pomsByCoordinates[coordinates]
            while (pom != null) {
                if (pom.licenses.isNotEmpty()) return pom.licenses
                pom = pom.parent?.let(pomsByCoordinates::get)
            }
            return emptyList()
        }
        fun isApache2(license: String) = license.lowercase().let { "apache" in it && "2.0" in it }

        val lines = libraries.get().map { library ->
            val licenses = licensesOf(library)
            if (licenses.isEmpty() || !licenses.all(::isApache2)) {
                throw GradleException(
                    "$library is not under the Apache License 2.0 as far as its POM says " +
                        "(${licenses.ifEmpty { listOf("no license") }.joinToString()}). Check its license " +
                        "before bundling it; the app's LICENSE and NOTICE only cover the Apache License 2.0.",
                )
            }
            "  $library (Apache License 2.0)"
        }

        // The NOTICE files a library carries (Apache License 2.0, section 4 d), once each.
        val notices = linkedMapOf<String, MutableList<String>>()
        javaResources.get().map { it.split("\t", limit = 2) }.forEach { (library, path) ->
            ZipFile(path).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && Regex("(^|/)NOTICE(\\.[A-Za-z]+)?$", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
                    .forEach { entry ->
                        val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }.trim()
                        notices.getOrPut(text) { mutableListOf() } += "$library (${entry.name})"
                    }
            }
        }

        val dir = outputDir.get().dir("licenses").asFile
        dir.deleteRecursively()
        dir.mkdirs()
        license.get().asFile.copyTo(dir.resolve("LICENSE"))
        dir.resolve("NOTICE").writeText(
            buildString {
                append(notice.get().asFile.readText().trimEnd()).append("\n\n").append(separator)
                append(thirdPartyNotice.get().asFile.readText().trimEnd()).append("\n\n").append(separator)
                append("Libraries bundled in the app, with the license their POM declares:\n\n")
                append(lines.joinToString("\n")).append("\n")
                if (notices.isNotEmpty()) {
                    append("\n").append(separator).append("Notices that come with the libraries:\n")
                    notices.forEach { (text, from) ->
                        append("\n== ").append(from.joinToString(", ")).append("\n\n").append(text).append("\n")
                    }
                }
            },
        )
    }
}

androidComponents {
    onVariants { variant ->
        val runtime = variant.runtimeConfiguration
        val libraries = runtime.incoming.resolutionResult.rootComponent.map { root ->
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
        }
        // The POMs (Gradle reads the Gradle Module Metadata instead and does not fetch them), and
        // their parents' up to three levels, where a POM such as Guava's leaves the license to.
        val dependencyHandler = dependencies
        fun pomConfiguration(name: String, coordinates: Provider<List<String>>) =
            configurations.create("${variant.name}LicensePoms$name") {
                isCanBeConsumed = false
                isTransitive = false
                dependencies.addAllLater(coordinates.map { list -> list.map { dependencyHandler.create("$it@pom") } })
            }
        fun parentsOf(configuration: Configuration) = configuration.incoming.files.elements.map { files ->
            files.mapNotNull { file ->
                Regex("<parent>.*?<groupId>(.*?)</groupId>.*?<artifactId>(.*?)</artifactId>.*?<version>(.*?)</version>", RegexOption.DOT_MATCHES_ALL)
                    .find(file.asFile.readText())?.destructured?.let { (group, name, version) -> "$group:$name:$version".trim() }
            }.distinct()
        }
        val poms0 = pomConfiguration("", libraries)
        val poms1 = pomConfiguration("Parents", parentsOf(poms0))
        val poms2 = pomConfiguration("Parents2", parentsOf(poms1))
        val poms3 = pomConfiguration("Parents3", parentsOf(poms2))
        // The libraries' Java resources, which a NOTICE file is among.
        val javaResources = runtime.incoming.artifactView {
            attributes { attribute(Attribute.of("artifactType", String::class.java), "android-java-res") }
        }.artifacts
        val task = tasks.register<LicenseAssetsTask>("${variant.name}LicenseAssets") {
            license.set(rootProject.file("LICENSE"))
            notice.set(rootProject.file("NOTICE"))
            thirdPartyNotice.set(rootProject.file("third_party/NOTICE"))
            this.libraries.set(libraries)
            poms.from(poms0, poms1, poms2, poms3)
            this.javaResources.set(
                javaResources.resolvedArtifacts.map { artifacts ->
                    artifacts.mapNotNull { artifact ->
                        val id = artifact.id.componentIdentifier as? org.gradle.api.artifacts.component.ModuleComponentIdentifier
                            ?: return@mapNotNull null
                        "${id.group}:${id.module}:${id.version}\t${artifact.file.path}"
                    }.sorted()
                },
            )
            javaResourceFiles.from(javaResources.artifactFiles)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, LicenseAssetsTask::outputDir)
    }
}

/**
 * Uploads the release App Bundle (made by scripts/build-release.sh) to Google Play without putting
 * it on a track: it goes into the app bundle explorer, and a release in Play Console adds it from
 * the library. The bundle's native libraries go up with it as its native debug symbols (a zip of
 * <abi>/<lib>.so). The only one, AndroidX's graphics-path, comes stripped, so AGP's
 * ndk.debugSymbolLevel finds no symbols to put in the bundle, and Play Console warns that the
 * bundle has native code without them. Both go up in one edit, which is committed only when both
 * uploads succeed.
 *
 * It reads the service account key from ANDROID_PUBLISHER_CREDENTIALS, which
 * scripts/build-and-upload-bundle.sh asks for. It does not build the bundle: the signed one is
 * built by scripts/build-release.sh, which this build (without the upload key) could not repeat.
 */
@UntrackedTask(because = "It uploads to Google Play.")
abstract class UploadBundleTask : DefaultTask() {
    @get:InputFile
    abstract val bundle: RegularFileProperty

    @get:InputDirectory
    abstract val nativeLibs: DirectoryProperty

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @get:OutputFile
    abstract val symbols: RegularFileProperty

    @TaskAction
    fun upload() {
        val key = System.getenv("ANDROID_PUBLISHER_CREDENTIALS")?.takeIf(String::isNotEmpty)
            ?: throw GradleException("Upload with scripts/build-and-upload-bundle.sh, which asks for the service account key.")
        val app = applicationId.get()
        val code = versionCode.get()
        writeSymbols()

        val credentials = HttpCredentialsAdapter(
            GoogleCredentials.fromStream(key.byteInputStream()).createScoped(AndroidPublisherScopes.ANDROIDPUBLISHER),
        )
        val timeouts = HttpRequestInitializer { request ->
            credentials.initialize(request)
            request.connectTimeout = 300_000
            request.readTimeout = 300_000
        }
        val edits = AndroidPublisher.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), timeouts)
            .setApplicationName("caret-launcher-release")
            .build()
            .edits()
        val edit = edits.insert(app, null).execute().id
        try {
            if (edits.bundles().list(app, edit).execute().bundles.orEmpty().any { it.versionCode == code }) {
                throw GradleException("Version code $code is already on Google Play: raise appVersionName in app/build.gradle.kts.")
            }
            logger.lifecycle("Uploading ${bundle.get().asFile.name} (version code $code)")
            val uploaded = edits.bundles().upload(app, edit, FileContent("application/octet-stream", bundle.get().asFile)).execute()
            if (uploaded.versionCode != code) {
                throw GradleException(
                    "The bundle has version code ${uploaded.versionCode}, not $code: build it again with scripts/build-release.sh.",
                )
            }
            logger.lifecycle("Uploading the native debug symbols")
            edits.deobfuscationfiles()
                .upload(app, edit, code, "nativeCode", FileContent("application/octet-stream", symbols.get().asFile))
                .execute()
            edits.commit(app, edit).execute()
            logger.lifecycle("Uploaded version code $code to the app bundle explorer, on no track.")
        } catch (e: Exception) {
            runCatching { edits.delete(app, edit).execute() }
            throw e
        }
    }

    private fun writeSymbols() {
        val libs = nativeLibs.get().asFile
        val files = libs.walkTopDown().filter { it.isFile && it.name.endsWith(".so") }.sortedBy { it.path }.toList()
        if (files.isEmpty()) throw GradleException("No native libraries in $libs: build the release first.")
        ZipOutputStream(symbols.get().asFile.outputStream()).use { zip ->
            for (file in files) {
                zip.putNextEntry(ZipEntry(file.relativeTo(libs).invariantSeparatorsPath))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}

tasks.register<UploadBundleTask>("uploadReleaseBundle") {
    description = "Uploads the release App Bundle and its native debug symbols to Google Play, on no track."
    bundle.set(layout.buildDirectory.file("outputs/bundle/release/app-release.aab"))
    nativeLibs.set(layout.buildDirectory.dir("intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib"))
    applicationId.set(android.defaultConfig.applicationId)
    versionCode.set(appVersionCode)
    symbols.set(layout.buildDirectory.file("outputs/native-debug-symbols/release/native-debug-symbols.zip"))
}

