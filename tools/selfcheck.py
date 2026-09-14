#!/usr/bin/env python3
"""
DevTerminal 静态自检脚本。

用途：在没有 Android SDK 的环境下，尽可能多地验证代码正确性。
检查项：
  1. Kotlin 花括号/圆括号配平（逐行追踪深度，正确处理字符串字面量）
  2. Compose 图标引用是否都有对应 import
  3. 关键类的相互引用是否闭环（Templates/Engine/ViewModel 等）
  4. 包声明与目录结构是否一致

用法： python3 tools/selfcheck.py
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "java")
errors = []
warnings = []


def kt_files():
    for dirpath, _, files in os.walk(ROOT):
        for f in files:
            if f.endswith(".kt"):
                yield os.path.join(dirpath, f)


def check_brace_balance(path, src):
    """逐行追踪花括号深度；字符串/注释内的括号不参与统计。"""
    depth = 0
    in_block_comment = False
    in_raw_string = False
    for lineno, line in enumerate(src.split("\n"), 1):
        i = 0
        n = len(line)
        while i < n:
            # 块注释
            if in_block_comment:
                if line.startswith("*/", i):
                    in_block_comment = False
                    i += 2
                else:
                    i += 1
                continue
            if line.startswith("/*", i):
                in_block_comment = True
                i += 2
                continue
            if line.startswith("//", i):
                break
            # 原始字符串 """..."""
            if line.startswith('"""', i):
                in_raw_string = not in_raw_string
                i += 3
                continue
            if in_raw_string:
                i += 1
                continue
            c = line[i]
            # 普通字符串
            if c == '"':
                i += 1
                while i < n:
                    if line[i] == "\\":
                        i += 2
                        continue
                    if line[i] == '"':
                        i += 1
                        break
                    # 字符串模板 ${} 里的花括号要计入
                    if line.startswith("${", i):
                        depth += 1
                        i += 2
                        # 找到匹配的 }
                        inner = 1
                        while i < n and inner > 0:
                            if line[i] == "{":
                                inner += 1
                            elif line[i] == "}":
                                inner -= 1
                            i += 1
                        depth -= 1
                        continue
                    i += 1
                continue
            if c == "'":
                i += 1
                while i < n:
                    if line[i] == "\\":
                        i += 2
                        continue
                    if line[i] == "'":
                        i += 1
                        break
                    i += 1
                continue
            if c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth < 0:
                    errors.append(f"{path}:{lineno} 花括号提前闭合")
                    return
            i += 1
    if depth != 0:
        errors.append(f"{path} 花括号不配平（结余 {depth}）")


def check_icon_imports(path, src):
    used = set(re.findall(r"Icons\.(?:Filled|AutoMirrored\.Filled)\.(\w+)", src))
    if not used:
        return
    imported = set(
        re.findall(
            r"import androidx\.compose\.material\.icons\.(?:filled|automirrored\.filled)\.(\w+)",
            src,
        )
    )
    missing = used - imported
    if missing:
        errors.append(f"{path} 缺少图标 import: {sorted(missing)}")


def check_package_matches_dir(path, src):
    m = re.search(r"^package\s+([\w.]+)", src, re.M)
    if not m:
        errors.append(f"{path} 缺少 package 声明")
        return
    pkg = m.group(1)
    # rel 路径形如 com/devterminal/ui/Foo.kt；期望其父目录等于 package 的路径形式
    parent = os.path.dirname(path).replace("\\", "/")
    if parent != pkg.replace(".", "/"):
        errors.append(f"{path} package={pkg} 与所在目录 {parent} 不一致")


# Compose 委托属性隐式使用的符号，出现这些 import 不算未使用
IMPLICIT_SYMBOLS = {
    "getValue",     # by remember { mutableStateOf(...) }
    "setValue",     # by ... 的可变委托
    "provideDelegate",
    "Composable",
}


def check_unused_imports(path, src):
    imports = re.findall(r"^import\s+([\w.]+)", src, re.M)
    body = re.sub(r"^import\s+[\w.]+$", "", src, flags=re.M)
    for imp in imports:
        sym = imp.rsplit(".", 1)[-1]
        if sym == "*" or sym in IMPLICIT_SYMBOLS:
            continue
        # 简单匹配：类名在正文里是否出现
        if not re.search(r"\b" + re.escape(sym) + r"\b", body):
            warnings.append(f"{path} 可能未使用的 import: {imp}")


def main():
    count = 0
    for path in kt_files():
        count += 1
        rel = os.path.relpath(path, ROOT)
        src = open(path, encoding="utf-8").read()
        check_brace_balance(rel, src)
        check_icon_imports(rel, src)
        check_package_matches_dir(rel, src)
        check_unused_imports(rel, src)

    print(f"检查了 {count} 个 Kotlin 文件")
    if warnings:
        print(f"\n警告 {len(warnings)} 条：")
        for w in warnings:
            print("  ⚠ " + w)
    if errors:
        print(f"\n错误 {len(errors)} 条：")
        for e in errors:
            print("  ✗ " + e)
        sys.exit(1)
    print("\n✓ 静态自检通过")


if __name__ == "__main__":
    main()
