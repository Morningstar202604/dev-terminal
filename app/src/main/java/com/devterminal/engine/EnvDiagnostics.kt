package com.devterminal.engine

import java.io.File

/** 单个工具的探测结果 */
data class ToolStatus(
    val name: String,
    val available: Boolean,
    val detail: String
)

/** 环境自检汇总 */
data class EnvDiagnostics(
    val prefixReady: Boolean,
    val installedSizeBytes: Long,
    val tools: List<ToolStatus>
) {
    val allCriticalAvailable: Boolean
        get() = tools.filter { it.name == "python" || it.name == "java" || it.name == "javac" }
            .let { list -> list.isEmpty() || list.any { it.available } }

    /** 给 UI 用的一行摘要 */
    fun summary(): String {
        val ok = tools.count { it.available }
        return "环境自检：$ok/${tools.size} 项可用"
    }
}

/**
 * 离线环境自检。
 *
 * 动机：用户拿到 APK 后，最可能的失败是「工具链没打包进去」或「架构不匹配」。
 * 与其让他看到一个笼统的报错，不如主动探测并给出**可执行**的提示。
 */
object EnvDiagnostics {

    /** 需要探测的关键工具及其探测参数 */
    private val PROBES = listOf(
        Triple("python", listOf("-V"), "Python 解释器"),
        Triple("pip", listOf("-V"), "pip 包管理"),
        Triple("java", listOf("-version"), "Java 运行时"),
        Triple("javac", listOf("-version"), "Java 编译器"),
        Triple("git", listOf("--version"), "Git")
    )

    /**
     * 执行诊断。会真实启动进程探测，务必在 IO 线程调用。
     */
    fun run(installer: EnvironmentInstaller): EnvDiagnostics {
        val ready = installer.isReady
        val size = runCatching {
            if (installer.prefixDir.exists()) installer.prefixDir.walkTopDown()
                .filter { it.isFile }.sumOf { it.length() } else 0L
        }.getOrDefault(0L)

        val env = EnvironmentInstaller.buildEnv(installer.filesDir)
        val tools = PROBES.map { (bin, args, label) ->
            val exe = File(installer.prefixDir, "bin/$bin")
            when {
                !exe.exists() -> ToolStatus(label, false, "未安装（bin/$bin 不存在）")
                !exe.canExecute() -> ToolStatus(label, false, "无执行权限（解压时权限位丢失）")
                else -> probe(exe, args, env, label)
            }
        }
        return EnvDiagnostics(ready, size, tools)
    }

    private fun probe(
        exe: File,
        args: List<String>,
        env: Map<String, String>,
        label: String
    ): ToolStatus = try {
        val p = ProcessBuilder(listOf(exe.absolutePath) + args)
            .apply { environment().putAll(env) }
            .redirectErrorStream(true)
            .start()
        val out = p.inputStream.bufferedReader().readText().trim()
        val finished = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
            p.destroyForcibly()
            ToolStatus(label, false, "探测超时（可能架构不匹配）")
        } else if (p.exitValue() == 0 || out.isNotEmpty()) {
            // 有些工具（java -version）输出到 stderr 且退出码为 0
            ToolStatus(label, true, out.lineSequence().firstOrNull()?.take(60) ?: "可用")
        } else {
            ToolStatus(label, false, "退出码 ${p.exitValue()}")
        }
    } catch (e: Exception) {
        ToolStatus(label, false, "启动失败：${e.message?.take(50)}")
    }

    /** 人类可读的体积 */
    fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024)
        else -> "%.2f GB".format(bytes / 1024.0 / 1024 / 1024)
    }
}
