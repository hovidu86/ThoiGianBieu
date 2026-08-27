@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
echo   Dang dung APK...
call gradlew.bat assembleRelease --console=plain
if errorlevel 1 (
  echo   [LOI] Dung that bai. Doc thong bao o tren.
  pause
  exit /b 1
)
copy /Y "app\build\outputs\apk\release\app-release.apk" "dist-apk\ThoiGianBieu.apk" >nul

rem Cap nhat version.json theo dung build.gradle. App doc tep nay de biet co ban
rem moi hay khong, quen sua la nguoi dung khong bao gio thay ban moi.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Ghi-version-json.ps1"

echo   Xong: dist-apk\ThoiGianBieu.apk
echo   NHO tang versionCode trong app\build.gradle truoc moi lan phat hanh.
pause
