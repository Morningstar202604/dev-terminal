package com.devterminal.engine

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 原生 Python 引擎（CPython 3.13 官方 Android 支持，PEP 738）。
 *
 * 与 [PyodideEngine] 实现同一 [RunEngine] 接口，UI 层零改动切换。
 *
 * 特性（对比 Pyodide/WASM）：
 * - 原生 ARM64 性能（不再有 1/10~1/50 的 WASM 降速）
 * - 真实文件系统：脚本直接读写 App 私有目录，cwd = 项目目录
 * - stdin/stdout 在 Python 层桥接：input()/print() 天然可用，无死锁
 * - 中断走 CPython 官方 Py_AddPendingCall：死循环可被 KeyboardInterrupt 终止
 *
 * 标准库存放在 files/python-stdlib（首次运行时从 assets 解压），
 * libpython3.13.so 与 libpybridge.so 在 jniLibs/arm64-v8a。
 */
class NativeEngine(private val context: Context) : RunEngine {

    private val bridge = NativeBridge()
    private val readyFlag = AtomicBoolean(false)
    override val isEnvironmentReady: Boolean get() = readyFlag.get()

    private val busy = AtomicBoolean(false)

    /** 解释器初始化是否完成（首次 run 前惰性初始化，耗时约 0.5~2s） */
    private val initLock = Any()
    private var initialized = false

    @Volatile
    private var running = false

    @Volatile
    private var startedAt = 0L

    /** 事件出口：C 回调 → Flow（同一时刻只有一个运行） */
    private val sink = AtomicReference<((RunEvent) -> Unit)?>(null)

    // ==================== 初始化 ====================

    private fun ensureStdlib(): File {
        // 标准库存放在 files/python-stdlib；不存在时从 assets/python-stdlib.zip 解压
        val dest = File(context.filesDir, "python-stdlib")
        if (dest.isDirectory && dest.list()?.isNotEmpty() == true) return dest
        synchronized(initLock) {
            if (dest.isDirectory && dest.list()?.isNotEmpty() == true) return dest
            dest.mkdirs()
            val zip = File(context.filesDir, "python-stdlib.zip")
            if (!zip.exists()) {
                context.assets.open("python-stdlib.zip").use { input ->
                    zip.outputStream().use { output -> input.copyTo(output) }
                }
            }
            runCatching {
                java.util.zip.ZipFile(zip).use { zf ->
                    zf.entries().asSequence().forEach { e ->
                        val target = File(dest, e.name)
                        if (e.isDirectory) { target.mkdirs() }
                        else {
                            target.parentFile?.mkdirs()
                            zf.getInputStream(e).use { src -> target.outputStream().use { dst -> src.copyTo(dst) } }
                        }
                    }
                }
            }.onFailure { e ->
                android.util.Log.e("NativeEngine", "stdlib 解压失败", e)
            }
            zip.delete()
        }
        return dest
    }

    private fun ensureInitialized(): Boolean {
        if (initialized) return true
        synchronized(initLock) {
            if (initialized) return true
            val stdlib = ensureStdlib()
            val ok = runCatching {
                NativeEngine.bind(this)
                bridge.attach(bridge)
                bridge.initialize(stdlib.absolutePath)
            }.getOrDefault(false)
            initialized = ok
            readyFlag.set(ok)
            return ok
        }
    }

    override suspend fun prepareEnvironment(onProgress: (Long, Long) -> Unit) {
        onProgress(0L, 100L)
        val ok = withContext(Dispatchers.IO) { ensureInitialized() }
        onProgress(100L, 100L)
        if (!ok) throw IllegalStateException("原生 Python 解释器初始化失败")
    }

    override fun warmup() {
        Thread {
            runCatching { ensureInitialized() }
        }.start()
    }

    // ==================== 运行 ====================

