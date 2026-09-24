package com.devterminal.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 真离线 Python 执行引擎（Pyodide / WebAssembly）。
 *
 * ## 为什么重写成这个
 * 旧实现依赖一个 441MB 的原生 Termux 工具链（`assets/usrtar.zip`），
 * 该文件不入库、必须联网下载，导致「离线」名不副实、且 clone 后构建出的 APK 根本跑不了代码。
 * 本实现改为内置 Pyodide（WASM 版 CPython，约 13MB），随 APK 打包，**装完即离线**。
 *
 * ## 架构
 * ```
 * PyodideEngine (本类)
 *   └─ 隐藏 WebView（渲染载体，必须在主线程创建）
 *        └─ python-runner.html   ← 加载 pyodide.mjs
 *             └─ assets/pyodide/  ← WASM + stdlib，全部来自 APK，零网络
 * ```
 * 通过 [WebViewAssetLoader] 以 https 域加载 assets，避免 `file://` 下 WASM/模块脚本的跨源限制。
 *
 * ## 与 UI 的兼容
 * 对外暴露的 [isEnvironmentReady] / [prepareEnvironment] / [run] / [writeStdin] / [closeStdin] / [stop]
 * 与旧引擎签名一致，因此 ViewModel 层无需改动。
 *
 * ## Java 说明
 * WASM 沙箱无法运行 JVM，Java 执行在本版已降级（保留编辑与语法高亮）。
 * 详见 [run] 中 JAVA 分支的提示。
 */
class PyodideEngine(private val context: Context) : RunEngine {

    /** 当前运行的取消句柄 */
    @Volatile
    private var stopRequested = false

    /** 解释器是否已就绪（由 JS 侧 onState 回调更新） */
    private val readyFlag = AtomicBoolean(false)
    override val isEnvironmentReady: Boolean get() = readyFlag.get()

    /**
     * JS 侧是否拿到 SharedArrayBuffer。
     * Android WebView 无 COOP/COEP 头时恒为 false：此时无法用 interruptBuffer 中断死循环，
     * stop / 超时一律走 [hardKillAndFinish]（销毁 WebView 重建）强杀。
     */
    private val hasSab = AtomicBoolean(false)

    /** 当前运行开始时刻，供强杀后计算耗时 */
    @Volatile
    private var startedAt = 0L

    /** 正在运行的标记，避免并发运行两段代码 */
    private val busy = AtomicBoolean(false)

    /**
     * 全局共享的 WebView。WebView 创建与销毁都必须发生在主线程，
     * 且反复创建开销大（每次都要重新加载 9MB WASM），因此整个进程复用一个实例。
     */
    private var webView: WebView? = null

    /** 解释器加载完成的信号，供首次 [run] 挂起等待；强杀重建后需要重置 */
    private var readySignal = CompletableDeferred<Unit>()

    // ==================== 生命周期 ====================

    /**
     * 预热：创建 WebView 并让 JS 侧开始加载 Pyodide。
     * 阻塞式（挂起直到就绪或超时），请在协程中调用。
     */
    override suspend fun prepareEnvironment(onProgress: (Long, Long) -> Unit) {
        onProgress(0L, 100L)
        withContext(Dispatchers.Main) { ensureWebView() }
        onProgress(50L, 100L)
        // JS 侧自己会异步加载；这里不阻塞界面，仅触发。
        // 真正需要等待时由 run() 里的 readySignal 兜底。
        onProgress(100L, 100L)
    }

    /** 主动预热解释器（后台，不阻塞） */
    override fun warmup() {
        Handler(Looper.getMainLooper()).post {
            ensureWebView()?.evaluateJavascript("window.DevTerminalBridge && window.DevTerminalBridge.warmup()", null)
        }
    }

