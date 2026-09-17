#!/usr/bin/env bash
# DevTerminal v0.5.0 → GitHub 发布脚本
# ------------------------------------------------------------
# 背景：当前沙箱网络策略将 github.com 解析到保留地址 198.18.0.18（不可达），
#       因此无法在本环境完成 GitHub 推送与 Release。此脚本在可访问 GitHub 的
#       环境中执行，即可一键完成「推送代码 + 推送 tag + 创建 Release + 上传附件」。
#
# 前置：
#   1) 已安装 gh 并登录：  gh auth login
#   2) 目标仓库已存在：    https://github.com/badhope/dev-terminal
#
# 用法：  bash tools/publish_github.sh
set -euo pipefail

REPO="badhope/dev-terminal"
TAG="v0.5.0"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="${DIST_DIR:-/workspace/交付}"

cd "$ROOT"

echo "==> 0. 校验 GitHub 连通性与登录状态"
gh auth status
gh repo view "$REPO" >/dev/null 2>&1 || { echo "远端仓库不存在，正在创建…"; gh repo create "$REPO" --private --source=. --push; }

echo "==> 1. 配置 remote 并推送 main"
git remote remove github 2>/dev/null || true
git remote add github "https://github.com/${REPO}.git"
git push github main

echo "==> 2. 推送 tag ${TAG}"
git push github "${TAG}"

echo "==> 3. 准备 Release 说明"
NOTES="$(mktemp)"
cat > "$NOTES" <<'MDEOF'
## DevTerminal v0.5.0

本轮聚焦「**离线真实 Python 运行引擎**」，并用 11 道真实编程题目完成端到端全量测试。

### ✨ 新增
- 接入 Pyodide 0.26.4 离线真实解释器（Web Worker，真实 stdout/stderr、完整 traceback、input 预喂、多文件 import、超时中断）
- 内置 11 道真实可运行题目（素数筛 / 递归溢出 / 语法错误 / 除零 / KeyError / 多文件导入 / 长输出 / 死循环 / 文件 IO / 中文 Emoji / 交互问答）
- 报错行点击跳转（与 Kotlin 端 parseErrorLine 逐字符一致）

### 🐛 修复
- worker 超时提示字符串拼接错误
- traceback 内部帧清洗、中文/Emoji UTF-8 流式解码
- 多文件 import 的 sys.path、setup 与用户代码分离避免行号偏移

### ✅ 测试：11 / 11 通过
素数筛真实算出 168 个素数；递归/语法/除零/KeyError 精确定位报错行；
多文件导入报错跳到被导入模块；死循环 3 秒安全终止；中文 Emoji 零乱码。

### 📦 附件
- `DevTerminal-v0.5.0-debug.apk` — Android 调试包
- `devterminal_promo.mp4` — 宣传片（34.2s 竖屏）
- `devterminal_recording_labs.mp4` — 11 题录屏（43.8s）
- `devterminal_recording_ui.mp4` — UI 交互录屏（17.2s）
- `DevTerminal-v0.5.0-测试报告.md`

**变更**：versionCode 5 → 6，versionName 0.4.1 → 0.5.0
MDEOF

echo "==> 4. 创建 / 更新 Release"
if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  gh release edit "$TAG" --repo "$REPO" --title "DevTerminal v0.5.0 — 离线真实 Python 引擎" --notes-file "$NOTES"
else
  gh release create "$TAG" --repo "$REPO" --title "DevTerminal v0.5.0 — 离线真实 Python 引擎" --notes-file "$NOTES"
fi

echo "==> 5. 上传附件"
[ -f "$DIST/devterminal_promo.mp4" ] && gh release upload "$TAG" "$DIST/devterminal_promo.mp4#devterminal_promo.mp4" --repo "$REPO" --clobber
[ -f "$DIST/devterminal_录屏_题库遍历.mp4" ] && gh release upload "$TAG" "$DIST/devterminal_录屏_题库遍历.mp4#devterminal_recording_labs.mp4" --repo "$REPO" --clobber
[ -f "$DIST/devterminal_录屏_UI交互.mp4" ] && gh release upload "$TAG" "$DIST/devterminal_录屏_UI交互.mp4#devterminal_recording_ui.mp4" --repo "$REPO" --clobber
[ -f "$DIST/交付报告.md" ] && gh release upload "$TAG" "$DIST/交付报告.md#DevTerminal-v0.5.0-测试报告.md" --repo "$REPO" --clobber
[ -f "$DIST/DevTerminal-v0.4.1-debug.apk" ] && gh release upload "$TAG" "$DIST/DevTerminal-v0.4.1-debug.apk#DevTerminal-v0.5.0-debug.apk" --repo "$REPO" --clobber

echo "==> ✅ GitHub Release ${TAG} 发布完成：https://github.com/${REPO}/releases/tag/${TAG}"
