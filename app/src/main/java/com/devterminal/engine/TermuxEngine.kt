package com.devterminal.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

/**
 * 离线执行引擎。
 *
 * 设计取舍：
 *  - 不自造终端模拟器（那是 termux 十年工程），改用 ProcessBuilder 直接起进程，
 *    这是「完全离线、无按需下载」场景下最稳的做法。
 *  - 环境变量完全模拟 termux 布局（见 [EnvironmentInstaller.buildEnv]），
 *    因此内置的 termux 预编译二进制可以在 bionic 上正常跑。
 *  - 输出以 [RunEvent] 流式回调，UI 可实时显示；stdin 双向可写，支持 input() 交互。
 *
 * 关键健壮性设计：
 *  - **命令一律以参数数组下发**，不拼 `sh -c` 字符串：文件名里的引号、分号、
 *    空格都不会被 shell 解释（否则一个叫 `a';rm -rf *;'.java` 的文件就能注入命令）。
 *  - 等待进程结束用轮询而非阻塞 [Process.waitFor]，这样协程取消（ViewModel 销毁）
 *    能真正中断等待并回收子进程，不会泄漏进程。
 *  - Java 走「编译整包 → 运行主类」两步；编译失败即中止，带出 javac 的原始报错。
 */
class TermuxEngine(private val context: Context) {

    private val installer = EnvironmentInstaller(context)

    /** 当前运行的进程，用于停止与 stdin 写入 */
    @Volatile
    private var currentProcess: Process? = null

    /** 当前进程的 stdin 写入器 */
    private val stdinWriter = AtomicReference<BufferedWriter?>(null)

    val isEnvironmentReady: Boolean get() = installer.isReady

