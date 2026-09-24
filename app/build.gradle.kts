plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Kotlin 2.0 起，Compose 编译器不再由 kotlinCompilerExtensionVersion 指定，
    // 而是作为独立的 Kotlin 编译器插件引入（K2 编译器）。
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.devterminal"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.devterminal"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 11
        versionName = "0.9.1"
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
        // language-textmate 依赖 java.time 等需要 core desugaring
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // SoraEditor 为 LGPL，排除其许可文件避免打包冲突（许可随源码提供）
            excludes += "/META-INF/{AL2.0,LGPL2.1,license/*}"
        }
        // ⚠ 关键：Pyodide 的 .wasm 与 .zip 若被 APK 再压缩，WebView 加载会失败。
        // 必须原样存储（noCompress），这是 Pyodide 在 Android 上最常见的坑。
        jniLibs { useLegacyPackaging = false }
    }

    // 让 assets 里的 WASM/标准库原样打包，不被 AAPT 压缩
    androidResources {
        noCompress += listOf("wasm", "zip")
    }
}

dependencies {
    // core library desugaring（language-textmate 需要 java.time 等 API 脱糖）
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Compose：版本由 BOM 统一约束
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // AndroidX 基础
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ===== 代码编辑器：SoraEditor (LGPL-2.1) =====
    // 若产品闭源，需保证用户可替换该库（动态链接 + 提供目标文件）。
    implementation(libs.sora.editor)
    implementation(libs.sora.editor.language.java)
    implementation(libs.sora.editor.language.textmate)

    // ===== WebView 本地资源加载：Pyodide 运行器以此加载 APK 内 assets =====
    implementation(libs.androidx.webkit)

    // ===== Git：JGit（纯 Java，恢复完整 Git 能力，同样离线） =====
    implementation(libs.jgit)
    implementation(libs.slf4j.nop)

    // ===== Markdown 解析：实时预览用（纯 Kotlin，GFM 方言） =====
    implementation(libs.intellij.markdown)

    // 单元测试：纯 JVM 逻辑（如报错行解析）不依赖 Android 框架，可直接跑
    testImplementation(libs.junit)
}
