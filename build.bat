@echo off
REM 一键打包脚本（Windows）
REM 用法：在 Z6XBrowser 目录打开 cmd，执行 build.bat
setlocal enabledelayedexpansion

echo === 1/3 生成 debug 签名 keystore（首次）===
if not exist app\z6x.keystore (
  keytool -genkeypair -v -keystore app\z6x.keystore -storepass 123456 -keypass 123456 -alias z6x -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Z6X Browser, OU=TV, O=Z6X, L=SZ, S=GD, C=CN"
  echo keystore 已生成
) else (
  echo keystore 已存在，跳过
)

echo === 2/3 编译 APK ===
call gradlew.bat :app:assembleRelease --no-daemon

echo === 3/3 产物 ===
for /r app\build\outputs\apk\release %%f in (*.apk) do (
  echo 找到 APK: %%f
  copy "%%f" "app\build\Z6XBrowser-可安装版.apk" >nul
)
echo.
echo 完成！把 app\build\Z6XBrowser-可安装版.apk 拷到 U 盘，插 Z6X 安装。
endlocal
