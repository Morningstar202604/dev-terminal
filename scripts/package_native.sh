#!/bin/bash
# 打包原生 CPython 运行产物到 dev-terminal APK 结构
# 用法：bash package_native.sh <cpython-cross-dir>
set -euo pipefail

SRC="${1:-/home/user/tools/ndk-dl/cpython-3.13.9/cross-build/aarch64-linux-android}"
PREFIX="$SRC/prefix"
APP=/home/user/Doubao/chats/38444027447280130/dev-terminal/app
NDK=/home/user/tools/android-sdk-34/ndk/27.3.13750724
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64"

echo "== 1. 拷贝 libpython3.13.so 到 jniLibs =="
mkdir -p "$APP/src/main/jniLibs/arm64-v8a"
ls -la "$PREFIX/lib/libpython3.13.so"
cp "$PREFIX/lib/libpython3.13.so" "$APP/src/main/jniLibs/arm64-v8a/"

echo "== 2. 编译 libpybridge.so（JNI 桥） =="
ls "$PREFIX/include/python3.13/Python.h" >/dev/null
"$TC/bin/aarch64-linux-android21-clang" \
  -I"$PREFIX/include/python3.13" \
  -I"$APP/src/main/jniLibs/arm64-v8a" \
  -shared -fPIC -O2 \
  -o "$APP/src/main/jniLibs/arm64-v8a/libpybridge.so" \
  "$APP/src/main/cpp/pybridge.c" \
  -L"$APP/src/main/jniLibs/arm64-v8a" -lpython3.13 \
  -lm -ldl -llog -Wl,--no-undefined
ls -la "$APP/src/main/jniLibs/arm64-v8a/libpybridge.so"

echo "== 3. 打包标准库到 assets/python-stdlib.zip =="
# 只打纯 .py 标准库（lib-dynload 的 .so 扩展暂不打包，后续按需追加）
STDLIB="$PREFIX/lib/python3.13"
python3 - "$STDLIB" "$APP/src/main/assets/python-stdlib.zip" <<'PY'
import sys, zipfile, os
src, out = sys.argv[1], sys.argv[2]
count = 0
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk(src):
        if "lib-dynload" in root:
            continue
        for f in files:
            p = os.path.join(root, f)
            arc = "lib/python3.13/" + os.path.relpath(p, src)
            z.write(p, arc)
            count += 1
print(f"packed {count} files")
PY
ls -la "$APP/src/main/assets/python-stdlib.zip"

echo "== 完成 =="
