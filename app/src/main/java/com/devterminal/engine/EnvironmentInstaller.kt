package com.devterminal.engine

import android.content.Context
import java.io.File

/**
 * 离线运行环境的路径与安装管理。
 *
 * 目录布局（参考 termux 的 Filesystem Layout，但收在 App 私有目录内）：
 *   files/usr/            ← 离线工具链放这里，等价于 termux 的 $PREFIX
 *   files/home/           ← 用户 HOME
 *   files/usr/tmp/        ← 临时目录
 *   files/projects/       ← 用户项目（由 ProjectManager 使用）
 *
 * 首次启动时从 assets/usrtar.zip 解压到 files/usr，全程不联网。
 */
class EnvironmentInstaller(private val context: Context) {

    companion object {
        const val ASSET_ARCHIVE = "usrtar.zip"
        private const val MARK_FILE = ".installed_v1"

        /** termux 风格的环境变量，供 ProcessBuilder 使用 */
        fun buildEnv(filesDir: File): Map<String, String> {
            val prefix = File(filesDir, "usr")
            val home = File(filesDir, "home")
            val tmp = File(prefix, "tmp")
            val path = listOf(
                "$prefix/bin",
                "$prefix/bin/applets",
                "$prefix/bin/termux-file-editor",
                "/system/bin",
                "/system/xbin"
            ).joinToString(":")

            return mapOf(
                "PREFIX" to prefix.absolutePath,
                "HOME" to home.absolutePath,
                "TMPDIR" to tmp.absolutePath,
                "PATH" to path,
                "LD_LIBRARY_PATH" to "$prefix/lib",
                "LANG" to "en_US.UTF-8",
                "TERM" to "xterm-256color",
                // ncurses 默认 terminfo 路径硬编码为 termux 官方 prefix，这里显式指定
                "TERMINFO" to "$prefix/share/terminfo",
                "COLORTERM" to "truecolor",
                // Python 相关
                "PYTHONHOME" to prefix.absolutePath,
                "PYTHONPATH" to "$prefix/lib/python3.14",
                // 告诉子进程不要再往系统目录写缓存
                "TMP" to tmp.absolutePath
            )
        }
    }

    val filesDir: File get() = context.filesDir
    val prefixDir: File get() = File(filesDir, "usr")
    val homeDir: File get() = File(filesDir, "home")
    val projectsDir: File get() = File(filesDir, "projects")

    private val markFile: File get() = File(filesDir, MARK_FILE)

    /** 环境是否已就绪（usr/bin/python 存在即视为就绪） */
    val isReady: Boolean
        get() = File(prefixDir, "bin/python").exists()

    /**
     * 解压离线工具链。已就绪则直接返回。
     * @param onProgress (已解压字节数, 总字节数)
     */
    fun installIfNeeded(onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        ensureDirs()
        if (isReady && markFile.exists()) return
        extractAssets(onProgress)
        markFile.writeText("ok")
    }

    fun ensureDirs() {
        prefixDir.mkdirs()
        homeDir.mkdirs()
        projectsDir.mkdirs()
        File(prefixDir, "tmp").mkdirs()
        File(prefixDir, "bin").mkdirs()
        File(prefixDir, "lib").mkdirs()
    }

    private fun extractAssets(onProgress: (Long, Long) -> Unit) {
        val am = context.assets
        val total = try {
            am.openFd(ASSET_ARCHIVE).length
        } catch (e: Exception) {
            0L
        }

        val hasArchive = runCatching { am.open(ASSET_ARCHIVE).close() }.isSuccess
        if (!hasArchive) {
            // 没有内置工具链时给出明确提示，而不是静默失败
            File(filesDir, "NO_TOOLCHAIN.txt").writeText(
                """
                未找到内置工具链 assets/$ASSET_ARCHIVE。
                请用真机 Termux 执行 tools/extract_bootstrap.sh 生成后重新打包。
                """.trimIndent()
            )
            return
        }

        am.open(ASSET_ARCHIVE).use { input ->
            java.util.zip.ZipInputStream(java.io.BufferedInputStream(input)).use { zip ->
                var written = 0L
                val buf = ByteArray(64 * 1024)
                var entry = zip.nextEntry
                while (entry != null) {
                    val outFile = File(prefixDir, entry.name)
                    // 防 Zip Slip：确保解压路径不越出 prefixDir
                    if (!outFile.canonicalPath.startsWith(prefixDir.canonicalPath + File.separator)) {
                        zip.closeEntry(); entry = zip.nextEntry; continue
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        java.io.FileOutputStream(outFile).use { fos ->
                            while (true) {
                                val n = zip.read(buf)
                                if (n <= 0) break
                                fos.write(buf, 0, n)
                                written += n
                                onProgress(written, total)
                            }
                        }
                        // 保留可执行位（zip 里可能存在 unix mode，这里对 bin/ 下文件强制置位）
                        if (entry.name.startsWith("bin/") && !entry.name.endsWith("/")) {
                            outFile.setExecutable(true, false)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }
}
