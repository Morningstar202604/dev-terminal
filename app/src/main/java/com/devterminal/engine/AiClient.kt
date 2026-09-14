package com.devterminal.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * AI 助手客户端：OpenAI 兼容协议（/chat/completions）。
 *
 * 端点策略（2026 移动端主流玩法 BYOK + 本地优先）：
 * - 默认指向本机 Ollama（http://127.0.0.1:11434/v1）—— 本地推理 = 离线可用
 * - 也可填任何 OpenAI 兼容端点（llama.cpp server / LM Studio / 云端 API）
 * - 未配置时 AI 功能不可用，App 其余功能完全离线
 */
class AiClient(private val settings: com.devterminal.settings.AppSettings) {

    /** 一轮对话消息 */
    data class ChatMessage(val role: String, val content: String)

    /**
     * 发送对话请求。
     * @param system 系统提示词
     * @param history 历史消息（不含本轮）
     * @return 助手回复文本
     * @throws java.io.IOException 网络/端点错误时抛出，message 为可读说明
     */
    fun chat(system: String, history: List<ChatMessage>, userMessage: String): String {
        val base = settings.aiBaseUrl.trimEnd('/')
        val url = URL("$base/chat/completions")

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", system))
        history.forEach { m ->
            messages.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        messages.put(JSONObject().put("role", "user").put("content", userMessage))

        val body = JSONObject()
            .put("model", settings.aiModel.ifBlank { "qwen2.5-coder:3b" })
            .put("messages", messages)
            .put("temperature", 0.3)
            .put("max_tokens", 1200)
            .toString()

        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = TimeUnit.SECONDS.toMillis(8).toInt()
            conn.readTimeout = TimeUnit.SECONDS.toMillis(90).toInt()
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            if (settings.aiApiKey.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer ${settings.aiApiKey}")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val reader: BufferedReader = if (code in 200..299)
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
            else
                BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream, Charsets.UTF_8))
            val resp = reader.use { it.readText() }

            if (code !in 200..299) {
                throw java.io.IOException(friendlyHttp(code, resp))
            }
            return parseChoices(resp)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseChoices(raw: String): String = runCatching {
        val json = JSONObject(raw)
        json.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").trim()
    }.getOrElse { "AI 返回格式无法解析（确认端点是 OpenAI 兼容协议）：${raw.take(200)}" }

    private fun friendlyHttp(code: Int, body: String): String = when (code) {
        401, 403 -> "鉴权失败（401/403）：检查 API Key"
        404 -> "端点不存在（404）：URL 应以 /v1 结尾，且服务已启动"
        408, 504 -> "AI 响应超时：模型太大或服务忙，试试更小的模型"
        else -> "AI 服务返回 $code：${body.take(160)}"
    }

    companion object {
        /** 内置系统提示词：强调离线场景与简洁回答 */
        const val SYSTEM_PROMPT =
            "你是 DevTerminal 内置的编程助手，运行在安卓离线编程终端里。" +
            "用户在手机上写 Python / Java。回答要求：中文、简洁、直接给结论和代码，" +
            "代码用 Markdown 代码块标注语言，避免冗长解释。"

        /** 从报错输出中提取要发给 AI 的错误上下文（截断防 token 爆炸） */
        fun errorContext(output: List<String>): String {
            val errLines = output.filter {
                it.startsWith("[err]") || it.contains("Error") || it.contains("Exception")
            }
            val tail = output.takeLast(25).joinToString("\n")
            val chosen = if (errLines.isNotEmpty())
                (errLines.take(10) + "\n--- 完整输出尾部 ---\n" + tail).joinToString("\n")
            else tail
            return chosen.take(3000)
        }
    }
}
