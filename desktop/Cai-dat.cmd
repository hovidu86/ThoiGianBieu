@echo off
chcp 65001 >nul
setlocal
net session >nul 2>&1
if not errorlevel 1 goto chay
echo.
echo   Can quyen quan tri de dang ky tac vu tu chay.
echo   Bam Yes o hop thoai Windows vua hien len.
echo.
rem Bat ten nguoi dung THAT truoc khi nang quyen. Sau khi nang quyen,
rem %USERNAME% co the la tai khoan quan tri khac, tac vu se dang ky nham nguoi.
powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -ArgumentList '%USERDOMAIN%\%USERNAME%' -Verb RunAs"
exit /b
:chay
set NGUOIDUNG=%~1
if "%NGUOIDUNG%"=="" set NGUOIDUNG=%USERDOMAIN%\%USERNAME%
echo.
echo   Dang cai dat Kiem soat may...
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Cai-dat-tu-chay.ps1" -NguoiDung "%NGUOIDUNG%"
echo.
echo   Lan dau chay: BAT BUOC dat ma mo khoa toi thieu 10 ky tu,
echo   go GIONG NHAU o CA HAI o mat khau, roi bam "Luu cai dat".
echo   Thieu gi app se hien hop thoai bao ro ly do - khong con im lang nua.
echo.
pause
