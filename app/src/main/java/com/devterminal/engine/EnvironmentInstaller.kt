package com.devterminal.engine

import android.content.Context
import java.io.File

/**
 * 运行环境的目录管理（Pyodide / WebAssembly 版）。
 *
 * ## 与旧版的区别
 * 旧版负责把 441MB 的 `assets/usrtar.zip` 解压到 `files/usr`，并模拟 Termux 环境变量
 * 供原生二进制使用。该方案已废弃——工具链不入库、必须联网下载，与「离线」定位矛盾。
 *
 * 现在 Python 由随 APK 打包的 Pyodide（WASM）执行，不再需要解压任何东西，
 * 本类因此精简为纯粹的目录管理。
 *
 * ## 目录布局
 * ```
 * files/projects/   ← 用户项目（ProjectManager 使用）
 * files/home/       ← 用户 HOME（导出/临时文件）
 * ```
 */
class EnvironmentInstaller(private val context: Context) {

    val filesDir: File get() = context.filesDir
    val homeDir: File get() = File(filesDir, "home")
    val projectsDir: File get() = File(filesDir, "projects")

    /**
     * 确保工作目录存在。
     * Pyodide 运行时随 APK 打包，无需解压，因此这里只建目录。
     */
    fun ensureDirs() {
        homeDir.mkdirs()
        projectsDir.mkdirs()
    }
}