    /** 强制停止当前运行的进程 */
    fun stop() {
        val p = currentProcess ?: return
        currentProcess = null
        // 先断掉 stdin，避免进程卡在读输入上
        runCatching { stdinWriter.getAndSet(null)?.close() }
        runCatching { p.destroy() }
        Thread {
            try {
                if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly()
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }
    }

    /**
     * 向运行中的进程写入一行输入（模拟用户敲回车）。
     * 供 Python 的 input() 等交互场景使用。
     *
     * 注意：管道缓冲区满时 write 会阻塞，**必须在 IO 线程调用**。
     * @return 是否写入成功
     */
    fun writeStdin(line: String): Boolean {
        val writer = stdinWriter.get() ?: return false
        return try {
            writer.write(line)
            writer.newLine()
            writer.flush()
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 关闭 stdin（等价于 Ctrl-D），让等待输入的进程收到 EOF */
    fun closeStdin() {
        runCatching { stdinWriter.getAndSet(null)?.close() }
    }

    /** 触发首次安装（解压离线工具链），阻塞式，请在 IO 线程调用 */
    fun prepareEnvironment(onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        installer.installIfNeeded(onProgress)
    }

    /**
     * 执行，返回事件流。内部带超时保护，防止死循环把手机卡死。
     */
    fun run(request: RunRequest, timeoutMs: Long = 120_000L): Flow<RunEvent> = channelFlow {
        if (!isEnvironmentReady) {
            send(RunEvent.Failed("离线运行环境未就绪：缺少内置工具链（assets/usrtar.zip）。"))
            return@channelFlow
        }
        val script = File(request.scriptPath)
        if (!script.exists()) {
            send(RunEvent.Failed("文件不存在：${request.scriptPath}"))
            return@channelFlow
        }

        val env = EnvironmentInstaller.buildEnv(installer.filesDir)
        val workDir = File(request.workingDir).takeIf { it.isDirectory }
            ?: script.parentFile ?: installer.homeDir
        val started = System.currentTimeMillis()

        // 每条分支都保证「有且仅有一个」Finished/Failed 收尾，UI 才不会显示两次结束
        when (request.language) {
            Language.PYTHON -> {
                val bin = File(installer.prefixDir, "bin/python").absolutePath
                // -u：关闭 Python 的 stdout/stderr 缓冲。
                // 不传的话，输出会憋到进程结束才一次性刷出，实时性全无。
                val cmd = buildList {
                    add(bin); add("-u"); add(script.absolutePath); addAll(request.args)
                }
                send(RunEvent.Started(cmd.joinToString(" ")))
                val r = exec(cmd, workDir, env, timeoutMs)
                if (r.timedOut) send(RunEvent.Stderr("执行超时（${timeoutMs / 1000}s），进程已被终止。"))
                send(RunEvent.Finished(r.exitCode, System.currentTimeMillis() - started))
            }

            Language.JAVA -> {
                val projectRoot = File(request.workingDir).takeIf { it.isDirectory }
                    ?: script.parentFile ?: installer.homeDir

                // 收集项目内所有 Java 源文件（递归，跳过隐藏目录）
                val sources = projectRoot.walkTopDown()
                    .onEnter { !it.name.startsWith(".") }
                    .filter { it.isFile && it.extension == "java" }
                    .map { it.absolutePath }
                    .toList()
                if (sources.isEmpty()) {
                    send(RunEvent.Failed("项目内没有 .java 源文件"))
                    return@channelFlow
                }

                // 编译产物放 App 私有 tmp，避免污染项目目录
                val classesDir = File(installer.filesDir, "build/classes").apply {
                    deleteRecursively(); mkdirs()
                }
                val mainClass = request.mainClass?.takeIf { it.isNotBlank() }
                    ?: detectMainClass(sources)

                val javac = File(installer.prefixDir, "bin/javac").absolutePath
                val compileCmd = buildList {
                    add(javac); add("-encoding"); add("UTF-8")
                    add("-d"); add(classesDir.absolutePath)
                    addAll(sources)
                }
                send(RunEvent.Started(compileCmd.joinToString(" ")))
                val compiled = exec(compileCmd, workDir, env, timeoutMs)
                if (compiled.timedOut) {
                    send(RunEvent.Stderr("编译超时（${timeoutMs / 1000}s），已终止。"))
                    send(RunEvent.Finished(compiled.exitCode, System.currentTimeMillis() - started))
                    return@channelFlow
                }
                if (compiled.exitCode != 0) {
                    // 编译失败就把 javac 的原始报错留在输出里，不再尝试运行
                    send(RunEvent.Stderr("编译未通过，已中止运行。"))
                    send(RunEvent.Finished(compiled.exitCode, System.currentTimeMillis() - started))
                    return@channelFlow
                }

                val java = File(installer.prefixDir, "bin/java").absolutePath
                val runCmd = buildList {
                    add(java); add("-Dfile.encoding=UTF-8")
                    add("-cp"); add(classesDir.absolutePath)
                    add(mainClass); addAll(request.args)
                }
                send(RunEvent.Started(runCmd.joinToString(" ")))
                val r = exec(runCmd, workDir, env, timeoutMs)
                if (r.timedOut) send(RunEvent.Stderr("执行超时（${timeoutMs / 1000}s），进程已被终止。"))
                send(RunEvent.Finished(r.exitCode, System.currentTimeMillis() - started))
            }
        }
    }.flowOn(Dispatchers.IO)

    /** 一次子进程执行的结果 */
    private data class ExecResult(val exitCode: Int, val timedOut: Boolean)

    /**
     * 启动一个子进程并把输出转成事件；返回退出码与是否超时。
     *
     * 用轮询代替阻塞式 waitFor：协程被取消（如 ViewModel 销毁、用户退出）
     * 时能立刻感知并回收进程，避免留下孤儿进程持续占用 CPU 和内存。
     */
    private suspend fun ProducerScopeCompat.exec(
        cmd: List<String>,
        workDir: File,
        env: Map<String, String>,
        timeoutMs: Long
    ): ExecResult {
        val process = try {
            ProcessBuilder(cmd)
                .directory(workDir)
                .apply { environment().putAll(env) }
                .redirectErrorStream(false)
                .start()
        } catch (e: Exception) {
            send(RunEvent.Failed("启动进程失败：${e.message}"))
            return ExecResult(-1, false)
        }

        currentProcess = process
        val writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
        stdinWriter.set(writer)

        val started = System.currentTimeMillis()
        val deadline = started + timeoutMs
        var timedOut = false

        try {
            // stdout / stderr 并发读取，避免缓冲区满导致死锁
            val outJob = launch(Dispatchers.IO) {
                pumpLines(process.inputStream) { send(RunEvent.Stdout(it)) }
            }
            val errJob = launch(Dispatchers.IO) {
                pumpLines(process.errorStream) { send(RunEvent.Stderr(it)) }
            }
            var exit = -1
            while (true) {
                if (!process.isAlive) {
                    exit = runCatching { process.exitValue() }.getOrDefault(-1)
                    break
                }
                if (System.currentTimeMillis() >= deadline) {
                    timedOut = true
                    process.destroy()
                    process.destroyForcibly()
                    break
                }
                if (!coroutineContext.isActive) {
                    process.destroyForcibly()
                    break
                }
                delay(80)
            }
            outJob.join()
            errJob.join()
            return ExecResult(exit, timedOut)
        } finally {
            currentProcess = null
            stdinWriter.set(null)
            runCatching { writer.close() }
            if (process.isAlive) process.destroyForcibly()
        }
    }

    /**
     * 从源文件里找出主类全限定名。
     * 规则：先找含 `public static void main` 的文件，再读取其 package 声明。
     * 找不到则退化为第一个文件名（不带扩展名）。
     */
    private fun detectMainClass(sources: List<String>): String {
        val mainFile = sources.firstOrNull { path ->
            runCatching { File(path).readText().contains("static void main") }.getOrDefault(false)
        }
        val target = mainFile ?: sources.first()
        val file = File(target)
        val pkg = runCatching {
            Regex("""^\s*package\s+([\w.]+)\s*;""", RegexOption.MULTILINE)
                .find(file.readText())?.groupValues?.get(1)
        }.getOrNull()
        val simple = file.nameWithoutExtension
        return if (pkg.isNullOrBlank()) simple else "$pkg.$simple"
    }

    /** 挂起式逐行读取（在 launch 的 IO 协程内调用，逐行发事件） */
    private suspend fun pumpLines(
        stream: java.io.InputStream,
        onLine: suspend (String) -> Unit
    ) {
        try {
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    onLine(line)
                    line = reader.readLine()
                }
            }
        } catch (_: Exception) {
            // 进程被杀导致的流关闭可忽略
        }
    }
}

/**
 * [kotlinx.coroutines.channels.ProducerScope] 的别名，
 * 让 exec 能在 channelFlow 内部直接 send 事件。
 */
private typealias ProducerScopeCompat = kotlinx.coroutines.channels.ProducerScope<RunEvent>
