@echo off
where gradle >nul 2>nul
if %ERRORLEVEL%==0 (gradle %*) else (
  if exist "%USERPROFILE%\.gradle\wrapper\dists\gradle-8.7-bin\*" (
    for /r "%USERPROFILE%\.gradle\wrapper\dists\gradle-8.7-bin" %%g in (gradle-8.7\bin\gradle.bat) do "%%g" %*
  ) else (
    echo ERROR: 未找到 gradle。请先安装 Android Studio 或运行 build.bat
    exit /b 1
  )
)
