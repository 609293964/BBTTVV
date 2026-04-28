import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)
}

val signingProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}
val releaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val missingReleaseSigningKeys = releaseSigningKeys.filter { signingProperties.getProperty(it).isNullOrBlank() }
val hasReleaseKeystore = missingReleaseSigningKeys.isEmpty()

gradle.taskGraph.whenReady {
    val hasReleaseTaskRequiringKeystore = allTasks.any { task ->
        task.name.contains("Release", ignoreCase = true)
    }
    if (!hasReleaseKeystore && hasReleaseTaskRequiringKeystore) {
        error("Missing keystore.properties for release signing: ${missingReleaseSigningKeys.joinToString()}")
    }
}

android {
    namespace = "com.bbttvv.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bbttvv.app"
        minSdk = 26
<<<<<<< HEAD
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
=======
        targetSdk = 35  // 保持35以避免Android 16的新运行时行为
        // 🔥🔥 [版本号] 发布新版前记得更新！格式：versionCode +1, versionName 递增
        // 更新日志：CHANGELOG.md
        versionCode = 169
        versionName = "8.0.0-Alpha7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
>>>>>>> 66bf842c85f92ca468e1f91940f277d9739fd68f
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isProfileable = false
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            isShrinkResources = false
            ndk {
                abiFilters += listOf("arm64-v8a", "x86", "x86_64")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        baseline = file("lint-baseline.xml")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

ksp {
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                register("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    // --- 1. Compose TV UI ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    
    // Android TV Foundation and Material
    implementation(libs.androidx.tv.material)

    // --- 2. Base Utilities matching BiliPai ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    
    // Network & serialization ready
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // Media3 ready
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Room ready
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Core libs
    implementation(project(":settings-core"))
    implementation(project(":network-core"))
    implementation(libs.androidx.datastore.preferences)

    // Utilities from BiliPai 
    implementation(libs.org.brotli.dec)
    implementation(libs.protobuf.javalite)
    implementation(libs.zxing.core)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.danmaku.render.engine)

    // Extra media dependencies
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.datasource.okhttp)

<<<<<<< HEAD
    // Coil ready
    implementation(libs.coil)
    implementation(libs.coil.compose)

    // Traditional View libraries for hybrid rendering
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
=======
    // --- 3. Image (图片加载) ---
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")  // 🔥 GIF 动图支持
    
    // --- 3.1 Palette (颜色提取 - 动态取色) ---
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("com.materialkolor:material-kolor:4.1.1")
    
    // --- 3.2 Lottie (动画效果) ---
    implementation("com.airbnb.android:lottie-compose:6.7.1")
    
    // --- 3.3 Haze (毛玻璃效果) ---
    implementation("dev.chrisbanes.haze:haze:1.7.2")
    implementation("dev.chrisbanes.haze:haze-materials:1.7.2")
    
    // --- 3.4 Shimmer (骨架屏加载) ---
    implementation("com.valentinilk.shimmer:compose-shimmer:1.4.0")
    
    // --- 3.5 Compose Cupertino (iOS 风格 UI 组件) ---
    // 提供 iOS 风格的 Switch、Button、Picker、Dialog 等组件
    implementation("io.github.alexzhirkevich:cupertino:0.1.0-alpha04")
    implementation("io.github.alexzhirkevich:cupertino-adaptive:0.1.0-alpha04")
    // 🍎 800+ iOS SF Symbols 风格图标
    implementation("io.github.alexzhirkevich:cupertino-icons-extended:0.1.0-alpha04")
    
    // --- 3.6 Orbital (iOS 风格共享元素动画) ---
    // 提供流畅的共享元素过渡、尺寸变换、位置移动动画
    implementation("com.github.skydoves:orbital:0.4.0")
    
    // --- 3.7 Startup (应用初始化) ---
    implementation("androidx.startup:startup-runtime:1.2.0")
    
    // --- 3.8 Backdrop (液态玻璃效果) ---
    // 提供透镜折射、玻璃高光、连续圆角等 iOS/visionOS 风格视觉效果
    implementation("io.github.kyant0:backdrop:1.0.6")


    // --- 4. Player (视频播放器 Media3) ---
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")  // 🔥 HLS 直播流支持
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")

    // --- 5. Danmaku (弹幕引擎) ---
    // 🔥 使用 ByteDance DanmakuRenderEngine - 轻量级高性能弹幕渲染引擎
    implementation("com.github.bytedance:DanmakuRenderEngine:v0.1.0")
    
    // 注：FFmpegKit 已于 2025 年停止维护，改用 ExoPlayer 直接播放分离音视频

    // --- 6. Database (Room 数据库) ---
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // --- 7. DataStore (本地存储 Cookie/设置) ---
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // --- 8. Utils (工具类) ---
    // 二维码生成
    implementation("com.google.zxing:core:3.5.4")
    // Pinyin 拼音转换 (用于模糊搜索)
    implementation("com.belerweb:pinyin4j:2.5.0")
    // Core KTX
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVersion")  // 🔋 ProcessLifecycleOwner 后台检测
    implementation("androidx.metrics:metrics-performance:1.0.0")
    
    // --- 8.1 WorkManager (后台下载任务) ---
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    // --- 8.2 DLNA & Local Proxy (投屏) ---
    // DLNA Casting (Cling)
    implementation("org.fourthline.cling:cling-core:2.1.2")
    implementation("org.fourthline.cling:cling-support:2.1.2")
    // Jetty (Cling 传输层依赖)
    implementation("org.eclipse.jetty:jetty-server:8.1.22.v20160922")
    implementation("org.eclipse.jetty:jetty-servlet:8.1.22.v20160922")
    implementation("org.eclipse.jetty:jetty-client:8.1.22.v20160922")
    implementation("javax.servlet:javax.servlet-api:3.1.0")
    
    // NanoHTTPD (Lightweight local proxy server)
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    implementation("androidx.navigation:navigation-compose:2.9.7")
    
    // --- 9. SplashScreen (启动屏支持) ---
    implementation("androidx.core:core-splashscreen:1.2.0")
    
    // --- 10. ProfileInstaller (启动优化) ---
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    
    // --- 11. Firebase (崩溃追踪和分析) ---
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")

    // --- 11. Debug (调试工具) ---
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    if (debugUiToolingRuntimeEnabled) {
        debugImplementation("androidx.compose.ui:ui-tooling")
    }
    if (debugLeakCanaryEnabled) {
        // 🔥 LeakCanary - 内存泄漏检测 (按需启用)
        debugImplementation("com.squareup.leakcanary:leakcanary-android:2.13")
    }
    
    // --- 12. Testing (测试框架) ---
    // JUnit 4 (兼容旧测试)
    testImplementation("junit:junit:4.13.2")
    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    // JUnit 4 兼容层 (允许 JUnit 5 运行 JUnit 4 测试)
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")
    // Kotlin Test (提供 assertEquals, assertTrue 等断言)
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
    // MockK for Kotlin mocking
    testImplementation("io.mockk:mockk:1.13.9")
    // Coroutines testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    // Turbine for Flow testing
    testImplementation("app.cash.turbine:turbine:1.0.0")
    
    // --- 13. Android Instrumented Tests ---
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.03.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

tasks.register("assembleFast") {
    group = "build"
    description = "Assembles the fast local development variant (debug)."
    dependsOn("assembleDebug")
}

tasks.register("installFast") {
    group = "install"
    description = "Installs the fast local development variant (debug) on a connected device."
    dependsOn("installDebug")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    tasks.matching { task ->
        task.name.startsWith("uploadCrashlyticsMappingFile")
    }.configureEach {
        // 本地构建不上传 mapping，避免 release/dev 在离线环境失败。
        enabled = false
    }
>>>>>>> 66bf842c85f92ca468e1f91940f277d9739fd68f
}
