// app/build.gradle 或 build.gradle (app)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.vigil"
    compileSdk = 35 // 使用您提供的 compileSdk 版本

    defaultConfig {
        applicationId = "com.example.vigil"
        minSdk = 26 // 使用您提供的 minSdk 版本
        targetSdk = 35 // 使用您提供的 targetSdk 版本
        versionCode = 1
        versionName = "0.8.4" // 使用您提供的 versionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true //启用代码压缩（移除未使用的代码）。
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            ) //启用资源压缩（移除未使用的资源）。
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11 // 使用您提供的 Java 版本
        targetCompatibility = JavaVersion.VERSION_11 // 使用您提供的 Java 版本
    }
    kotlinOptions {
        jvmTarget = "11" // 使用您提供的 JVM Target
    }
    buildFeatures {
        compose = true // *** 启用 Compose ***
        viewBinding = true // 如果还有其他 XML 布局使用 ViewBinding，则保留
    }



    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LA2.0,LICENSE.md,LICENSE-notice.md}" // 更新排除规则，避免冲突
        }
    }
}

dependencies {

    // 核心 KTX 库 (使用您提供的版本)
    implementation("androidx.core:core-ktx:1.12.0")
    // AppCompat 库 (使用您提供的版本)
    implementation("androidx.appcompat:appcompat:1.6.1")
    // Material Design 库 (旧的 XML 版本，如果只用 Compose UI，可以移除这个)
    implementation("com.google.android.material:material:1.11.0")
    // ConstraintLayout (如果还有 XML 布局使用) (使用您提供的版本)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // LiveData (如果需要在 ViewModel 中使用 LiveData) (使用您提供的 libs 版本)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    // ViewModel (使用您提供的 libs 版本)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    // Navigation Fragment KTX (如果保留 Fragment) (使用您提供的 libs 版本)
    implementation(libs.androidx.navigation.fragment.ktx)
    // Navigation UI KTX (如果保留 Fragment) (使用您提供的 libs 版本)
    implementation(libs.androidx.navigation.ui.ktx)

    // LocalBroadcastManager (如果继续使用本地广播)
    implementation ("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")


    // *** Jetpack Compose 依赖 ***

    // Compose BOM (Bill of Materials) 推荐使用，它帮你管理所有 Compose 库的版本兼容性
    // 如果你已经在项目根目录的 build.gradle (project) 中添加了 BOM，这里可以移除 platform()
    // 使用与 compileSdk 35 兼容的 Compose BOM 版本
    implementation(platform("androidx.compose:compose-bom:2024.02.00")) // *** 使用与 compileSdk 35 兼容的最新 BOM 版本 ***

    // Compose UI 基础库
    implementation("androidx.compose.ui:ui")
    // Compose Graphics
    implementation("androidx.compose.ui:ui-graphics")
    // Compose Tooling
    implementation("androidx.compose.ui:ui-tooling")
    // Compose Foundation (基础布局，手势等)
    implementation("androidx.compose.foundation:foundation")
    // Compose Layout (更高级的布局)
    implementation("androidx.compose.foundation:foundation-layout")

    // Compose Material Design 2 (我们引入基础库，但会自定义样式，不使用其默认外观)
    implementation("androidx.compose.material:material")
    // Compose Material Design 3 (可选，如果需要 Material 3 的基础 Composable 但自定义样式)
    // implementation('androidx.compose.material3:material3')

    // Activity 集成 Compose
    implementation("androidx.activity:activity-compose:1.8.2") // *** 使用与 compileSdk 35 兼容的最新版本 ***
    // Navigation 集成 Compose
    implementation("androidx.navigation:navigation-compose:2.7.7") // *** 使用与 compileSdk 35 兼容的最新版本 ***

    // Lifecycle ViewModel Compose integration (已在上面添加，但确保版本兼容)
    // implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0") // 检查并更新到兼容版本

    // 如果你需要 LiveData 在 Compose 中观察
    implementation("androidx.compose.runtime:runtime-livedata")


    // 测试依赖 (保留您原有的测试依赖)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Testing Compose
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // 其他你项目原有的依赖，请确保也复制过来
    // 例如：
    // implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1'
    // implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1'
    // implementation 'com.google.code.gson:gson:2.10.1'
    // ...

}
