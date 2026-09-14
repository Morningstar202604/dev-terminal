package com.devterminal.engine

import java.io.File

/**
 * 快速语法自检（不依赖 Android，可在纯 JVM 里跑）。
 * 用于开发期验证：离线 prefix 下目标文件是否可被解释器识别。
 */
object SyntaxCheck {

    /** 简易检查：Python 文件首行是否可读、非空 */
    fun looksLikePython(file: File): Boolean =
        file.exists() && file.extension == "py" && file.readText().isNotBlank()

    /** 生成 Python 单行自检命令（供引擎冒烟测试用） */
    fun pythonSmokeCommand(prefixBin: String): List<String> =
        listOf("$prefixBin/python", "-c", "print('devterminal-ok')")
}
