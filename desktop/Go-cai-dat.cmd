@echo off
chcp 65001 >nul
net session >nul 2>&1
if not errorlevel 1 goto chay
powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
exit /b
:chay
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Cai-dat-tu-chay.ps1" -Go
echo.
echo   App dang chay van tiep tuc chay: chuot phai icon khien o khay
echo   he thong -^> Thoat (can ma) de dung han.
echo.
pause
