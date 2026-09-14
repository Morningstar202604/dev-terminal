#!/usr/bin/env bash
# =============================================================
# 方式 B：在 Linux/CI 上用 termux-packages 交叉编译构建 bootstrap
#
# 适用：需要更大的可控性、或想固定版本、或没有真机时。
# 前置：Ubuntu/Debian，能跑 Docker（termux-packages 官方推荐用容器构建）。
#
# 用法：
#   ./build_offline_bundle.sh
# 产物：
#   ./out/bootstrap-aarch64.zip  → 放进 app/src/main/assets/usrtar.zip
# =============================================================
set -euo pipefail

WORK="${WORK:-$(pwd)/termux-packaging}"
OUT="$(pwd)/out"
PACKAGES="python openjdk-17 git clang make"

mkdir -p "$OUT"

if [ ! -d "$WORK" ]; then
  echo "==> 克隆 termux-packages"
  git clone --depth 1 https://github.com/termux/termux-packages.git "$WORK"
fi

cd "$WORK"

echo "==> 用官方容器脚本构建 bootstrap"
echo "    （首次会拉取构建镜像，耗时较长）"
# 官方脚本：scripts/run-docker.sh 进入构建容器
# 全量构建所有包极慢；这里走 bootstrap 模式，只含基础包。
# 如需附加包，编辑 scripts/build-bootstraps.sh 的 BOOTSTRAP_PACKAGES。
docker run --rm -v "$WORK:/home/builder/termux-packages" \
  -v "$OUT:/home/builder/out" \
  -w /home/builder/termux-packages \
  ghcr.io/termux/package-builder:latest \
  ./scripts/run-bootstraps.sh --architectures aarch64

echo "==> 收集产物"
cp -v "$WORK"/bootstrap-aarch64.zip "$OUT/" 2>/dev/null || true

if [ -f "$OUT/bootstrap-aarch64.zip" ]; then
  echo "==> 完成：$OUT/bootstrap-aarch64.zip"
  echo "    cp $OUT/bootstrap-aarch64.zip app/src/main/assets/usrtar.zip"
else
  echo "!! 未找到 bootstrap-aarch64.zip，请检查构建日志"
  echo "   提示：bootstrap 默认只含基础包，若需 python/openjdk，"
  echo "   请在 termux-packages 仓库调整 scripts/build-bootstraps.sh 的包列表后重跑。"
  exit 1
fi
