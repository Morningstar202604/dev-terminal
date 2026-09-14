plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.devterminal"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.devterminal"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 演示阶段复用 debug 签名，正式发布请替换为自己的 keystore
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // language-textmate 0.23.5 依赖 java.time 等需要 core desugaring
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // 与 Kotlin 1.9.24 匹配的 Compose 编译器版本
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            // SoraEditor 为 LGPL，排除其许可文件避免打包冲突（许可随源码提供）
            excludes += "/META-INF/{AL2.0,LGPL2.1,license/*}"
        }
    }
}

dependencies {
    // core library desugaring（language-textmate 需要 java.time 等 API 脱糖）
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    val composeBom = "2024.06.00"
    implementation(platform("androidx.compose:compose-bom:$composeBom"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    // 前台服务通知（兼容到 API 24）
    implementation("androidx.core:core:1.13.1")

    // ===== 代码编辑器：SoraEditor (LGPL-2.1) =====
    // 若产品闭源，需保证用户可替换该库（动态链接 + 提供目标文件）。
    // 最新版本以 https://github.com/Rosemoe/sora-editor Releases 为准。
    val sora = "0.23.5"
    implementation("io.github.Rosemoe.sora-editor:editor:$sora")
    implementation("io.github.Rosemoe.sora-editor:language-java:$sora")
    implementation("io.github.Rosemoe.sora-editor:language-textmate:$sora")
}
