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
    description = "Runs lint, unit tests, and a release assemble for the TV app."
    dependsOn(":app:lintDebug", ":app:testDebugUnitTest", ":app:assembleRelease")
}

tasks.register("tvUiRegression") {
    group = "verification"
    description = "Runs connected TV UI smoke tests on an attached emulator or device."
    dependsOn(":app:connectedDebugAndroidTest")
}
