#!/usr/bin/env bash
# =============================================================
# 把 App 内置的 Python 运行时同步到设计原型目录
# =============================================================
#
# 背景：Pyodide 运行时（约 13MB）只需要在仓库里存**一份**——即
# app/src/main/assets/pyodide/（App 构建必需，随 APK 打包）。
#
# design/ 下的原型（mockup.html）同样需要这份运行时来做浏览器端演示，
# 但为避免仓库里重复存放 13MB，design/pyodide/full/ 不再入库，
# 改由本脚本从 App assets 同步过来。
#
# 用法：
#   bash tools/sync_design_runtime.sh          # 同步
#   python3 tools/serve_design.py              # 然后起本地服务看原型
# =============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/app/src/main/assets/pyodide"
DST="$ROOT/design/pyodide/full"

if [ ! -d "$SRC" ]; then
  echo "❌ 找不到源目录：$SRC" >&2
  echo "   Python 运行时应随仓库提供（app/src/main/assets/pyodide/）。" >&2
  exit 1
fi

mkdir -p "$DST"
echo "同步 $SRC → $DST"
for f in pyodide.asm.wasm pyodide.asm.js pyodide.js pyodide.mjs pyodide-lock.json python_stdlib.zip; do
  if [ -f "$SRC/$f" ]; then
    cp -f "$SRC/$f" "$DST/$f"
    echo "  ✓ $f"
  else
    echo "  ⚠ 缺少 $f（跳过）"
  fi
done

echo
echo "✅ 完成。运行以下命令预览设计原型："
echo "   python3 tools/serve_design.py"
