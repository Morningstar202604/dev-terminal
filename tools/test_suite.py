#!/usr/bin/env python3.11
"""
DevTerminal 端到端测试套件
=========================
一次性完成：启动本地静态服务器 → 驱动 Chromium（带全程录屏）→ 预热 Pyodide →
逐题打开「题库」真实题目并真实运行 → 等待成功/失败 → 抓取输出、截图、断言 →
产出结构化报告（JSON + Markdown）。

设计要点：
- 死循环题（lab_loop, timeout=3）放到最后执行：无 SharedArrayBuffer 时主线程看门狗会在
  ~15s 终止 worker，若它不在最后会污染后续题目（worker 需重新加载）。
- 每题之间必须等待上题进入「运行中」且到达终态，避免 _pyPending 被覆盖。
"""
import os, sys, json, time, threading, subprocess
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from playwright.sync_api import sync_playwright

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "design"))
PORT = 8190
OUT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "test_results"))
SHOT_DIR = os.path.join(OUT_DIR, "screenshots")
VIDEO_DIR = os.path.join(OUT_DIR, "recording")
os.makedirs(SHOT_DIR, exist_ok=True)
os.makedirs(VIDEO_DIR, exist_ok=True)

# 题库顺序：lab_loop 放到最后（死循环终止温热 worker）
LAB_ORDER = [
    "lab_sieve", "lab_fib", "lab_syntax", "lab_zerodiv", "lab_key",
    "lab_multi", "lab_long", "lab_fileio", "lab_unicode", "lab_quiz", "lab_loop",
]

# 每题主文件名（用于确定性等待「本次运行已启动」的输出标记）
FILENAME = {
    "lab_sieve": "sieve.py", "lab_fib": "fib_rec.py", "lab_syntax": "syntax_err.py",
    "lab_zerodiv": "zerodiv.py", "lab_key": "keyerr.py", "lab_multi": "app.py",
    "lab_long": "long_out.py", "lab_loop": "infinite.py", "lab_fileio": "fileio.py",
    "lab_unicode": "unicode.py", "lab_quiz": "quiz.py",
}

# 每题预期：[badge 终态, [必须出现在输出里的子串], [必须不存在的子串(乱码/异常)]]
EXPECT = {
    "lab_sieve":   ("成功", ["1..1000 内的素数共 168 个", "前 20 个:", "[2, 3, 5, 7, 11"], []),
    "lab_fib":     ("失败", ["RecursionError", "maximum recursion depth", "fib_rec.py"], []),
    "lab_syntax":  ("失败", ["SyntaxError"], []),
    "lab_zerodiv": ("失败", ["ZeroDivisionError", "10 / 10 = 1", "10 / 4 = 2"], ["�"]),
    "lab_key":     ("失败", ["KeyError", "'age'"], []),
    "lab_multi":   ("失败", ["hello, DevTerminal", "ZeroDivisionError", "mymath.py"], []),
    "lab_long":    ("成功", ["-- 共输出 5000 行 --", "0000: DevTerminal"], ["�"]),
    "lab_fileio":  ("成功", ["读到: DevTerminal 离线文件 IO 测试"], []),
    "lab_unicode": ("成功", ["你好，世界 🌍", "∑ ∫ √ π", "🚀🔥✅"], ["�", "Ã", "Â"]),
    "lab_quiz":    ("成功", ["你好，小明！代码已在离线环境运行。", "再过 10 年你就 28 岁了"], ["�"]),
    "lab_loop":    ("失败", [], []),  # 死循环：必为失败且不能卡死（由等待超时判定）
}


class Handler(SimpleHTTPRequestHandler):
    extensions_map = {
        **SimpleHTTPRequestHandler.extensions_map,
        ".mjs": "text/javascript", ".js": "text/javascript",
        ".wasm": "application/wasm", ".json": "application/json",
    }
    def log_message(self, *a):
        pass


def start_server():
    os.chdir(ROOT)
    httpd = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    t = threading.Thread(target=httpd.serve_forever, daemon=True)
    t.start()
    return httpd


def wait_output(page, needle, timeout):
    """等待输出面板出现指定文本（realRun 每次会 clearOutput，故标记无歧义）。"""
    page.wait_for_function(
        "(n) => document.getElementById('outBody').innerText.includes(n)",
        arg=needle, timeout=timeout,
    )