    override fun run(request: RunRequest, timeoutMs: Long): Flow<RunEvent> = callbackFlow {
        if (busy.getAndSet(true)) {
            trySend(RunEvent.Failed("已有程序在运行中，请先停止。"))
            close(); return@callbackFlow
        }

        val script = File(request.scriptPath)
        if (!script.exists()) {
            trySend(RunEvent.Failed("文件不存在：${request.scriptPath}"))
            busy.set(false); close(); return@callbackFlow
        }

        // 首次运行前初始化解释器（预热过则秒过）
        if (!ensureInitialized()) {
            trySend(RunEvent.Failed("原生 Python 初始化失败，请检查 libpython 是否随 APK 打包"))
            busy.set(false); close(); return@callbackFlow
        }

        trySend(RunEvent.Started("python ${script.name}"))
        val started = System.currentTimeMillis()
        startedAt = started
        running = true
        bridge.clearPendingInput()

        // 事件出口（C 回调线程 → Flow）
        sink.set { ev ->
            trySend(ev)
            if (ev is RunEvent.Finished) {
                sink.set(null)
                busy.set(false)
                running = false
                close()
            }
        }

        val job = launch(Dispatchers.IO) {
            // 跑在专用线程，Py_Initialize 已在 initialize() 完成（持 GIL 语义由 C 层管理）
            val err = bridge.execFile(script.absolutePath)
            if (err != null) {
                sink.get()?.invoke(RunEvent.Stderr(err))
                sink.get()?.invoke(RunEvent.Finished(1, System.currentTimeMillis() - started))
            } else {
                sink.get()?.invoke(RunEvent.Finished(0, System.currentTimeMillis() - started))
            }
        }

        // Kotlin 侧兜底看门狗：JS 引擎同款语义
        val watchdog = launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(timeoutMs + 5_000L)
            if (running) {
                // 极端情况下强制收尾（正常 KeyboardInterrupt 已在 C 层完成）
                sink.get()?.invoke(RunEvent.Stderr("运行超时，已强制终止。"))
                bridge.requestInterrupt()
            }
        }

        awaitClose {
            job.cancel()
            watchdog.cancel()
            sink.set(null)
            busy.set(false)
            running = false
        }
    }.flowOn(Dispatchers.Default)

    override fun stop() {
        running = false
        // 1) Py_AddPendingCall：死循环在下一个字节码检查点抛 KeyboardInterrupt；
        // 2) pushEof：若程序正阻塞在 input()（JNI 调用上，pending call 打不断），
        //    EOF 会让桥返回 EOFError，input() 立即退出——两条路都能让运行停下来。
        runCatching { bridge.requestInterrupt() }
        bridge.pushEof()
    }

    override fun writeStdin(line: String): Boolean {
        bridge.pushInputLine(line)
        return true
    }

    override fun closeStdin() {
        bridge.pushEof()
    }

    override fun release() {
        if (initialized) {
            runCatching { bridge.shutdown() }
            runCatching { bridge.detach() }
            initialized = false
            readyFlag.set(false)
        }
    }

    // ==================== C 回调入口 ====================

    private fun emit(ev: RunEvent) { sink.get()?.invoke(ev) }

    /** 由 NativeBridge 静态转发调用（C 回调线程） */
    companion object {
        private val stateSink = AtomicReference<(String, String) -> Unit>(null)
        private val lineSink = AtomicReference<(String, String) -> Unit>(null)

        /** 绑定到当前 NativeEngine 实例 */
        fun bind(engine: NativeEngine) {
            stateSink.set { s, m -> engine.onState(s, m) }
            lineSink.set { s, t -> engine.onLine(s, t) }
        }

        fun emitLine(stream: String, text: String) {
            lineSink.get()?.invoke(stream, text)
        }

        fun postState(state: String, msg: String) {
            stateSink.get()?.invoke(state, msg)
        }
    }

    private fun onLine(stream: String, text: String) {
        emit(if (stream == "stderr") RunEvent.Stderr(text) else RunEvent.Stdout(text))
    }

    private fun onState(state: String, msg: String) {
        if (state == "ready") readyFlag.set(true)
    }
}
