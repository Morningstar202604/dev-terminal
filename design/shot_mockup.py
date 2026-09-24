#!/usr/bin/env python3
"""渲染 mockup.html 并截图 .device 元素（用作官网/宣传素材）
用法：python3 shot_mockup.py <state> <out.png>
state: idle | running | error | drawer
"""
import sys
from playwright.sync_api import sync_playwright

CHROME = "/opt/vm/preinstall/ms-playwright/chromium-1169/chrome-linux/chrome"
URL = "file:///home/user/Doubao/chats/38444027447280130/dev-terminal/design/mockup.html"

state = sys.argv[1] if len(sys.argv) > 1 else "idle"
out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/mockup.png"

with sync_playwright() as p:
    b = p.chromium.launch(executable_path=CHROME)
    page = b.new_page(viewport={"width": 900, "height": 1200})
    page.goto(URL, wait_until="networkidle")
    page.wait_for_timeout(400)

    if state == "running":
        page.click("#btnRun")
        page.wait_for_timeout(1500)  # 等输出流式渲染
    elif state == "error":
        # utils.py retry 脚本：第一次正常运行，第二次演示「写错代码 → RecursionError → 点报错行跳回」
        page.click('[data-tab="utils.py"]')
        page.wait_for_timeout(400)
        page.click("#btnRun")
        page.wait_for_timeout(1600)
        page.click("#btnRun")
        page.wait_for_timeout(2200)  # retry 脚本完整播完（含报错行 + 跳转提示）
    elif state == "drawer":
        page.click("#btnMenu")
        page.wait_for_timeout(500)

    dev = page.query_selector(".device")
    assert dev, "device element not found"
    dev.screenshot(path=out)
    print("saved", out)
    b.close()
