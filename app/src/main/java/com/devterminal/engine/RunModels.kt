package com.devterminal.engine

/** 支持的运行语言 */
enum class Language(
    val displayName: String,
    val fileExtension: String,
    /** 用于在 assets 工具链里定位解释器/编译器 */
    val runtimeBinary: String
) {
    PYTHON("Python", "py", "python"),
    JAVA("Java", "java", "javac")
}

/** 一次运行的请求参数 */
data class RunRequest(
    val scriptPath: String,
    val workingDir: String,
    val args: List<String> = emptyList(),
    val language: Language = Language.PYTHON,
    /** Java 专用：主类全限定名；为空时引擎自动探测含 main 方法的类 */
    val mainClass: String? = null
)

/** 运行过程中流出的事件（用于实时刷新 UI） */
sealed interface RunEvent {
    /** 进程已启动，附带实际执行的命令（便于排查） */
    data class Started(val command: String) : RunEvent
    /** 标准输出的一行 */
    data class Stdout(val line: String) : RunEvent
    /** 标准错误的一行 */
    data class Stderr(val line: String) : RunEvent
    /** 引擎侧静音日志（如首次预热提示），非程序输出，UI 应以灰色小字展示 */
    data class Log(val message: String) : RunEvent
    /** 进程结束 */
    data class Finished(val exitCode: Int, val durationMs: Long) : RunEvent
    /** 运行前发生的错误（例如工具链未安装、文件不存在、编译失败） */
    data class Failed(val message: String) : RunEvent
}

/** 运行最终结果（给非流式场景用） */
data class RunResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long
)
