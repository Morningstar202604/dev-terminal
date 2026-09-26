#!/bin/bash
# 本地构建脚本：自动清理 Gradle 孤儿锁/损坏缓存并重试（最多 3 次）
# 用法：bash scripts/build_apk.sh [额外的 gradlew 参数]
#
# 依赖环境变量（都可选）：
#   JAVA_HOME         期望 JDK 17；未设置时自动探测常见安装位置
#   GRADLE_USER_HOME  Gradle 缓存目录，默认 ~/.gradle
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"

if [ -z "${JAVA_HOME:-}" ]; then
  for cand in /usr/lib/jvm/temurin-17-jdk* /usr/lib/jvm/java-17-openjdk* "$HOME/.jdks/temurin-17"*; do
    if [ -d "$cand" ]; then JAVA_HOME="$cand"; break; fi
  done
fi
if [ -n "${JAVA_HOME:-}" ] && [ -d "$JAVA_HOME" ]; then
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
fi

cd "$ROOT"

for attempt in 1 2 3; do
  echo "===== build attempt $attempt ====="
  pkill -9 -f "GradleDaemon" 2>/dev/null
  pkill -9 -f "KotlinCompileDaemon" 2>/dev/null
  sleep 1
  find "$GRADLE_HOME" -name "*.lock" -delete 2>/dev/null
  rm -rf "$GRADLE_HOME"/caches/8.11.1/fileHashes \
         "$GRADLE_HOME"/caches/8.11.1/file-changes \
         "$GRADLE_HOME"/caches/8.11.1/executions \
         "$GRADLE_HOME"/caches/journal-1 2>/dev/null
  if ./gradlew --no-daemon assembleDebug "$@" 2>&1 | tail -5; then
    echo "===== BUILD OK on attempt $attempt ====="
    exit 0
  fi
  echo "===== attempt $attempt failed, cleaning and retrying ====="
done
echo "===== BUILD FAILED after 3 attempts ====="
exit 1