package com.devterminal.engine

import java.io.File

/**
 * Git 操作管理。
 *
 * ## 当前状态：暂不可用（诚实标注）
 * 旧实现调用内置工具链里的 `git` 原生二进制（`files/usr/bin/git`）。
 * 该工具链方案已废弃——它不入库、必须联网下载 441MB，与「真离线」定位矛盾。
 *
 * 在 Pyodide（WebAssembly）架构下，Python 由内置 WASM 解释器执行，
 * 但 **WASM 沙箱内无法运行 git 原生二进制**，因此 Git 能力暂时下线。
 *
 * ## 后续恢复方案（不在本轮范围）
 * 引入 JGit（纯 Java 实现，无外部二进制依赖）即可恢复完整 Git 能力，
 * 且同样离线可用。这属于独立阶段的工作，避免为次要模块影响主线稳定性。
 *
 * ## 本类为何还保留
 * UI 层（GitPanel / 状态栏 / 命令面板）已完整实现并引用本类，
 * 保留接口可让 UI 零改动，后续接入 JGit 时只需替换内部实现。
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

    companion object {
        /** 统一的「暂不可用」说明，UI 直接展示 */
        const val UNAVAILABLE_MSG =
            "Git 暂不可用：本版运行于 WebAssembly 架构，沙箱内无法运行 git 二进制。" +
                "后续将通过 JGit（纯 Java 实现）恢复，同样无需联网。"
    }

    /** 本版恒为 false —— git 能力已随原生工具链一并下线 */
    fun isGitInstalled(): Boolean = false

    /** 读取仓库状态：本版直接返回「非仓库」 */
    fun status(dir: File): GitStatus =
        GitStatus(isRepo = false, branch = "", changes = emptyList())

    /** 初始化仓库：本版不可用 */
    fun init(dir: File, userName: String, userEmail: String): String = UNAVAILABLE_MSG

    /** 提交：本版不可用 */
    fun commitAll(dir: File, message: String, userName: String, userEmail: String): String =
        UNAVAILABLE_MSG

    /** 推送：本版不可用 */
    fun push(dir: File, remoteUrl: String): String = UNAVAILABLE_MSG

    /** 拉取：本版不可用 */
    fun pull(dir: File, remoteUrl: String): String = UNAVAILABLE_MSG
}
