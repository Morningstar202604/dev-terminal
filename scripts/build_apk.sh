#!/bin/bash
# 沙箱专用构建脚本：自动清理 Gradle 孤儿锁/损坏缓存并重试（最多 3 次）
# 用法：bash build_apk.sh
set -uo pipefail

export JAVA_HOME=/home/user/tools/jdk17
export PATH=$JAVA_HOME/bin:$PATH
cd /home/user/Doubao/chats/38444027447280130/dev-terminal

for attempt in 1 2 3; do
  echo "===== build attempt $attempt ====="
  pkill -9 -f "GradleDaemon" 2>/dev/null
  pkill -9 -f "KotlinCompileDaemon" 2>/dev/null
  sleep 1
  find /home/user/.gradle -name "*.lock" -delete 2>/dev/null
  rm -rf /home/user/.gradle/caches/8.11.1/fileHashes \
         /home/user/.gradle/caches/8.11.1/file-changes \
         /home/user/.gradle/caches/8.11.1/executions \
         /home/user/.gradle/caches/journal-1 2>/dev/null
  if ./gradlew --no-daemon assembleDebug "$@" 2>&1 | tail -5; then
    echo "===== BUILD OK on attempt $attempt ====="
    exit 0
  fi
  echo "===== attempt $attempt failed, cleaning and retrying ====="
done
echo "===== BUILD FAILED after 3 attempts ====="
exit 1
