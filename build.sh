#!/bin/bash
# 一键打包脚本（Mac / Linux / WSL / 极米开发机）
# 用法：cd Z6XBrowser && ./build.sh
set -e

echo "=== 1/3 生成 debug 签名 keystore（首次） ==="
if [ ! -f app/z6x.keystore ]; then
  keytool -genkeypair -v -keystore app/z6x.keystore -storepass 123456 \
    -keypass 123456 -alias z6x -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Z6X Browser, OU=TV, O=Z6X, L=SZ, S=GD, C=CN"
  echo "keystore 已生成"
else
  echo "keystore 已存在，跳过"
fi

echo "=== 2/3 编译 APK ==="
./gradlew :app:assembleRelease --no-daemon --offline 2>/dev/null || \
./gradlew :app:assembleRelease --no-daemon

echo "=== 3/3 对齐 + 签名 ==="
APK=$(find app/build/outputs/apk/release -name "*.apk" | head -1)
if [ -z "$APK" ]; then echo "未找到 APK，编译失败"; exit 1; fi

# zipalign + apksigner（Android 构建自动完成，这里做兜底重签）
"$ANDROID_HOME/build-tools/33.0.2/zipalign" -f -p 4 "$APK" "app/build/Z6XBrowser-aligned.apk" 2>/dev/null || cp "$APK" "app/build/Z6XBrowser-aligned.apk"
"$ANDROID_HOME/build-tools/33.0.2/apksigner" sign \
  --ks app/z6x.keystore --ks-pass pass:123456 --key-pass pass:123456 \
  --out "app/build/Z6XBrowser-最后可安装版.apk" "app/build/Z6XBrowser-aligned.apk"

echo ""
echo "✅ 完成！安装包："
ls -lh "app/build/Z6XBrowser-最后可安装版.apk"
echo "把上面这个 apk 拷到 U 盘，插 Z6X 安装即可。"
