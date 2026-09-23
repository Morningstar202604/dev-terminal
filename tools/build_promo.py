#!/usr/bin/env python3.11
"""
DevTerminal 宣传片生成器
=======================
阶段一（PIL）：把测试截图合成「手机外壳 + 中文字幕」卡片，并生成标题卡/结尾卡。
阶段二（ffmpeg）：给每张卡片加 Ken Burns 缓动缩放 → 交叉淡入淡出拼接 → 叠加程序化背景音乐 → 输出 promo.mp4。
所有素材自包含，不依赖外部图片/音频。
"""
import os, subprocess, json, math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SHOTS = os.path.join(ROOT, "test_results", "screenshots")
UISHOTS = os.path.join(ROOT, "test_results", "ui_shots")
CARDS = os.path.join(ROOT, "test_results", "promo_cards")
CLIPS = os.path.join(ROOT, "test_results", "promo_clips")
OUT_MP4 = os.path.join(ROOT, "..", "交付", "devterminal_promo.mp4")
os.makedirs(CARDS, exist_ok=True)
os.makedirs(CLIPS, exist_ok=True)

W, H = 1080, 1920
CJK = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"

def font(size, index=3):
    try:
        return ImageFont.truetype(CJK, size, index=index)
    except Exception:
        return ImageFont.truetype(CJK, size, index=0)

def vgrad(top, bottom):
    """竖向渐变背景。"""
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / H
        r = int(top[0] + (bottom[0]-top[0])*t)
        g = int(top[1] + (bottom[1]-top[1])*t)
        b = int(top[2] + (bottom[2]-top[2])*t)
        d.line([(0, y), (W, y)], fill=(r, g, b))
    return img

def glow(d, box, color=(90, 130, 255), width=3, radius=28):
    d.rounded_rectangle(box, radius=radius, outline=color, width=width)

def fit(short_path, target_h):
    """载入截图并按目标高度等比缩放。"""
    im = Image.open(short_path).convert("RGB")
    w, h = im.size
    nh = target_h
    nw = int(w * nh / h)
    return im.resize((nw, nh), Image.LANCZOS)

# 统一为 5 元组：(name, 截图中文幕, 英文副标, 底部说明, 标题卡主副文)
#  - 普通题卡：第 2~4 项为字幕；第 5 项为 None
#  - 标题/结尾卡：第 2 项为 None，第 3 项=主标题，第 4 项=副标题，第 5 项=底部说明
SLIDES = [
    ("title", None, "DevTerminal", "离线 Python / Java 编程终端", "移动端 IDE · 真实可运行 · 随时随地写代码"),
    ("00_home.png", "移动端离线编程终端", "Offline IDE in your pocket", "手机里的完整开发环境", None),
    ("01_lab_panel.png", "内置题库 · 真实题目", "13 real runnable problems", "不是演示，是真跑", None),
    ("02_lab_sieve.png", "真实 Python · 即写即跑", "Real execution · 168 primes", "素数筛算出 1..1000 内 168 个素数", None),
    ("13_lab_pandas.png", "离线数据分析", "pandas DataFrame in pocket", "numpy / pandas 零网络聚合统计", None),
    ("14_lab_matplotlib.png", "离线可视化出图", "matplotlib → PNG offline", "真实渲染图片，直接显示在终端", None),
    ("05_lab_zerodiv.png", "真实报错 · 完整 traceback", "Real Python traceback", "ZeroDivisionError 精确定位", None),
    ("ui_07_error_jump.png", "点击报错行 · 一键跳源码", "Click error → jump to line", "编辑器自动滚动并高亮", None),
    ("10_lab_unicode.png", "中文 / Emoji 零乱码", "Native UTF-8 output", "∑ ∫ √ π  ·  🚀🔥✅", None),
    ("11_lab_quiz.png", "真实交互 · input 预喂", "Interactive input feeding", "问答程序离线真实运行", None),
    ("07_lab_multi.png", "多文件导入 · 跨文件定位", "Multi-file import errors", "报错跳到被导入模块", None),
    ("12_lab_loop.png", "死循环安全终止 · 不卡死", "Safe infinite-loop kill", "3 秒后强制终止，应用照常", None),
    ("ui_08_git.png", "Git 版本管理", "Built-in Git", "提交 / 拉取 / 推送", None),
    ("ui_09_ai.png", "AI 编程助手", "AI assistant", "边写边问", None),
    ("ui_10_cmdk.png", "命令面板 · 全局搜索", "Command palette & search", "键盘流直达", None),
    ("ui_13_theme.png", "6 套编辑器主题", "6 editor themes", "Darcula / Monokai / Solarized …", None),
    ("ui_14_markdown.png", "Markdown 实时预览", "Live Markdown preview", "写文档即时渲染", None),
    ("outro", None, "DevTerminal", "把 IDE 装进口袋", "随时随地，写代码"),
]

