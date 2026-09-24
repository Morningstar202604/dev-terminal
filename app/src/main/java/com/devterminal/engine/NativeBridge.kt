package com.devterminal.engine

import android.util.Log
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

/**
 * 原生 Python 引擎的 JNI 桥（对应 cpp/pybridge.c）。
 *
 * 回调线程模型：
 * - [onOutput] / [onState]：由 Python 执行线程（C 侧）回调，只做转发，不碰 UI；
 * - [onInputLine]：由 Python 线程阻塞调用，必须阻塞到用户输入到达——
 *   用 [inputQueue]（BlockingQueue）实现，UI 侧 [NativeEngine.writeStdin] 写入，
 *   返回 null 表示 EOF（用户点了 EOF / 停止）。
 */
class NativeBridge {

    private val inputQueue: BlockingQueue<String?> = LinkedBlockingQueue()

    /** EOF 哨兵：与真实输入行（永不为 null）区分 */
    fun pushInputLine(line: String) { inputQueue.put(line) }
    fun pushEof() { inputQueue.put(null) }
    fun clearPendingInput() { inputQueue.clear() }

    /** 由 C 层回调：Java bridge 对象方法签名必须与 pybridge.c 中一致 */
    @Suppress("unused")
    fun onOutput(stream: String?, text: String?) {
        val s = stream ?: "stdout"
        val t = text ?: ""
        // 按行转发：Kotlin 侧 RunEvent 一行一行收
        t.split('\n').forEach { line ->
            if (line.isNotEmpty()) NativeEngine.emitLine(s, line)
        }
    }

    @Suppress("unused")
    fun onState(state: String?, msg: String?) {
        NativeEngine.postState(state ?: "", msg ?: "")
    }

    /** 由 C 层回调（Python 线程，阻塞）：取下一行输入，队列空则等待 */
    @Suppress("unused")
    fun onInputLine(): String? = inputQueue.take()

    // ---- JNI 方法声明（签名与 pybridge.c 对应） ----
    external fun attach(bridge: NativeBridge)
    external fun detach()
    external fun initialize(stdlibPath: String?): Boolean
    external fun execFile(path: String): String?
    external fun requestInterrupt()
    external fun shutdown()

    companion object {
        init { System.loadLibrary("pybridge") }
        private const val TAG = "NativeBridge"
        fun log(msg: String) = Log.i(TAG, msg)
    }
}
