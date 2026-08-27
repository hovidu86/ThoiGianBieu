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
copy /Y "app\build\outputs\apk\release\app-release.apk" "dist-apk\KiemSoatMay.apk" >nul
echo   Xong: dist-apk\KiemSoatMay.apk
echo   NHO tang versionCode trong app\build.gradle truoc moi lan phat hanh,
echo   neu khong Android coi la cung mot ban va co the khong cap nhat.
pause
