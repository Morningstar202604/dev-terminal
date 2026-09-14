package com.devterminal.engine

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃记录。
 *
 * 动机：这是要在手机上离线使用的工具类 App，用户崩了之后**没法连电脑看 logcat**。
 * 把堆栈写到 App 私有目录，用户可在「关于」页复制出来反馈，排查成本大幅降低。
 */
object CrashLogger {

    private const val FILE_NAME = "crash.log"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(appContext, thread, throwable) }
            // 交回系统默认处理（走正常崩溃流程），避免吞掉异常
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = buildString {
            append("===== $time =====\n")
            append("Thread: ${thread.name}\n")
            append(sw.toString())
            append("\n\n")
        }
        File(context.filesDir, FILE_NAME).appendText(entry)
    }

    /** 读取已记录的崩溃日志（供「关于」页展示/复制） */
    fun read(context: Context): String {
        val f = File(context.filesDir, FILE_NAME)
        return if (f.exists()) runCatching { f.readText() }.getOrDefault("") else ""
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}
