package com.devterminal.settings

import android.content.Context

/** 用户可配置项 */
data class AppSettings(
    val darkTheme: Boolean = true,
    val editorFontSize: Int = 14,
    /** 切换文件时是否自动保存当前改动 */
    val autoSave: Boolean = true,
    /** 单次运行超时（秒），防止死循环把手机拖死 */
    val timeoutSeconds: Int = 120
)

/**
 * 基于 SharedPreferences 的轻量设置存储。
 *
 * 选择 SharedPreferences 而非 DataStore：本应用只有几个标量配置，
 * 没有必要为它引入 DataStore 的协程/Flow 复杂度和额外依赖。
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("devterminal_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        darkTheme = prefs.getBoolean(KEY_DARK, true),
        editorFontSize = prefs.getInt(KEY_FONT, 14),
        autoSave = prefs.getBoolean(KEY_AUTOSAVE, true),
        timeoutSeconds = prefs.getInt(KEY_TIMEOUT, 120)
    )

    fun save(s: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_DARK, s.darkTheme)
            .putInt(KEY_FONT, s.editorFontSize)
            .putBoolean(KEY_AUTOSAVE, s.autoSave)
            .putInt(KEY_TIMEOUT, s.timeoutSeconds)
            .apply()
    }

    private companion object {
        const val KEY_DARK = "dark_theme"
        const val KEY_FONT = "editor_font_size"
        const val KEY_AUTOSAVE = "auto_save"
        const val KEY_TIMEOUT = "timeout_seconds"
    }
}
