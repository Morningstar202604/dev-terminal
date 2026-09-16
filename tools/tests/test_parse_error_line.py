#!/usr/bin/env python3
"""
报错行解析逻辑的回归测试。

对应实现：app/src/main/java/com/devterminal/ui/OutputPanel.kt 的 parseErrorLine()

为什么单独测这个：它是「运行 → 看报错 → 跳过去改」这条链路唯一的解析环节，
正则写松了会把版本号、时间戳误认成行号（用户点了跳到莫名其妙的行），
写紧了又会让真实的报错行点不动。两种错都不会让编译失败，只会在真机上
表现为「有时候能跳有时候不能」，所以用测试锁住。

用 Python 复刻 Kotlin 的实现而非跑 Kotlin 测试，是为了不引入
额外的测试框架依赖（本沙箱 Google 系域名不可达，拉测试库成本高）。
两边语义一致，改动 Kotlin 侧时同步这里即可。

用法：python3 tools/tests/test_parse_error_line.py
"""
import re
import sys

# 与 OutputPanel.kt 保持一致
PY_TRACE = re.compile(r"line\s+(\d+)")
PATH_LINE = re.compile(r"[\w./\\-]+\.\w{1,6}:(\d+)")

# 不参与跳转的前缀（友好提示、分隔线）
SKIP_PREFIXES = ("💡", "——")


def parse_error_line(line: str):
    """从一行输出里解析出 0 基行号，解析不到返回 None。"""
    if line.startswith(SKIP_PREFIXES):
        return None
    m = PY_TRACE.search(line)
    if m:
        return max(int(m.group(1)) - 1, 0)
    m = PATH_LINE.search(line)
    if m:
        return max(int(m.group(1)) - 1, 0)
    return None


CASES = [
    # --- 应该能解析出行号 ---
    ('  File "main.py", line 12, in <module>', 11, "Python traceback"),
    ('  File "main.py", line 1, in <module>', 0, "第 1 行 → 0 基"),
    ("Main.java:7: error: ';' expected", 6, "javac 报错"),
    ("main.py:25:5: E501 line too long", 24, "flake8 风格"),
    ("./src/utils.py:100", 99, "带 ./ 前缀"),
    ("/data/data/com.devterminal/files/projects/demo/main.py:42", 41, "绝对路径"),
    ("  File 'C:/work/demo/app.py', line 3", 2, "Windows 风格路径"),

    # --- 不应该解析出行号（误报） ---
    ("[err] Traceback (most recent call last):", None, "无行号"),
    ("💡 看起来是缩进问题，Python 对空格敏感", None, "友好提示行"),
    ("—— 运行结束，退出码 1 ——", None, "分隔线（含数字）"),
    ("Hello World", None, "普通输出"),
    ("", None, "空行"),
    ("version 1.2.3 released", None, "版本号不能被当成行号"),
    ("2026-09-16T12:30:45 INFO started", None, "时间戳不能被当成行号"),
    ("[DevTerminal] python3 main.py", None, "命令行回显"),
]


def main() -> int:
    failed = 0
    for line, expect, desc in CASES:
        got = parse_error_line(line)
        if got != expect:
            failed += 1
            print(f"✗ {desc}: {line!r}\n    得到 {got}, 期望 {expect}")
        else:
            print(f"✓ {desc}")
    total = len(CASES)
    print(f"\n{total - failed}/{total} 通过")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