def main():
    httpd = start_server()
    url = f"http://127.0.0.1:{PORT}/mockup.html"
    results = []
    t0 = time.time()

    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            args=["--no-sandbox", "--disable-dev-shm-usage"],
        )
        ctx = browser.new_context(
            viewport={"width": 430, "height": 880},
            device_scale_factor=2,
            record_video_dir=VIDEO_DIR,
        )
        page = ctx.new_page()
        page.goto(url, wait_until="load")
        print(f"[{time.time()-t0:.1f}s] 页面已加载，worker 预热中…")

        # 脚本整体包在 IIFE 里，函数不挂在 window 上；因此通过真实 DOM 点击驱动。
        # worker 在页面加载时已自动预热（mockup 末尾 getPyWorker()）。
        time.sleep(1.5)

        # 首页截图
        page.screenshot(path=os.path.join(SHOT_DIR, "00_home.png"))
        # 打开题库面板截图
        page.evaluate("document.getElementById('btnLab').click()")
        time.sleep(0.6)
        page.screenshot(path=os.path.join(SHOT_DIR, "01_lab_panel.png"))
        page.evaluate("document.getElementById('labSheet').classList.remove('open')")

        for idx, key in enumerate(LAB_ORDER):
            exp_badge, must, must_not = EXPECT[key]
            shot = os.path.join(SHOT_DIR, f"{idx+2:02d}_{key}.png")
            rec = {"key": key, "expected_badge": exp_badge, "ok": False, "notes": []}
            try:
                # 通过真实 UI 交互：打开题库 → 点击对应题卡（其监听器调用 openProgram 并自动运行）
                page.evaluate("document.getElementById('btnLab').click()")
                page.wait_for_timeout(250)
                page.evaluate(
                    f"document.querySelector(\"#labList .lab-item[data-key='{key}']\").click()"
                )
                # 阶段1：等待本次运行的启动标记（realRun 会 clearOutput 后写入 run 命令行）
                wait_output(page, "devterminal run " + FILENAME[key], 30000)
                t_run = time.time()
                # 阶段2：等待终态标记。正常题为「退出码」；死循环题无 done 回调，
                # 由主线程看门狗写入「运行超时」标记。
                end_marker = "运行超时" if key == "lab_loop" else "退出码"
                wait_output(page, end_marker, 25000 if key == "lab_loop" else 120000)
                rec["run_seconds"] = round(time.time() - t_run, 1)
                badge = page.evaluate("document.getElementById('badgeText').textContent")
                out = page.evaluate("document.getElementById('outBody').innerText")
                jump = page.evaluate("document.querySelectorAll('#outBody .l.jump').length")
                rec["badge"] = badge
                rec["output_len"] = len(out)
                rec["jump_lines"] = jump
                rec["output_excerpt"] = out[:400]

                ok = (badge == exp_badge)
                for s in must:
                    if s not in out:
                        ok = False
                        rec["notes"].append(f"缺少预期输出: {s!r}")
                for s in must_not:
                    if s in out:
                        ok = False
                        rec["notes"].append(f"出现乱码/异常: {s!r}")
                # 报错题应至少有一条可点击跳转行
                if exp_badge == "失败" and key not in ("lab_loop",):
                    if jump < 1:
                        ok = False
                        rec["notes"].append("报错但无可点击跳转行（parseErrorLine 可能失效）")
                rec["ok"] = ok
                page.screenshot(path=shot)
                print(f"[{time.time()-t0:.1f}s] {key}: badge={badge} jumps={jump} -> {'PASS' if ok else 'FAIL'} "
                      + (" ".join(rec['notes']) if rec['notes'] else ""))
            except Exception as e:
                rec["error"] = str(e)
                try:
                    page.screenshot(path=shot)
                except Exception:
                    pass
                print(f"[{time.time()-t0:.1f}s] {key}: EXCEPTION {e}")
            results.append(rec)
            time.sleep(0.5)

        # 收尾：再截一张运行结束态
        page.screenshot(path=os.path.join(SHOT_DIR, f"{len(LAB_ORDER)+2:02d}_final.png"))
        ctx.close()
        browser.close()

    httpd.shutdown()
    total = time.time() - t0
    passed = sum(1 for r in results if r.get("ok"))
    report = {
        "url": url, "total_seconds": round(total, 1),
        "passed": passed, "total": len(results),
        "results": results,
    }
    with open(os.path.join(OUT_DIR, "report.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    # Markdown 报告
    md = ["# DevTerminal 端到端测试报告", "",
          f"- 总耗时：**{report['total_seconds']}s**",
          f"- 通过：**{passed}/{len(results)}**", ""]
    md.append("| # | 题目 | 终态 | 预期 | 可跳转 | 耗时 | 结论 | 备注 |")
    md.append("|---|------|------|------|--------|------|------|------|")
    for i, r in enumerate(results):
        md.append(f"| {i+1} | {r['key']} | {r.get('badge','-')} | {r['expected_badge']} | "
                  f"{r.get('jump_lines','-')} | {r.get('run_seconds','-')}s | "
                  f"{'✅' if r.get('ok') else '❌'} | {'; '.join(r.get('notes',[])) or '-'} |")
    with open(os.path.join(OUT_DIR, "report.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(md) + "\n")
    print(f"\n===== 测试完成：{passed}/{len(results)} 通过，耗时 {total:.1f}s =====")
    print(f"报告: {os.path.join(OUT_DIR,'report.md')}")


if __name__ == "__main__":
    main()
