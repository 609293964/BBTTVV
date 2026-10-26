pluginManagement {
    repositories {
        // 阿里云镜像对部分不存在的构件返回 502（而非 404），Gradle 会因此中止查找：
        // google 镜像只用于 Google/AndroidX 构件，KSP 插件直接走官方源。
        maven("https://maven.aliyun.com/repository/google") {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                excludeGroup("com.google.devtools.ksp")
            }
        }
        maven("https://maven.aliyun.com/repository/gradle-plugin") {
            content { excludeGroup("com.google.devtools.ksp") }
        }
        maven("https://maven.aliyun.com/repository/public") {
            content { excludeGroup("com.google.devtools.ksp") }
        }
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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google") {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                excludeGroup("com.google.devtools.ksp")
            }
        }
        maven("https://maven.aliyun.com/repository/public") {
            content { excludeGroup("com.google.devtools.ksp") }
        }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://artifact.bytedance.com/repository/releases/") }
    }
}

rootProject.name = "BBTTVV"
include(":app")
include(":baselineprofile")
include(":settings-core")
include(":network-core")
