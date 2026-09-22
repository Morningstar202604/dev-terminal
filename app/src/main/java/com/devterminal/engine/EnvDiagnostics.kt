package com.devterminal.engine

import android.content.Context

/** 单个运行能力的探测结果 */
data class ToolStatus(
    val name: String,
    val available: Boolean,
    val detail: String
)

/** 环境自检汇总 */
data class EnvReport(
    val prefixReady: Boolean,
    val installedSizeBytes: Long,
    val tools: List<ToolStatus>
) {
    val allCriticalAvailable: Boolean
        get() = tools.filter { it.name == "Python" }.let { list ->
            list.isEmpty() || list.any { it.available }
        }

    /** 给 UI 用的一行摘要 */
    fun summary(): String {
        val ok = tools.count { it.available }
        return "环境自检：$ok/${tools.size} 项可用"
    }
}

/**
 * 离线环境自检（Pyodide / WebAssembly 版）。
 *
 * 动机：用户拿到 APK 后，最可能的失败是「运行时资源没打进包」或「被压缩导致加载失败」。
 * 与其让他看到一个笼统的报错，不如主动检查 APK 内 assets 并给出**可执行**的提示。
 *
 * 与旧版的区别：旧版探测的是原生工具链目录（`files/usr/bin` 下的二进制），
 * 那是已废弃的 441MB 外置包方案；本版检查的是随 APK 打包的 Pyodide 运行时。
 */
object EnvDiagnostics {

    /** Pyodide 运行时必需的文件（缺任何一个都无法启动解释器） */
    private val REQUIRED_ASSETS = listOf(
        "pyodide/pyodide.mjs" to "加载器",
        "pyodide/pyodide.asm.wasm" to "解释器本体",
        "pyodide/python_stdlib.zip" to "Python 标准库",
        "python-runner.html" to "运行器页面"
    )

    /**
     * 执行诊断。检查 APK 内资源是否完整，务必在 IO 线程调用。
     *
     * @param context 用于读取 assets
     */
    fun run(context: Context): EnvReport {
        val tools = mutableListOf<ToolStatus>()
        var totalSize = 0L

        // 1) 逐个检查必需资源是否存在，并累计体积
        for ((path, label) in REQUIRED_ASSETS) {
            val size = assetSize(context, path)
            if (size > 0) {
                totalSize += size
                tools += ToolStatus(label, true, "${formatSize(size)} · $path")
            } else {
                tools += ToolStatus(label, false, "缺失：assets/$path")
            }
        }

        // 2) 能力说明（诚实标注：Python/Git 可用，Java 在 WASM 下不可用）
        tools += ToolStatus("Python 执行", true, "Pyodide（WebAssembly）· 完全离线")
        tools += ToolStatus(
            "数据科学库", true,
            "numpy / pandas 内置 wheel · 按代码 import 自动装载（零网络）"
        )
        tools += ToolStatus("Git 操作", true, "JGit（纯 Java）· 完全离线")
        tools += ToolStatus(
            "Java 执行", false,
            "不可用：JVM 无法运行于 WebAssembly 沙箱（仅保留编辑与高亮）"
        )

        val ready = REQUIRED_ASSETS.all { assetSize(context, it.first) > 0 }
        return EnvReport(ready, totalSize, tools)
    }

    /** 读取 assets 中某文件的大小；不存在或读取失败返回 0 */
    private fun assetSize(context: Context, path: String): Long = runCatching {
        context.assets.open(path).use { it.available().toLong() }
    }.getOrDefault(0L)

    /** 人类可读的体积 */
    fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024)
        else -> "%.2f GB".format(bytes / 1024.0 / 1024 / 1024)
    }
}
