package com.devterminal.settings

import android.content.Context

/** 用户可配置项 */
data class AppSettings(
    val darkTheme: Boolean = true,
    val editorFontSize: Int = 14,
    /** 切换文件时是否自动保存当前改动 */
    val autoSave: Boolean = true,
    /** 单次运行超时（秒），防止死循环把手机拖死 */
    val timeoutSeconds: Int = 120,
    /** 输出面板高度（dp），可拖拽调整，重启保留 */
    val outputHeightDp: Int = 260
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
        timeoutSeconds = prefs.getInt(KEY_TIMEOUT, 120),
        outputHeightDp = prefs.getInt(KEY_OUTPUT_H, 260)
    )

    fun save(s: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_DARK, s.darkTheme)
            .putInt(KEY_FONT, s.editorFontSize)
            .putBoolean(KEY_AUTOSAVE, s.autoSave)
            .putInt(KEY_TIMEOUT, s.timeoutSeconds)
            .putInt(KEY_OUTPUT_H, s.outputHeightDp)
            .apply()
    }

    // ---------- 会话恢复：重启后回到上次的编辑现场 ----------

    fun saveSession(projectPath: String?, filePath: String?, tabPaths: List<String>) {
        prefs.edit()
            .putString(KEY_LAST_PROJECT, projectPath)
            .putString(KEY_LAST_FILE, filePath)
            .putString(KEY_LAST_TABS, tabPaths.joinToString("\n"))
            .apply()
    }

    fun loadSession(): SessionSnapshot = SessionSnapshot(
        projectPath = prefs.getString(KEY_LAST_PROJECT, null),
        filePath = prefs.getString(KEY_LAST_FILE, null),
        tabPaths = prefs.getString(KEY_LAST_TABS, null)
            ?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    )

    /** 上一次会话的编辑现场 */
    data class SessionSnapshot(
        val projectPath: String?,
        val filePath: String?,
        val tabPaths: List<String>
    )

    private companion object {
        const val KEY_DARK = "dark_theme"
        const val KEY_FONT = "editor_font_size"
        const val KEY_AUTOSAVE = "auto_save"
        const val KEY_TIMEOUT = "timeout_seconds"
        const val KEY_OUTPUT_H = "output_height_dp"
        const val KEY_LAST_PROJECT = "last_project"
        const val KEY_LAST_FILE = "last_file"
        const val KEY_LAST_TABS = "last_tabs"
    }
}