def make_title_outro(is_title, big, mid, sub):
    img = vgrad((14, 18, 38), (8, 10, 24))
    d = ImageDraw.Draw(img)
    # 顶部柔光
    glow_circle = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow_circle)
    gd.ellipse([W//2-360, 120, W//2+360, 840], fill=(60, 90, 200, 60))
    img = Image.alpha_composite(img.convert("RGBA"), glow_circle).convert("RGB")
    d = ImageDraw.Draw(img)
    # 中心手机轮廓暗示
    d.rounded_rectangle([W//2-150, H//2-260, W//2+150, H//2+260], radius=36, outline=(120,150,255), width=3)
    d.text((W//2, H//2), "</>", font=font(150), fill=(150,180,255), anchor="mm")
    # 文案
    d.text((W//2, 360), big, font=font(116), fill=(255, 255, 255), anchor="mm")
    d.text((W//2, 360 + 150 if is_title else 150), mid, font=font(50), fill=(150, 180, 255), anchor="mm")
    d.text((W//2, H-300), sub, font=font(38), fill=(200, 210, 235), anchor="mm")
    return img

def make_slide(shot_name, cap_zh, cap_en, sub):
    img = vgrad((16, 20, 40), (10, 12, 28))
    d = ImageDraw.Draw(img)
    # 截图
    shot = fit(os.path.join(UISHOTS if shot_name.startswith("ui_") else SHOTS, shot_name), 1420)
    sw, sh = shot.size
    x = (W - sw) // 2
    y = 120
    # 手机外壳
    pad = 18
    d.rounded_rectangle([x-pad, y-pad, x+sw+pad, y+sh+pad], radius=42, fill=(20, 24, 44))
    glow(d, [x-pad, y-pad, x+sw+pad, y+sh+pad], color=(90, 130, 255), width=3, radius=42)
    img.paste(shot, (x, y))
    # 底部字幕条
    bar_y = y + sh + 60
    d.rounded_rectangle([60, bar_y, W-60, H-70], radius=28, fill=(12, 16, 34))
    d.text((W//2, bar_y + 70), cap_zh, font=font(54), fill=(255, 255, 255), anchor="mm")
    d.text((W//2, bar_y + 140), cap_en, font=font(34), fill=(150, 180, 255), anchor="mm")
    d.text((W//2, bar_y + 195), sub, font=font(30), fill=(190, 200, 225), anchor="mm")
    return img

print("生成卡片…")
for i, (name, cap_zh, cap_en, cap_sub, title_extra) in enumerate(SLIDES):
    if name in ("title", "outro"):
        # 第3项=主标题，第4项=副标题，第5项=底部说明
        img = make_title_outro(name == "title", cap_en, cap_sub, title_extra)
    else:
        img = make_slide(name, cap_zh, cap_en, cap_sub)
    img.save(os.path.join(CARDS, f"{i:02d}_{name}.png"))
print("卡片完成:", len(SLIDES))

# ---------- 阶段二：ffmpeg ----------
FF = "ffmpeg"
cards = sorted(os.listdir(CARDS))
# 1) 每张卡片 → Ken Burns 缓动缩放短片
clip_paths = []
for c in cards:
    src = os.path.join(CARDS, c)
    dst = os.path.join(CLIPS, c.replace(".png", ".mp4"))
    cmd = [
        FF, "-y", "-i", src,
        "-vf", "scale=1296:2304,zoompan=z='min(zoom+0.0012,1.09)':d=90:s=1080x1920:fps=30:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'",
        "-t", "3", "-r", "30", "-c:v", "libopenh264", "-pix_fmt", "yuv420p",
        "-b:v", "4000k", dst,
    ]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    clip_paths.append(dst)
print("片段完成:", len(clip_paths))

# 2) xfade 链式交叉淡入
xf = []
prev = "[0:v]"
for k in range(1, len(clip_paths)):
    out = f"[bf{k}]"
    off = k * (3 - 0.6)  # 每段 3s，过渡 0.6s
    xf.append(f"{prev}[{k}:v]xfade=transition=fade:duration=0.6:offset={off:.2f}{out}")
    prev = out
fc = ";".join(xf) + f";{prev}format=yuv420p[out]"
combined = os.path.join(CLIPS, "combined.mp4")
inputs = [item for p in clip_paths for item in ("-i", p)]
subprocess.run([FF, "-y", *inputs, "-filter_complex", fc, "-map", "[out]", "-c:v", "libopenh264",
                "-pix_fmt", "yuv420p", "-b:v", "4000k", combined], check=True,
               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
print("拼接完成:", combined)

# 3) 程序化背景音乐：柔和大三和弦 pad
music = os.path.join(CLIPS, "music.mp3")
subprocess.run([
    FF, "-y",
    "-f", "lavfi", "-i", "sine=frequency=196.00:duration=60",
    "-f", "lavfi", "-i", "sine=frequency=261.63:duration=60",
    "-f", "lavfi", "-i", "sine=frequency=329.63:duration=60",
    "-f", "lavfi", "-i", "sine=frequency=392.00:duration=60",
    "-filter_complex",
    "[0][1][2][3]amix=inputs=4:duration=longest,"
    "afade=in:st=0:d=3,afade=out:st=40:d=6,"
    "highpass=f=110,lowpass=f=2200,volume=0.13",
    "-c:a", "libmp3lame", "-q:a", "4", music,
], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
print("音乐完成:", music)

# 4) 合成：视频 + 音乐
os.makedirs(os.path.dirname(OUT_MP4), exist_ok=True)
subprocess.run([
    FF, "-y", "-i", combined, "-i", music,
    "-c:v", "copy", "-c:a", "aac", "-b:a", "160k", "-shortest",
    "-movflags", "+faststart", OUT_MP4,
], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
dur = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                     "-of", "default=noprint_wrappers=1:nokey=1", OUT_MP4],
                    capture_output=True, text=True).stdout.strip()
print("✅ 宣传片生成完成:", OUT_MP4, "时长", dur, "s")
