package com.devterminal.engine

import kotlinx.coroutines.flow.Flow

/**
 * 运行引擎的统一抽象：UI 层只依赖本接口，不关心底层实现。
 *
 * 当前实现 [PyodideEngine]（WebView + WASM，阶段 0 止血后仍可用）；
 * 阶段 1 将新增 [ChaquopyEngine]（原生 CPython）实现同一接口，
 * 切换运行时对 UI 完全透明，可双引擎并存灰度对比。
 */
interface RunEngine {

    /** 解释器是否已就绪（预热完成或首次运行完成加载） */
    val isEnvironmentReady: Boolean

    /** 预热：确保运行时可用（首次可能较慢，含 WASM/原生库装载） */
    suspend fun prepareEnvironment(onProgress: (Long, Long) -> Unit)

    /** 后台预热（不阻塞调用方） */
    fun warmup()

    /** 请求停止当前运行（引擎保证最终发出 Finished 事件收尾） */
    fun stop()

    /** 向运行中的程序写入一行输入（支持 input() 交互），返回是否已提交 */
    fun writeStdin(line: String): Boolean

    /** 关闭 stdin（EOF，等价 Ctrl-D） */
    fun closeStdin()

    /** 执行一段脚本，返回事件流 */
    fun run(request: RunRequest, timeoutMs: Long): Flow<RunEvent>

    /** 释放运行时资源（退出应用时调用） */
    fun release()
}
