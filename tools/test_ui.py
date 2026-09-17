#!/usr/bin/env python3.11
"""
DevTerminal UI 覆盖面测试 + 宣传片素材采集
=========================================
除「题库」真实题目外，再遍历其余界面能力，确保「全部测一遍」：
- 抽屉文件列表 / 切换文件
- 猜数字小游戏（canned 演示，真实交互输入）
- 报错行点击跳转（点击 traceback 可点行 → 编辑器滚动并高亮）
- Git 面板 / AI 面板 / 命令面板 / 全局搜索
- 编辑器长文件滚动高亮
全程录屏 + 每步截图，供宣传片剪辑。
"""
import os, sys, time, threading, json
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from playwright.sync_api import sync_playwright

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "design"))
PORT = 8192
OUT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "test_results"))
SHOT = os.path.join(OUT, "ui_shots")
VID = os.path.join(OUT, "ui_recording")
os.makedirs(SHOT, exist_ok=True)
os.makedirs(VID, exist_ok=True)

class H(SimpleHTTPRequestHandler):
    extensions_map = {**SimpleHTTPRequestHandler.extensions_map, ".mjs": "text/javascript", ".wasm": "application/wasm"}
    def log_message(self, *a): pass

def main():
    os.chdir(ROOT)
    srv = ThreadingHTTPServer(("127.0.0.1", PORT), H)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    url = f"http://127.0.0.1:{PORT}/mockup.html"
    steps = []
    t0 = time.time()

    def snap(name):
        p = os.path.join(SHOT, name)
        page.screenshot(path=p)
        steps.append(p)

    with sync_playwright() as pw:
        b = pw.chromium.launch(headless=True, args=["--no-sandbox", "--disable-dev-shm-usage"])
        ctx = b.new_context(viewport={"width": 430, "height": 880}, device_scale_factor=2, record_video_dir=VID)
        page = ctx.new_page()
        page.goto(url, wait_until="load")
        time.sleep(1.5)
        snap("ui_00_home.png")

        def click_id(i):
            page.evaluate(f"document.getElementById('{i}').click()")
            time.sleep(0.7)

        # 1) 抽屉：文件列表
        click_id("btnMenu"); snap("ui_01_drawer.png")
        # 切到 main.py（猜数字小游戏）
        page.evaluate("document.querySelector('.row[data-file=\"main.py\"]').click()")
        time.sleep(0.7); snap("ui_02_mainpy.png")
        # 运行 canned 演示
        click_id("btnRun")
        time.sleep(2.5); snap("ui_03_guess_running.png")
        time.sleep(1.5); snap("ui_04_guess_done.png")
        # 切到 utils.py
        page.evaluate("document.getElementById('btnMenu').click()")
        time.sleep(0.4)
        page.evaluate("document.querySelector('.row[data-file=\"utils.py\"]').click()")
        time.sleep(0.7); snap("ui_05_utilspy.png")

        # 2) 报错行点击跳转（headline feature）：跑 fib 错误，点击 traceback 可点行
        page.evaluate("document.getElementById('btnLab').click()"); time.sleep(0.3)
        page.evaluate("document.querySelector(\"#labList .lab-item[data-key='lab_fib']\").click()")
        # 等待运行结束
        page.wait_for_function(
            "document.getElementById('outBody').innerText.includes('退出码')", timeout=60000)
        time.sleep(0.6); snap("ui_06_fib_error.png")
        # 点击第一条可跳转报错行
        jumped = page.evaluate(
            "() => { const j=document.querySelector('#outBody .l.jump'); if(j){ j.click(); return true;} return false; }")
        time.sleep(0.9); snap("ui_07_error_jump.png")
        steps.append("error_jump_clicked=" + str(jumped))

        # 3) Git 面板
        page.evaluate("document.getElementById('labSheet').classList.remove('open')")
        click_id("btnGit"); snap("ui_08_git.png")
        # 4) AI 面板
        page.evaluate("document.getElementById('gitSheet').classList.remove('open')")
        click_id("btnAi"); snap("ui_09_ai.png")
        # 5) 命令面板
        page.evaluate("document.getElementById('aiSheet').classList.remove('open')")
        click_id("btnCmd"); snap("ui_10_cmdk.png")
        # 6) 全局搜索
        page.evaluate("document.getElementById('cmdk').classList.remove('open')")
        click_id("btnGsearch"); snap("ui_11_search.png")
        page.evaluate("document.getElementById('gsSheet').classList.remove('open')")
        # 7) 拉回题库看一个成功题（素数筛）
        page.evaluate("document.getElementById('btnLab').click()"); time.sleep(0.3)
        page.evaluate("document.querySelector(\"#labList .lab-item[data-key='lab_sieve']\").click()")
        page.wait_for_function(
            "document.getElementById('outBody').innerText.includes('退出码')", timeout=60000)
        time.sleep(0.8); snap("ui_12_sieve_ok.png")

        ctx.close(); b.close()
    srv.shutdown()
    with open(os.path.join(OUT, "ui_steps.json"), "w", encoding="utf-8") as f:
        json.dump({"seconds": round(time.time()-t0, 1), "steps": steps}, f, ensure_ascii=False, indent=2)
    print("UI 覆盖完成，用时 %.1fs，截图 %d 张" % (time.time()-t0, len(steps)))
    print("\n".join(steps))

if __name__ == "__main__":
    main()
