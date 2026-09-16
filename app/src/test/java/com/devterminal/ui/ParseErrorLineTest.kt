package com.devterminal.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 报错行解析逻辑的单元测试。
 *
 * 对应实现：OutputPanel.kt 的 parseErrorLine()。
 * 用例与 tools/tests/test_parse_error_line.py 保持同一条目——那份是
 * 无构建环境下的快速回归工具，这份是 CI / 本地 `gradlew test` 跑的正式版本。
 * 改解析逻辑时两边同步增删。
 *
 * 为什么单独测这个：它是「运行 → 看报错 → 跳过去改」这条链路唯一的解析环节，
 * 正则写松了会把版本号、时间戳误认成行号（用户点了跳到莫名其妙的行），
 * 写紧了又会让真实的报错行点不动。两种错都不会让编译失败，只会在真机上
 * 表现为「有时候能跳有时候不能」，所以用测试锁住。
 */
class ParseErrorLineTest {

    private fun assertLine(line: String, expected: Int?) =
        assertEquals(expected, parseErrorLine(line))

    // ---------- 应该能解析出行号 ----------

    @Test
    fun pythonTraceback() =
        assertLine("""  File "main.py", line 12, in <module>""", 11)

    @Test
    fun firstLineBecomesZeroBased() =
        assertLine("""  File "main.py", line 1, in <module>""", 0)

    @Test
    fun javacError() =
        assertLine("Main.java:7: error: ';' expected", 6)

    @Test
    fun flake8Style() =
        assertLine("main.py:25:5: E501 line too long", 24)

    @Test
    fun dotSlashPrefix() =
        assertLine("./src/utils.py:100", 99)

    @Test
    fun absolutePath() =
        assertLine("/data/data/com.devterminal/files/projects/demo/main.py:42", 41)

    @Test
    fun windowsStylePath() =
        assertLine("""  File 'C:/work/demo/app.py', line 3""", 2)

    // ---------- 不应该解析出行号（误报） ----------

    @Test
    fun tracebackHeaderWithoutLine() =
        assertLine("[err] Traceback (most recent call last):", null)

    @Test
    fun friendlyTipIsSkipped() =
        assertLine("💡 看起来是缩进问题，Python 对空格敏感", null)

    @Test
    fun dividerWithNumberIsSkipped() =
        assertLine("—— 运行结束，退出码 1 ——", null)

    @Test
    fun plainOutput() =
        assertLine("Hello World", null)

    @Test
    fun emptyLine() =
        assertLine("", null)

    @Test
    fun versionNumberIsNotALine() =
        assertLine("version 1.2.3 released", null)

    @Test
    fun timestampIsNotALine() =
        assertLine("2026-09-16T12:30:45 INFO started", null)

    @Test
    fun commandEchoIsNotALine() =
        assertLine("[DevTerminal] python3 main.py", null)

    // ---------- Kotlin 实现特有的边界 ----------

    @Test
    fun negativeLineCoercedToZero() {
        // line 0 在源文件里不存在，但解析层只保证不返回负数，
        // 真正的边界（行号超出文件）由编辑器 setSelection 自己夹。
        assertLine("""  File "main.py", line 0""", 0)
    }

    @Test
    fun hugeLineNumberDoesNotCrash() {
        // 超出 Int 范围的行号：toIntOrNull() 返回 null，整行解析为 null——
        // 只要求不抛异常（Kotlin 的 Int 溢出会让崩溃藏进真实用户的报错里）
        assertEquals(null, parseErrorLine("""  File "main.py", line 999999999999"""))
    }
}
