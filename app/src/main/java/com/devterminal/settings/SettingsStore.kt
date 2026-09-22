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
    val outputHeightDp: Int = 260,
    // ---------- AI 助手（可选，BYOK） ----------
    /**
     * AI 端点（OpenAI 兼容，形如 `https://<host>/v1`），由用户自行配置（BYOK）。
     * 默认值指向本机 Ollama 仅作占位示例——手机上一般没有本地推理服务，
     * 不填就用不了。AI 属于可选的在线服务，与「离线执行 Python」无关。
     */
    val aiBaseUrl: String = DEFAULT_AI_BASE_URL,
    val aiApiKey: String = "",
    val aiModel: String = DEFAULT_AI_MODEL,
    /**
     * 用户是否在设置页显式保存过 AI 配置。
     *
     * 不能靠「端点非空」来判断：默认值是占位示例（本机 Ollama），恒非空，
     * 否则「未配置」引导永远不出现，用户一点 AI 动作就直接发请求然后必然失败。
     */
    val aiConfigured: Boolean = false,
    // ---------- Git ----------
    val gitUserName: String = "",
    val gitUserEmail: String = "",
    /** 远程仓库地址（token 可内嵌在 URL 里，如 https://user:token@host/repo.git） */
    val gitRemoteUrl: String = ""
) {
    companion object {
        const val DEFAULT_AI_BASE_URL = "http://127.0.0.1:11434/v1"
        const val DEFAULT_AI_MODEL = "qwen2.5-coder:3b"
    }
}

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
        outputHeightDp = prefs.getInt(KEY_OUTPUT_H, 260),
        aiBaseUrl = prefs.getString(KEY_AI_URL, AppSettings.DEFAULT_AI_BASE_URL)
            ?: AppSettings.DEFAULT_AI_BASE_URL,
        aiApiKey = prefs.getString(KEY_AI_KEY, "") ?: "",
        aiModel = prefs.getString(KEY_AI_MODEL, AppSettings.DEFAULT_AI_MODEL)
            ?: AppSettings.DEFAULT_AI_MODEL,
        aiConfigured = prefs.getBoolean(KEY_AI_CONFIGURED, false),
        gitUserName = prefs.getString(KEY_GIT_NAME, "") ?: "",
        gitUserEmail = prefs.getString(KEY_GIT_EMAIL, "") ?: "",
        gitRemoteUrl = prefs.getString(KEY_GIT_REMOTE, "") ?: ""
    )

    fun save(s: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_DARK, s.darkTheme)
            .putInt(KEY_FONT, s.editorFontSize)
            .putBoolean(KEY_AUTOSAVE, s.autoSave)
            .putInt(KEY_TIMEOUT, s.timeoutSeconds)
            .putInt(KEY_OUTPUT_H, s.outputHeightDp)
            .putString(KEY_AI_URL, s.aiBaseUrl)
            .putString(KEY_AI_KEY, s.aiApiKey)
            .putString(KEY_AI_MODEL, s.aiModel)
            .putBoolean(KEY_AI_CONFIGURED, s.aiConfigured)
            .putString(KEY_GIT_NAME, s.gitUserName)
            .putString(KEY_GIT_EMAIL, s.gitUserEmail)
            .putString(KEY_GIT_REMOTE, s.gitRemoteUrl)
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
        const val KEY_AI_URL = "ai_base_url"
        const val KEY_AI_KEY = "ai_api_key"
        const val KEY_AI_MODEL = "ai_model"
        const val KEY_AI_CONFIGURED = "ai_configured"
        const val KEY_GIT_NAME = "git_user_name"
        const val KEY_GIT_EMAIL = "git_user_email"
        const val KEY_GIT_REMOTE = "git_remote_url"
    }
}
