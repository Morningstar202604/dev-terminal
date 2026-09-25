#!/bin/bash
# 打包原生 CPython 运行产物到 dev-terminal APK 结构
# 用法：bash package_native.sh <cpython-cross-dir>
set -euo pipefail

SRC="${1:-/home/user/tools/ndk-dl/cpython-3.13.9/cross-build/aarch64-linux-android}"
PREFIX="$SRC/prefix"
APP=/home/user/Doubao/chats/38444027447280130/dev-terminal/app
NDK=/home/user/tools/android-sdk-34/ndk/27.3.13750724
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
JDIR="$APP/src/main/jniLibs/arm64-v8a"

echo "== 1. 拷贝 libpython3.13.so 到 jniLibs =="
mkdir -p "$JDIR"
ls -la "$PREFIX/lib/libpython3.13.so"
cp "$PREFIX/lib/libpython3.13.so" "$JDIR/"

echo "== 2. 拷贝 C 扩展（lib-dynload）与依赖库到 jniLibs =="
# C 扩展走 jniLibs 方案：APK 安装时系统把它们解压到 nativeLibraryDir，
# pybridge.c initialize 时把该目录 insert 进 sys.path，CPython 即可 import
# math/random/json/datetime/socket/ctypes/_ssl/_sqlite3 等。
# 过滤测试模块（_test* / _ctypes_test / xxlimited* / xxsubtype / _xxtestfuzz）。
DYLOAD="$PREFIX/lib/python3.13/lib-dynload"
copied=0
for f in "$DYLOAD"/*.so; do
  base=$(basename "$f")
  case "$base" in
    _test*|_ctypes_test*|xxlimited*|xxsubtype*|_xxtestfuzz) ;;  # skip
    *) cp "$f" "$JDIR/"; copied=$((copied+1)) ;;
  esac
done
echo "    copied $copied extension modules"
# 依赖库：_ssl/_hashlib 依赖 libcrypto_python(+libssl_python)，_sqlite3 依赖 libsqlite3_python
cp "$PREFIX/lib/libssl_python.so"      "$JDIR/"
cp "$PREFIX/lib/libcrypto_python.so"   "$JDIR/"
cp "$PREFIX/lib/libsqlite3_python.so"  "$JDIR/"

echo "== 3. 编译 libpybridge.so（JNI 桥） =="
ls "$PREFIX/include/python3.13/Python.h" >/dev/null
"$TC/bin/aarch64-linux-android21-clang" \
  -I"$PREFIX/include/python3.13" \
  -shared -fPIC -O2 \
  -o "$JDIR/libpybridge.so" \
  "$APP/src/main/cpp/pybridge.c" \
  -L"$JDIR" -lpython3.13 \
  -lm -ldl -llog -Wl,--no-undefined
ls -la "$JDIR/libpybridge.so"

echo "== 4. 打包纯 .py 标准库到 assets/python-stdlib.zip =="
# 注意：C 扩展（lib-dynload 下的 .so）不进 zip——它们走 jniLibs（见步骤 2）。
# 这里只打纯 Python 标准库，解压后落在 files/python-stdlib/lib/python3.13/。
STDLIB="$PREFIX/lib/python3.13"
python3 - "$STDLIB" "$APP/src/main/assets/python-stdlib.zip" <<'PY'
import sys, zipfile, os
src, out = sys.argv[1], sys.argv[2]
count = 0
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk(src):
        if "lib-dynload" in root:
            continue  # C 扩展走 jniLibs，不进 zip
        for f in files:
            p = os.path.join(root, f)
            arc = "lib/python3.13/" + os.path.relpath(p, src)
            z.write(p, arc)
            count += 1
print(f"packed {count} pure-python files")
PY
ls -la "$APP/src/main/assets/python-stdlib.zip"

echo "== 完成 =="
