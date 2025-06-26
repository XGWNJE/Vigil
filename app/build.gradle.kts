// app/build.gradle 或 build.gradle (app)

plugins {
    // 应用 Android 应用程序插件
    alias(libs.plugins.android.application)
    // 应用 Kotlin Android 插件
    alias(libs.plugins.kotlin.android)
    // 应用 Kotlin Compose 插件 (如果您的 libs.versions.toml 中定义了此插件)
    // 或者直接使用 id "org.jetbrains.kotlin.plugin.compose"
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.vigil"
    compileSdk = 35 // Android 编译 SDK 版本

    defaultConfig {
        applicationId = "com.example.vigil"
        minSdk = 26 // 最低支持的 SDK 版本
        targetSdk = 35 // 目标 SDK 版本
        versionCode = 1
        versionName = "1.0" // 应用版本名

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" // 测试运行器
        vectorDrawables {
            useSupportLibrary = true // 启用对矢量图的支持库
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/vigil.keystore")
            storePassword = "vigilapp"
            keyAlias = "vigil"
            keyPassword = "vigilapp"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true // 启用代码压缩（移除未使用的代码）
            isShrinkResources = true // 启用资源压缩（移除未使用的资源）
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro" // Proguard 规则文件
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11 // Java 源代码兼容性版本
        targetCompatibility = JavaVersion.VERSION_11 // Java 目标代码兼容性版本
    }
    kotlinOptions {
        jvmTarget = "11" // Kotlin 编译到 JVM 的目标版本
    }
    buildFeatures {
        compose = true // *** 启用 Jetpack Compose ***
        viewBinding = true // 如果项目中仍有部分UI使用ViewBinding，则保留此项
    }

    packaging {
        resources {
            // 排除特定路径下的资源文件，以避免打包冲突
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE.md,LICENSE-notice.md}"
        }
    }
}

dependencies {

    // Android 核心 KTX 库
    implementation("androidx.core:core-ktx:1.12.0")
    // AppCompat 库，提供向后兼容的 Material Design 组件
    implementation("androidx.appcompat:appcompat:1.6.1")
    // Material Design 组件库 (这是用于传统 View 系统的 Material 组件库)
    implementation("com.google.android.material:material:1.11.0")
    // ConstraintLayout 库 (如果传统 View 系统布局中使用)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // AndroidX Lifecycle 库 (LiveData 和 ViewModel)
    implementation(libs.androidx.lifecycle.livedata.ktx)  // LiveData KTX 扩展
    implementation(libs.androidx.lifecycle.viewmodel.ktx) // ViewModel KTX 扩展

    // AndroidX Navigation 库 (如果使用基于 Fragment 的导航)
    implementation(libs.androidx.navigation.fragment.ktx) // Navigation Fragment KTX 扩展
    implementation(libs.androidx.navigation.ui.ktx)       // Navigation UI KTX 扩展

    // LocalBroadcastManager (用于应用内广播，但请注意它已被废弃)
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")


    // *** Jetpack Compose 依赖 ***

    // Compose BOM (Bill of Materials) - 推荐使用，它能统一管理所有 Compose 相关库的版本，确保兼容性
    implementation(platform("androidx.compose:compose-bom:2024.02.00")) // 请查阅官方文档获取与您环境最匹配的最新稳定版

    // Compose UI 核心库
    implementation("androidx.compose.ui:ui")
    // Compose 图形处理库
    implementation("androidx.compose.ui:ui-graphics")
    // Compose 预览工具支持 (用于 Android Studio 中的预览)
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Compose Foundation (提供 Compose 的基础构建块)
    implementation("androidx.compose.foundation:foundation")

    // Compose Material Design 2 (M2) 组件库
    implementation("androidx.compose.material:material")
    // Compose Material Icons Core (M2 的核心图标，material 依赖通常会带上)
    implementation("androidx.compose.material:material-icons-core")
    // Compose Material Icons Extended (M2 的扩展图标，包含 Link 等)
    // *** 添加此依赖以使用 Icons.Filled.Link 等更多图标 ***
    implementation("androidx.compose.material:material-icons-extended")

    // Compose Material Design 3 (M3) 组件库
    implementation("androidx.compose.material3:material3")

    // Activity 与 Compose 的集成库
    implementation("androidx.activity:activity-compose:1.8.2") // 保持版本与BOM推荐或最新稳定版一致
    // Navigation 与 Compose 的集成库
    implementation("androidx.navigation:navigation-compose:2.7.7") // 保持版本与BOM推荐或最新稳定版一致

    // ViewModel 与 Compose 的集成库
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose") // 版本由 BOM 控制

    // LiveData 与 Compose 的集成库 (如果您需要在 Compose 中观察 LiveData)
    implementation("androidx.compose.runtime:runtime-livedata")

    // Google Accompanist 库 - Flow Layout (用于FlowRow)
    implementation("com.google.accompanist:accompanist-flowlayout:0.32.0")


    // *** 测试依赖 ***
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Compose UI 测试依赖
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00")) // 为测试也使用BOM
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Compose UI 调试工具 (例如 Layout Inspector)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // 其他您项目原有的依赖...
}