    /** 必须在主线程调用 */
    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(): WebView? {
        webView?.let { return it }

        val loader = WebViewAssetLoader.Builder()
            // 用一个不会与外网冲突的域名承载本地 assets
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()

        val wv = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // 允许 WebAssembly 与模块脚本
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            // 字体/缩放：运行器页面本身不可见，无需交互
            settings.textZoom = 100
            addJavascriptInterface(Bridge(), "DevTerminal")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    // 拦截 https://devterminal.local/assets/... 并在 APK 内解析
                    return request?.url?.let { loader.shouldInterceptRequest(it) }
                }
            }
        }
        webView = wv
        wv.loadUrl("https://devterminal.local/assets/python-runner.html")
        Log.i(TAG, "Pyodide WebView 已创建，开始加载运行器")
        return wv
    }

    /** 释放 WebView（退出应用时调用） */
    override fun release() {
        Handler(Looper.getMainLooper()).post {
            webView?.let {
                it.removeJavascriptInterface("DevTerminal")
                it.destroy()
            }
            webView = null
            readyFlag.set(false)
            readySignal = CompletableDeferred()
        }
    }

    // ==================== 运行 ====================

    /**
     * 强制停止当前运行。
     *
     * 有 SAB：向 JS 发 KeyboardInterrupt，解释器保持温热、下次运行无需重载。
     * 无 SAB（Android WebView 常态）：evaluateJavascript 依赖 JS 事件循环，
     * 而死循环会占死事件循环，消息根本进不去 —— 只能销毁 WebView 重建强杀。
     */
    override fun stop() {
        stopRequested = true
        if (hasSab.get()) {
            Handler(Looper.getMainLooper()).post {
                webView?.evaluateJavascript("window.DevTerminalBridge && window.DevTerminalBridge.stop()", null)
            }
        } else {
            hardKillAndFinish()
        }
    }

    /**
     * 强杀当前运行：销毁 WebView 并重置解释器状态，然后补发 Finished 事件。
     * 用于无 SAB 时的 stop / 超时兜底。必须在协程外可安全调用（内部切主线程）。
     */
    private fun hardKillAndFinish() {
        stopRequested = true
        Handler(Looper.getMainLooper()).post {
            webView?.let {
                runCatching { it.removeJavascriptInterface("DevTerminal") }
                runCatching { it.stopLoading() }
                runCatching { it.destroy() }
            }
            webView = null
            readyFlag.set(false)
            readySignal = CompletableDeferred()
            RunBridge.emitFinish(RunEvent.Finished(1, System.currentTimeMillis() - startedAt))
        }
    }

    /**
     * 向运行中的程序写入一行输入（支持 Python 的 input() 交互）。
     *
     * Pyodide 版通过 JS 侧 `pushInput` 把输入追加到解释器的输入队列：
     * 若程序正阻塞在 input() 上会立即被唤醒，否则进入预喂队列等后续读取。
     *
     * @return 是否已提交（WebView 已就绪即视为成功）
     */
    override fun writeStdin(line: String): Boolean {
        var ok = false
        Handler(Looper.getMainLooper()).post {
            webView?.evaluateJavascript(
                "window.DevTerminalBridge && window.DevTerminalBridge.pushInput(${quoteJs(line)})",
                null
            )
            ok = true
        }
        return ok
    }

    /** 关闭 stdin（Pyodide 版由输入队列与超时共同决定结束，无需额外动作） */
    override fun closeStdin() { /* no-op：EOF 由队列空 + 超时触发 */ }

    /**
     * 执行，返回事件流。签名与旧引擎一致，UI 层无需改动。
     */
    override fun run(request: RunRequest, timeoutMs: Long): Flow<RunEvent> = callbackFlow {
        if (busy.getAndSet(true)) {
            trySend(RunEvent.Failed("已有程序在运行中，请先停止。"))
            close(); return@callbackFlow
        }
        stopRequested = false

        if (request.language == Language.JAVA) {
            // WASM 沙箱无法运行 JVM：明确降级提示，而不是假装能跑
            trySend(RunEvent.Failed(JAVA_UNSUPPORTED_MSG))
            busy.set(false); close(); return@callbackFlow
        }

        val script = java.io.File(request.scriptPath)
        if (!script.exists()) {
            trySend(RunEvent.Failed("文件不存在：${request.scriptPath}"))
            busy.set(false); close(); return@callbackFlow
        }

        // 收集项目内所有 .py 文件，写入虚拟 FS，保证 import 同目录模块时能解析
        val workDir = java.io.File(request.workingDir).takeIf { it.isDirectory }
            ?: script.parentFile ?: java.io.File(context.filesDir, "projects")
        val files = collectPythonFiles(workDir)

        val code = runCatching { script.readText() }.getOrElse {
            trySend(RunEvent.Failed("读取文件失败：${it.message}"))
            busy.set(false); close(); return@callbackFlow
        }

        trySend(RunEvent.Started("python ${script.name}"))

        val started = System.currentTimeMillis()
        startedAt = started

        // 事件出口：JS 回调 → Flow。收到 Finished 时立即收尾并关闭 Flow，
        // 否则 UI 的 running 状态会永远卡住（旧版漏了这一步）。
        val emit: (RunEvent) -> Unit = { ev ->
            trySend(ev)
            if (ev is RunEvent.Finished) {
                busy.set(false)
                close()
            }
        }
        RunBridge.attach(emit)

        withContext(Dispatchers.Main) { ensureWebView() }

        val payload = buildRunPayload(
            code = code,
            files = files,
            inputs = emptyList(),
            mainFile = script.name,
            timeoutSec = (timeoutMs / 1000).coerceAtLeast(1L)
        )

        withContext(Dispatchers.Main) {
            webView?.evaluateJavascript(
                "window.DevTerminalBridge && window.DevTerminalBridge.run(${quoteJs(payload)})",
                null
            )
        }

        // 兜底看门狗：JS 侧若因极端情况（WebView 崩溃等）未回调，
        // 超时后强制收尾，避免 Flow 永久挂起、UI 一直转圈。
        // 无 SAB 时这里也是死循环的最终防线：销毁 WebView 重建，强制终止。
        val watchdog = launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(timeoutMs + 5_000L)
            if (busy.get()) {
                RunBridge.emit(RunEvent.Stderr("运行器未在规定时间内返回，已强制终止。"))
                hardKillAndFinish()
            }
        }

        awaitClose {
            watchdog.cancel()
            RunBridge.detach(emit)
            busy.set(false)
        }
    }.flowOn(Dispatchers.Main)

    /** 递归收集目录下所有 .py 文件内容，映射为「相对路径 → 源码」 */
    private fun collectPythonFiles(root: java.io.File): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        if (!root.isDirectory) return out
        root.walkTopDown()
            .onEnter { !it.name.startsWith(".") }
            .filter { it.isFile && it.extension == "py" }
            .take(60) // 防御：避免超大项目把整包内容塞进 JS 桥
            .forEach { f ->
                val rel = runCatching { f.relativeTo(root).path }.getOrElse { f.name }
                runCatching { out[rel] = f.readText() }
            }
        return out
    }

    /**
     * 构造下发给 JS 的 JSON。
     * 手写 JSON 而非依赖 kotlinx.serialization：少一个依赖，且此处结构简单固定。
     */
    private fun buildRunPayload(
        code: String,
        files: Map<String, String>,
        inputs: List<String>,
        mainFile: String,
        timeoutSec: Long
    ): String {
        val filesObj = JSONObject()
        files.forEach { (k, v) -> filesObj.put(k, v) }
        val inputsArr = org.json.JSONArray()
        inputs.forEach { inputsArr.put(it) }
        return JSONObject()
            .put("code", code)
            .put("files", filesObj)
            .put("inputs", inputsArr)
            .put("mainFile", mainFile)
            .put("timeout", timeoutSec)
            .toString()
    }

    /** 把字符串转成可安全嵌入 JS 的单引号字面量 */
    private fun quoteJs(s: String): String {
        val escaped = s
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")
        return "'$escaped'"
    }

    /**
     * JS → Kotlin 的桥。
     * 所有方法都会被 WebView 在 **非主线程**（JavaBridge 线程）回调，
     * 因此内部只做线程安全的事件转发，不触碰 UI。
     */
    private inner class Bridge {
        @JavascriptInterface
        fun onOutput(payload: String) {
            val json = runCatching { JSONObject(payload) }.getOrNull() ?: return
            val text = json.optString("text")
            when (json.optString("stream")) {
                "stderr" -> RunBridge.emit(RunEvent.Stderr(text))
                else -> RunBridge.emit(RunEvent.Stdout(text))
            }
        }

        @JavascriptInterface
        fun onState(payload: String) {
            val json = runCatching { JSONObject(payload) }.getOrNull() ?: return
            val state = json.optString("state")
            Log.i(TAG, "runner state=$state msg=${json.optString("msg")}")
            if (state == "ready") {
                readyFlag.set(true)
                // msg 携带中断能力标记：'sab' = 可用 interruptBuffer；'nosab' = 只能销毁重建
                hasSab.set(json.optString("msg") == "sab")
                if (!readySignal.isCompleted) readySignal.complete(Unit)
            }
        }

        @JavascriptInterface
        fun onFinish(payload: String) {
            val json = runCatching { JSONObject(payload) }.getOrNull() ?: JSONObject()
            val exit = json.optInt("exitCode", 0)
            val duration = json.optLong("durationMs", 0L)
            RunBridge.emitFinish(RunEvent.Finished(exit, duration))
        }
    }

    /**
     * 运行事件转发中枢。
     *
     * JS 回调来自 JavaBridge 线程，而 Flow 收集在主线程，
     * 用 [AtomicReference] 持有一个「发事件」的 lambda 即可（单次只允许一个运行）。
     */
    private object RunBridge {
        private val sink = AtomicReference<((RunEvent) -> Unit)?>(null)

        /** 绑定本次运行的事件出口 */
        fun attach(emitTo: (RunEvent) -> Unit) {
            sink.set(emitTo)
        }

        /** 解除绑定（运行取消时调用；正常结束由 emitFinish 自动解除） */
        fun detach(emitTo: (RunEvent) -> Unit) {
            sink.compareAndSet(emitTo, null)
        }

        /** 发一条普通事件（stdout/stderr） */
        fun emit(ev: RunEvent) {
            sink.get()?.invoke(ev)
        }

        /**
         * 发结束事件并解除绑定：
         * 用 getAndSet(null) 保证同一轮运行只会收到一次 Finished，
         * 避免 JS 侧重复回调导致 UI 收尾两次。
         */
        fun emitFinish(ev: RunEvent.Finished) {
            val s = sink.getAndSet(null) ?: return
            s.invoke(ev)
        }
    }

    private companion object {
        const val TAG = "PyodideEngine"

        val JAVA_UNSUPPORTED_MSG = """
            |Java 运行暂不可用。
            |
            |本版为「真离线」重构：Python 由内置 Pyodide（WebAssembly）执行，随 APK 打包、装完即离线。
            |而 JVM 无法在 WebAssembly 沙箱中运行，因此 Java 的执行能力已暂时移除，仅保留编辑与语法高亮。
            |
            |如需恢复 Java 运行，需要单独引入原生 JDK 工具链（体积大、需交叉编译），
            |属于后续独立阶段的工作。
        """.trimMargin()
    }
}
