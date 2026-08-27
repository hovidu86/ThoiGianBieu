@echo off
chcp 65001 >nul
net session >nul 2>&1
if not errorlevel 1 goto chay
echo.
echo   Can quyen quan tri de dang ky tac vu tu chay.
echo   Bam Yes o hop thoai Windows vua hien len.
echo.
powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
exit /b
:chay
echo.
echo   Dang cai dat Kiem soat may...
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Cai-dat-tu-chay.ps1"
echo.
echo   Lan dau chay: BAT BUOC dat ma mo khoa toi thieu 10 ky tu,
echo   go GIONG NHAU o CA HAI o mat khau, roi bam "Luu cai dat".
echo   Thieu gi app se hien hop thoai bao ro ly do - khong con im lang nua.
echo.
pause
