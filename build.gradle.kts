// 根构建脚本：仅声明插件，版本统一由 gradle/libs.versions.toml 管理
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
