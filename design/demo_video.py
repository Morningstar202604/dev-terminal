#!/usr/bin/env python3
"""DevTerminal 宣传视频生成：驱动 mockup.html 交互动画，连拍帧 + ffmpeg 合成。
剧情（~28s @ 24fps）：
  编辑 main.py → 运行 → 猜数字交互（50/75/63）→ 切 utils.py → 运行出错 →
  点报错行跳转 → 打开文件抽屉 → 打开命令面板 → 回到 main.py
"""
import os, subprocess, sys
from playwright.sync_api import sync_playwright

CHROME = "/opt/vm/preinstall/ms-playwright/chromium-1169/chrome-linux/chrome"
URL = "file:///home/user/Doubao/chats/38444027447280130/dev-terminal/design/mockup.html"
FPS = 24
OUT_DIR = sys.argv[1] if len(sys.argv) > 1 else "/tmp/video_frames"
os.makedirs(OUT_DIR, exist_ok=True)

def wait(page, seconds):
    page.wait_for_timeout(int(seconds * 1000))

FRAME = [0]
def shot(page, t):
    dev = page.query_selector(".device")
    assert dev, "device element not found"
    dev.screenshot(path=f"{OUT_DIR}/f{FRAME[0]:04d}.png")
    FRAME[0] += 1
    page.wait_for_timeout(int(1000 / FPS))

def click(page, sel):
    page.click(sel, timeout=3000)
    page.wait_for_timeout(120)  # 让按钮按压态/涟漪稍作停留

def send_input(page, value):
    page.fill("#stdin", value)
    wait(page, 0.35)           # 显示输入内容
    page.click("#btnSend")
    wait(page, 0.6)            # 输出反馈

with sync_playwright() as p:
    b = p.chromium.launch(executable_path=CHROME)
    page = b.new_page(viewport={"width": 900, "height": 1200}, device_scale_factor=2)
    page.goto(URL, wait_until="domcontentloaded")
    wait(page, 0.8)

    t = 0.0
    # ---- 开场：main.py 编辑态 ----
    for _ in range(72):  # 3s 开场
        shot(page, t); t += 1 / FPS

    # ---- 运行：猜数字提示 + 输入等待 ----
    click(page, "#btnRun")
    for _ in range(48):
        shot(page, t); t += 1 / FPS
    # 交互输入：50 → 太小了；75 → 太大了；63 → 猜对了
    send_input(page, "50")
    for _ in range(36):
        shot(page, t); t += 1 / FPS
    send_input(page, "75")
    for _ in range(36):
        shot(page, t); t += 1 / FPS
    send_input(page, "63")
    for _ in range(48):
        shot(page, t); t += 1 / FPS

    # ---- 切到 utils.py（多文件） ----
    click(page, '[data-tab="utils.py"]')
    for _ in range(36):
        shot(page, t); t += 1 / FPS

    # ---- 运行 utils.py：第一次正常，第二次报错 ----
    click(page, "#btnRun")
    for _ in range(48):
        shot(page, t); t += 1 / FPS
    click(page, "#btnRun")  # retry → RecursionError
    for _ in range(72):
        shot(page, t); t += 1 / FPS

    # ---- 点报错行跳转源码 ----
    page.evaluate("""() => {
        const jump = document.querySelector('.out-body .l.jump');
        if (jump) jump.click();
    }""")
    for _ in range(60):
        shot(page, t); t += 1 / FPS

    # ---- 打开文件抽屉 ----
    click(page, "#btnMenu")
    for _ in range(48):
        shot(page, t); t += 1 / FPS

    # ---- 关闭抽屉，打开命令面板 ----
    page.mouse.click(400, 200)  # 点遮罩关闭抽屉
    wait(page, 0.4)
    click(page, "#btnCmd")
    for _ in range(48):
        shot(page, t); t += 1 / FPS

    # ---- 关闭命令面板，回到 main.py ----
    page.mouse.click(400, 200)
    wait(page, 0.4)
    click(page, '[data-tab="main.py"]')
    for _ in range(36):
        shot(page, t); t += 1 / FPS

    print(f"captured {int(t*FPS)} frames, duration {t:.1f}s")
    b.close()

# ---- ffmpeg 合成 ----
mp4 = "/home/user/Doubao/chats/38444027447280130/dev-terminal/design/promo.mp4"
cmd = ["ffmpeg", "-y", "-framerate", str(FPS), "-i", f"{OUT_DIR}/f%04d.png",
       "-c:v", "libx264", "-preset", "medium", "-crf", "20",
       "-pix_fmt", "yuv420p", "-movflags", "+faststart", mp4]
subprocess.run(cmd, check=True, capture_output=True)
print("saved", mp4)
