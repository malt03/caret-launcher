pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Text Launcher"
include(":app")

// Code copied from AOSP (docs/launcher3-porting.md)
include(":launcher3")
project(":launcher3").projectDir = file("third_party/launcher3")
include(":iconloaderlib")
project(":iconloaderlib").projectDir = file("third_party/systemui/iconloaderlib")
include(":msdllib")
project(":msdllib").projectDir = file("third_party/systemui/msdllib")

// Stand-ins for the parts of AOSP the copied code depends on
include(":launcher3-compat")
project(":launcher3-compat").projectDir = file("compat/launcher3")
