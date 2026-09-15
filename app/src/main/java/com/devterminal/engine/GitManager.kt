package com.devterminal.engine

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Git 操作管理：直接调用内置工具链里的 git 二进制。
 *
 * 设计取舍：
 * - 不引入 JGit/ libgit2 依赖——工具链里本来就有 git（离线可用，零体积增量）
 * - 身份与凭据走 `-c` 参数与内嵌 URL，不写全局配置文件
 * - 输出友好化：错误信息直接可读，含常见失败的处理建议
 */
class GitManager(private val installer: EnvironmentInstaller) {

    /** 仓库状态快照 */
    data class GitStatus(
        val isRepo: Boolean,
        val branch: String,
        val changes: List<Change>,
        val ahead: Int = 0,
        val behind: Int = 0,
        val recentCommits: List<String> = emptyList()
    )

    data class Change(val statusCode: String, val path: String)

    fun isGitInstalled(): Boolean = File(installer.prefixDir, "bin/git").exists()

    private fun git(dir: File, timeoutSec: Long = 30, args: List<String>): Pair<Int, String> {
        val bin = File(installer.prefixDir, "bin/git")
        if (!bin.exists()) return 127 to "git 未随工具链安装，请重新生成 usrtar.zip"
        return try {
            val pb = ProcessBuilder(listOf(bin.absolutePath) + args)
                .directory(dir)
            val env = pb.environment()
            EnvironmentInstaller.buildEnv(installer.filesDir).forEach { (k, v) -> env[k] = v }
            pb.redirectErrorStream(true)
            val p = pb.start()
            val output = p.inputStream.bufferedReader().readText()
            val ok = p.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!ok) { p.destroyForcibly(); -1 to "git 执行超时" }
            else p.exitValue() to output.trim()
        } catch (e: Exception) {
            -1 to "git 执行失败：${e.message}"
        }
    }

    /** 读取仓库状态（未初始化时 isRepo=false） */
    fun status(dir: File): GitStatus {
        val (code, out) = git(dir, 15, listOf("status", "--porcelain", "-b"))
        if (code != 0) return GitStatus(isRepo = false, branch = "", changes = emptyList())
        var branch = ""
        val changes = mutableListOf<Change>()
        out.lines().forEach { line ->
            when {
                line.startsWith("##") -> branch = line.removePrefix("##").trim()
                    .substringBefore("...").ifBlank { "main" }
                // porcelain 格式：XY PATH。路径含空格/中文时 git 会加引号并转义，
                // 不还原的话用户看到的是 "新增 文件.py" 这种带引号的名字。
                line.length >= 4 -> changes.add(
                    Change(line.take(2).trim(), unquotePath(line.substring(3)))
                )
            }
        }
        // 最近提交
        val (_, log) = git(dir, 10, listOf("log", "--oneline", "-8"))
        val commits = if (log.startsWith("git") && log.contains("not a git repository")) emptyList()
                      else log.lines().filter { it.isNotBlank() }
        return GitStatus(isRepo = true, branch = branch, changes = changes, recentCommits = commits)
    }

    /** 初始化仓库（带默认分支 main） */
    fun init(dir: File, userName: String, userEmail: String): String {
        val (code, out) = git(dir, 15, listOf(
            "-c", "user.name=$userName", "-c", "user.email=$userEmail",
            "init", "-b", "main"
        ))
        return if (code == 0) "已初始化 Git 仓库（main）" else friendly(out)
    }

    /** 全部暂存并提交 */
    fun commitAll(dir: File, message: String, userName: String, userEmail: String): String {
        if (message.isBlank()) return "提交信息不能为空"
        val (c1, o1) = git(dir, 20, listOf("add", "-A"))
        if (c1 != 0) return friendly(o1)
        val (c2, o2) = git(dir, 30, listOf(
            "-c", "user.name=$userName", "-c", "user.email=$userEmail",
            "commit", "-m", message
        ))
        return if (c2 == 0) {
            val n = Regex("(\\d+) files? changed").find(o2)?.groupValues?.get(1) ?: "?"
            "已提交：$n 个文件变更"
        } else if (o2.contains("nothing to commit")) "没有可提交的变更"
        else friendly(o2)
    }

    /** 推送到设置里配置的远程仓库 */
    fun push(dir: File, remoteUrl: String): String {
        if (remoteUrl.isBlank()) return "请先在面板里配置远程仓库地址"
        val (code, out) = git(dir, 90, listOf("push", remoteUrl, "HEAD"))
        return if (code == 0) "推送成功"
        else friendly(out) + "（提示：私有仓库需要把用户名:令牌内嵌在 URL 里）"
    }

    /** 拉取远程更新 */
    fun pull(dir: File, remoteUrl: String): String {
        if (remoteUrl.isBlank()) return "请先在面板里配置远程仓库地址"
        val (code, out) = git(dir, 90, listOf("pull", remoteUrl))
        return if (code == 0) "拉取成功" else friendly(out)
    }

    /** 去掉 git porcelain 给路径加的引号（含中文/空格时出现） */
    private fun unquotePath(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("\"") || !trimmed.endsWith("\"") || trimmed.length < 2) return trimmed
        return trimmed.substring(1, trimmed.length - 1)
    }

    /** 把 git 报错翻译成可操作的建议 */
    private fun friendly(raw: String): String = when {
        raw.contains("not a git repository") -> "当前项目还不是 Git 仓库，先点「初始化」"
        raw.contains("Permission denied", ignoreCase = true) ||
            raw.contains("403") -> "鉴权失败：检查 URL 里的用户名与令牌"
        raw.contains("404") -> "远程仓库不存在：检查 URL 拼写"
        raw.contains("non-fast-forward") || raw.contains("rejected") ->
            "远程有新提交：先「拉取」合并后再推送"
        raw.contains("Please tell me who you are") -> "请在面板里填写 Git 用户名和邮箱"
        raw.contains("Could not resolve host") -> "无法解析主机：当前无网络或地址错误"
        raw.contains("未随工具链") -> raw
        else -> raw.ifBlank { "未知错误（退出码非 0）" }
    }
}
