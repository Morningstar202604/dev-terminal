package com.devterminal.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 离线执行引擎。
 *
 * 设计取舍：
 *  - 不自造终端模拟器（那是 termux 十年工程），改用 ProcessBuilder 直接起进程，
 *    这是「完全离线、无按需下载」场景下最稳的做法。
 *  - 环境变量完全模拟 termux 布局（见 [EnvironmentInstaller.buildEnv]），
 *    因此内置的 termux 预编译二进制可以在 bionic 上正常跑。
 *  - 输出以 [RunEvent] 流式回调，UI 可实时显示；stdin 双向可写，支持 input() 交互。
 *  - Java 走「递归编译整包 → 指定主类运行」流程；Python 支持同目录模块导入。
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
        currentProcess?.let {
            it.destroy()
            Thread {
                try {
                    if (!it.waitFor(3, TimeUnit.SECONDS)) it.destroyForcibly()
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true; start() }
        }
    }

    /**
     * 向运行中的进程写入一行输入（模拟用户敲回车）。
     * 供 Python 的 input() 等交互场景使用。
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
        runCatching { stdinWriter.get()?.close() }
    }

    /** 触发首次安装（解压离线工具链），阻塞式，请在 IO 线程调用 */
    fun prepareEnvironment(onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        installer.installIfNeeded(onProgress)
    }

    /**
     * 执行，返回事件流。内部带超时保护，防止死循环把手机卡死。
     */
    fun run(request: RunRequest, timeoutMs: Long = 120_000L): Flow<RunEvent> = flow {
        if (!isEnvironmentReady) {
            emit(RunEvent.Failed("离线运行环境未就绪：缺少内置工具链（assets/usrtar.zip）。"))
            return@flow
        }
        val script = File(request.scriptPath)
        if (!script.exists()) {
            emit(RunEvent.Failed("文件不存在：${request.scriptPath}"))
            return@flow
        }

        val cmd = try {
            buildCommand(request, script)
        } catch (e: Exception) {
            emit(RunEvent.Failed(e.message ?: "构建命令失败"))
            return@flow
        }

        val env = EnvironmentInstaller.buildEnv(installer.filesDir)
        val workDir = File(request.workingDir).takeIf { it.isDirectory }
            ?: script.parentFile ?: installer.homeDir

        emit(RunEvent.Started(cmd.joinToString(" ")))

        val process = try {
            ProcessBuilder(cmd)
                .directory(workDir)
                .apply { environment().putAll(env) }
                .redirectErrorStream(false)
                .start()
        } catch (e: Exception) {
            emit(RunEvent.Failed("启动进程失败：${e.message}"))
            return@flow
        }

        currentProcess = process
        // 建立 stdin 写入通道
        val writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
        stdinWriter.set(writer)

        val started = System.currentTimeMillis()
        val timedOut = AtomicBoolean(false)
        val watchdog = Thread {
            try {
                Thread.sleep(timeoutMs)
                timedOut.set(true)
                process.destroy()
                process.destroyForcibly()
            } catch (_: InterruptedException) {
            }
        }.apply { isDaemon = true; start() }

        try {
            // stdout / stderr 并发读取，避免缓冲区满导致死锁
            val outReader = pump(process.inputStream) { emit(RunEvent.Stdout(it)) }
            val errReader = pump(process.errorStream) { emit(RunEvent.Stderr(it)) }
            val exit = process.waitFor()
            outReader.join(1000); errReader.join(1000)
            if (timedOut.get()) {
                emit(RunEvent.Stderr("执行超时（${timeoutMs / 1000}s），进程已被终止。"))
            }
            emit(RunEvent.Finished(exit, System.currentTimeMillis() - started))
        } finally {
            currentProcess = null
            stdinWriter.set(null)
            runCatching { writer.close() }
            watchdog.interrupt()
            if (process.isAlive) process.destroyForcibly()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 组装执行命令。
     *
     * Python：直接调用解释器；由于工作目录设为项目根，同目录的 `import utils` 可用。
     * Java：先把项目内所有 .java 递归编译到临时 classes 目录，
     *       再以该目录为 classpath 运行主类（按 @param request.mainClass 或自动探测）。
     */
    private fun buildCommand(request: RunRequest, script: File): List<String> {
        val bin = File(installer.prefixDir, "bin").absolutePath
        return when (request.language) {
            Language.PYTHON -> buildList {
                // -u：关闭 Python 的 stdout/stderr 缓冲。
                // 不传的话，输出会憋到进程结束才一次性刷出，实时性全无。
                add("$bin/python")
                add("-u")
                add(script.absolutePath)
                addAll(request.args)
            }

            Language.JAVA -> {
                val projectRoot = File(request.workingDir).takeIf { it.isDirectory }
                    ?: script.parentFile ?: installer.homeDir

                // 收集项目内所有 Java 源文件（递归）
                val sources = projectRoot.walkTopDown()
                    .filter { it.isFile && it.extension == "java" }
                    .map { it.absolutePath }
                    .toList()
                if (sources.isEmpty()) throw IllegalStateException("项目内没有 .java 源文件")

                // 编译产物放 App 私有 tmp，避免污染项目目录
                val classesDir = File(installer.filesDir, "build/classes").apply {
                    deleteRecursively(); mkdirs()
                }

                // 主类：优先用显式指定；否则从含 main 方法的文件推断（含 package 声明）
                val mainClass = request.mainClass?.takeIf { it.isNotBlank() }
                    ?: detectMainClass(sources)

                val srcList = sources.joinToString(" ") { "'$it'" }
                val args = request.args.joinToString(" ")
                val script0 = buildString {
                    append("set -e; ")
                    append("'$bin/javac' -encoding UTF-8 -d '${classesDir.absolutePath}' $srcList; ")
                    append("'$bin/java' -Dfile.encoding=UTF-8 ")
                    append("-cp '${classesDir.absolutePath}' ")
                    append("'$mainClass' $args")
                }
                listOf("/system/bin/sh", "-c", script0)
            }
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

    /** 逐行读取输入流并在 IO 线程回调 */
    private fun pump(
        stream: java.io.InputStream,
        onLine: (String) -> Unit
    ): Thread = Thread {
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
    }.apply { isDaemon = true; start() }
}
