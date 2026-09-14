#!/data/data/com.termux/files/usr/bin/bash
# =============================================================
# 方式 A：在真机 Termux 里导出离线工具链
#
# 原理：Termux 里的 $PREFIX (= /data/data/com.termux/files/usr)
#       已经是能在 Android bionic 上运行的完整二进制目录。
#       把它整体打包成 zip，塞进我们 App 的 assets，即可完全离线运行。
#
# 用法（在手机 Termux 中执行）：
#   bash extract_bootstrap.sh
# 产物：
#   /sdcard/Download/usrtar.zip   ← 拷到电脑，放进 app/src/main/assets/
# =============================================================
set -e

PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
OUT="${1:-/sdcard/Download/usrtar.zip}"

echo "==> 更新并安装工具链（python/java/git/clang）"
pkg update -y
pkg install -y python openjdk-17 git clang make

echo "==> 安装常用 Python 库（离线也要能跑）"
pip install --no-cache-dir numpy pandas matplotlib requests flask 2>/dev/null || true

echo "==> 清理缓存，减小体积"
pkg clean 2>/dev/null || true
rm -rf "$PREFIX/tmp/"* 2>/dev/null || true
rm -rf "$PREFIX/var/cache/apt"/* 2>/dev/null || true

echo "==> 打包 $PREFIX -> $OUT"
echo "    （只保留 usr/ 下内容，路径结构与 App 期望一致）"
cd "$PREFIX"
# -y 保留符号链接；-X 去掉 macOS 扩展属性；排除易变目录
zip -q -r -y "$OUT" . \
  -x 'tmp/*' \
  -x 'var/cache/*' \
  -x 'etc/apt/sources.list.d/*'

SIZE=$(du -h "$OUT" | cut -f1)
echo "==> 完成：$OUT（$SIZE）"
echo ""
echo "下一步："
echo "  1) 把 usrtar.zip 拷到开发机"
echo "  2) cp usrtar.zip dev-terminal/app/src/main/assets/usrtar.zip"
echo "  3) 重新构建 APK"
