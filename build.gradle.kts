buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("tvBuild") {
    group = "build"
    description = "Assembles the release build for the TV app."
    dependsOn(":app:assembleRelease")
}

tasks.register("tvInstall") {
    group = "install"
    description = "Installs the release build for the TV app on a connected device."
    dependsOn(":app:installRelease")
}

tasks.register("tvVerification") {
    group = "verification"
    description = "Runs debug lint and a debug assemble for the TV app."
    dependsOn(":app:lintDebug", ":app:assembleDebug")
}

tasks.register("tvReleaseVerification") {
    group = "verification"
    description = "Runs release lint and release assemble for the TV app."
    dependsOn(":app:lintRelease", ":app:assembleRelease")
}
