#!/usr/bin/env bash
# DevTerminal v0.8.0 → GitHub 发布脚本
# ------------------------------------------------------------
# 用法： bash tools/publish_github.sh
#
# 前置：
#   1) 已安装 gh 并登录（gh auth login），或设置 GH_TOKEN 环境变量
#   2) 本脚本会逐个推送以下 GitHub 仓库（与 README「开源仓库」保持一致）：
#      - x33834/dev-terminal      （主仓库）
#      - Morningstar202604/dev-terminal  （镜像）
#
# 说明：本脚本只负责任务编排；它不假设「环境能否访问 GitHub」——
#       在可访问 GitHub 的环境内执行即可一键完成
#       「推送代码 + 推送 tag + 创建/更新 Release + 上传附件」。
# ------------------------------------------------------------
set -euo pipefail

# 支持 --dry-run：不触网，只校验远端配置与附件是否齐备
DRY_RUN=0
[ "${1:-}" = "--dry-run" ] && DRY_RUN=1

TAG="v0.8.0"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="${DIST_DIR:-$ROOT/dist}"
mkdir -p "$DIST"

# 需要随 Release 上传的附件（存在才上传）
APK="$DIST/DevTerminal-${TAG}-debug.apk"
DEMO="$DIST/devterminal_demo.mp4"
PROMO="$DIST/devterminal_promo.mp4"
REPORT="$DIST/DevTerminal-${TAG}-测试报告.md"

REPOS=("x33834/dev-terminal" "Morningstar202604/dev-terminal")

cd "$ROOT"

# ------------------------------------------------------------
# dry-run：只校验「远端配置 + 附件清单」，不触网
# ------------------------------------------------------------
if [ "$DRY_RUN" = "1" ]; then
  echo "==> [dry-run] 校验远端仓库："
  for REPO in "${REPOS[@]}"; do
    if git remote get-url "gh-$REPO" >/dev/null 2>&1 \
       || git remote get-url "github" >/dev/null 2>&1 \
       || git remote get-url "github-mirror" >/dev/null 2>&1; then
      echo "  ✅ remote 就绪：$REPO"
    else
      echo "  ⚠️  未配置 remote：$REPO（正式执行时会自动添加）"
    fi
  done
  echo "==> [dry-run] 校验附件："
  for f in "$APK" "$DEMO" "$PROMO" "$REPORT"; do
    if [ -f "$f" ]; then
      echo "  ✅ $(basename "$f")  $(du -h "$f" | cut -f1)"
    else
      echo "  ⚠️  缺失：$f"
    fi
  done
  echo "==> [dry-run] 校验 tag：$(git tag --list "$TAG" | grep -q "$TAG" && echo "$TAG 已存在" || echo "$TAG 未创建")"
  echo "==> [dry-run] 完成（未触网、未推送）。"
  exit 0
fi

echo "==> 0. 校验 GitHub 登录状态"
gh auth status

echo "==> 1. 准备 Release 说明"
NOTES="$(mktemp)"
cat > "$NOTES" <<'MDEOF'
## DevTerminal v0.8.0 — 离线 Python 终端：数据科学 + 多主题 + 预览

本轮把 DevTerminal 从「能跑 Python」推进到「能干活」：

### ✨ 新增
- **matplotlib 离线画图**：3.5.2 及全部依赖随 APK 打包（约 24MB），`import matplotlib.pyplot` 即用，零网络。
- **数据科学栈离线内置**：numpy 1.26.4 / pandas 2.2.0 官方 Pyodide wheel 随包，运行器按 `import` 自动装载。
- **编辑器 6 套主题**：Darcula / Quiet Light / Monokai / Solarized 暗·亮 / 明日蓝，设置页可切换。
- **Markdown / HTML 实时预览**：打开 .md/.html 即开分屏预览，基于 IntelliJ 同款 markdown 解析器。
- **JGit 离线 Git**：init / status / commit / push / pull 全在应用内完成。

### ✅ 测试
- Kotlin 单元测试 **29/29 通过**（GitManager / MarkdownRenderer / ParseErrorLine）。
- 端到端：无头 Chromium 真实运行 13 道题目（含 numpy / pandas / matplotlib 出图），全部符合预期。
- 静态自检（selfcheck）通过。

### 📦 附件
- `DevTerminal-v0.8.0-debug.apk` — Android 调试包（含离线 Python 运行时，约 53MB）
- `devterminal_demo.mp4` — 真实录屏（13 题 + UI + 主题 + 预览）
- `devterminal_promo.mp4` — 宣传片（竖屏）
- `DevTerminal-v0.8.0-测试报告.md` — 全量测试报告

> 装完即离线：无需联网即可运行 Python / Java、画图、分析数据。
MDEOF

for REPO in "${REPOS[@]}"; do
  echo ""
  echo "=================================================="
  echo "==> 处理仓库：$REPO"
  echo "=================================================="

  echo "==> 2. 校验/创建远端仓库"
  if ! gh repo view "$REPO" >/dev/null 2>&1; then
    echo "远端仓库不存在，正在创建（private）…"
    gh repo create "$REPO" --private --source=. --push || echo "创建失败，继续尝试推送…"
  fi

  echo "==> 3. 配置 remote 并推送 main + tag"
  git remote remove "gh-$REPO" 2>/dev/null || true
  git remote add "gh-$REPO" "https://github.com/${REPO}.git"
  git push "gh-$REPO" main
  git push "gh-$REPO" "$TAG" || echo "tag 已存在或推送失败，继续…"

  echo "==> 4. 创建 / 更新 Release"
  if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
    gh release edit "$TAG" --repo "$REPO" --title "DevTerminal v0.8.0 — 离线数据科学 + 多主题 + 预览" --notes-file "$NOTES"
  else
    gh release create "$TAG" --repo "$REPO" --title "DevTerminal v0.8.0 — 离线数据科学 + 多主题 + 预览" --notes-file "$NOTES" --latest
  fi

  echo "==> 5. 上传附件"
  [ -f "$APK" ]    && gh release upload "$TAG" "$APK#DevTerminal-v0.8.0-debug.apk" --repo "$REPO" --clobber
  [ -f "$DEMO" ]   && gh release upload "$TAG" "$DEMO#devterminal_demo.mp4" --repo "$REPO" --clobber
  [ -f "$PROMO" ]  && gh release upload "$TAG" "$PROMO#devterminal_promo.mp4" --repo "$REPO" --clobber
  [ -f "$REPORT" ] && gh release upload "$TAG" "$REPORT#DevTerminal-v0.8.0-测试报告.md" --repo "$REPO" --clobber

  echo "✅ $REPO 发布完成：https://github.com/${REPO}/releases/tag/${TAG}"
done

echo ""
echo "全部仓库发布完成。"
