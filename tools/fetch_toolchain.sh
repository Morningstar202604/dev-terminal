#!/usr/bin/env bash
# 下载离线工具链 usrtar.zip
# ------------------------------------------------------------
# 为什么需要这个脚本？
#   该包约 441MB（含 CPython + openjdk-17 + Git 等），超出 GitHub 单文件
#   100MB 硬限制，也不适合直接入库 / 走免费版 Git LFS。因此仓库只保留
#   本脚本与校验值，工具链通过 Release 附件分发。
#
# 用法：
#   bash tools/fetch_toolchain.sh                # 默认从 GitCode 下载
#   MIRROR=gitee bash tools/fetch_toolchain.sh   # 从 Gitee 下载
#   MIRROR=github bash tools/fetch_toolchain.sh  # 从 GitHub 下载
#
# 下载后自动校验 SHA256，失败会报错并退出。
set -euo pipefail

VERSION="v0.5.0"
EXPECT_SHA256="7ff7f9bef166401b7aa73a212979a457181693fd995a6c058f890fe477425d06"
DEST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/usrtar.zip"

MIRROR="${MIRROR:-gitcode}"
case "$MIRROR" in
  gitcode) URL="https://gitcode.com/badhope/dev-terminal/releases/download/${VERSION}/usrtar.zip" ;;
  gitee)   URL="https://gitee.com/badhope/dev-terminal/releases/download/${VERSION}/usrtar.zip" ;;
  github)  URL="https://github.com/X33834/dev-terminal/releases/download/${VERSION}/usrtar.zip" ;;
  *) echo "未知 MIRROR: $MIRROR（可选 gitcode|gitee|github）" >&2; exit 2 ;;
esac

echo "==> 从 ${MIRROR} 下载离线工具链"
echo "    URL : ${URL}"
echo "    目标: ${DEST}"

if [ -f "$DEST" ]; then
  cur="$(sha256sum "$DEST" | awk '{print $1}')"
  if [ "$cur" = "$EXPECT_SHA256" ]; then
    echo "==> 已存在且校验通过，跳过下载"
    exit 0
  fi
  echo "==> 已存在但校验不符，重新下载"
fi

curl -L --fail --progress-bar -o "${DEST}.part" "$URL"
mv "${DEST}.part" "$DEST"

echo "==> 校验 SHA256"
actual="$(sha256sum "$DEST" | awk '{print $1}')"
if [ "$actual" != "$EXPECT_SHA256" ]; then
  echo "!! 校验失败" >&2
  echo "   期望: $EXPECT_SHA256" >&2
  echo "   实际: $actual" >&2
  exit 1
fi
echo "==> ✅ 完成：$DEST"
