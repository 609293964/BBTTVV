buildscript {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
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
    description = "Runs debug lint, unit tests, and a debug assemble for the TV app."
    dependsOn(":app:lintDebug", ":app:testDebugUnitTest", ":app:assembleDebug")
}

tasks.register("tvReleaseVerification") {
    group = "verification"
    description = "Runs release lint and release assemble for the TV app."
    dependsOn(":app:lintRelease", ":app:assembleRelease")
}

tasks.register("tvBaselineProfile") {
    group = "verification"
    description = "Generates the TV Baseline Profile on a connected device."
    dependsOn(":app:generateReleaseBaselineProfile")
}

tasks.register("tvProfiledRelease") {
    group = "build"
    description = "Generates the TV Baseline Profile, then assembles the release APK with the fresh profile."
    dependsOn(":app:generateReleaseBaselineProfile", ":app:assembleRelease")
}

gradle.projectsEvaluated {
    project(":app").tasks.named("assembleRelease").configure {
        mustRunAfter(project(":app").tasks.named("generateReleaseBaselineProfile"))
    }
}

tasks.register("tvUiRegression") {
    group = "verification"
    description = "Runs connected TV UI smoke tests on an attached emulator or device."
    dependsOn(":app:connectedDebugAndroidTest")
}
