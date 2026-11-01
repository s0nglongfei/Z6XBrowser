#!/bin/sh
# 简化版 gradlew：优先用本机已安装的 gradle，找不到再尝试 wrapper
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
# fallback: 尝试 Android Studio 自带的 gradle
for g in "$HOME/.gradle/wrapper/dists/gradle-8.7-bin/"*"/gradle-8.7/bin/gradle" \
         "$HOME/Library/Android/sdk/cmdline-tools/latest/bin/gradle" ; do
  if [ -x "$g" ]; then exec "$g" "$@"; fi
done
echo "ERROR: 未找到 gradle。请先安装 Android Studio 或 Gradle 8.7，或运行 build.sh" >&2
exit 1
