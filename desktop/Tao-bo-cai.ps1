#Requires -Version 5.1
<#
=====================================================================
  Dung KiemSoatMay-Setup.exe tu ba tep trong thu muc nay, bang IExpress
  (co san trong moi ban Windows - khong can cai them gi).

  Chay:  powershell -ExecutionPolicy Bypass -File Tao-bo-cai.ps1

  Bo cai .exe khi chay se bung KiemSoatMay.ps1 + Khoi-dong.vbs +
  Cai-dat-goi.ps1 vao thu muc tam roi chay Cai-dat-goi.ps1 (an cua so).
=====================================================================
#>
$ErrorActionPreference = 'Stop'
$thuMuc = Split-Path -Parent $MyInvocation.MyCommand.Path
$exe    = Join-Path $thuMuc 'KiemSoatMay-Setup.exe'
$sed    = Join-Path $thuMuc '_build.sed'
Set-Location $thuMuc   # IExpress doc tep SED theo thu muc hien hanh

$canCo = 'KiemSoatMay.ps1', 'Khoi-dong.vbs', 'Cai-dat-goi.ps1'
foreach ($f in $canCo) {
  if (-not (Test-Path (Join-Path $thuMuc $f))) { throw "Thieu $f" }
}

# Kiem cu phap MOI tep .ps1 truoc khi dong goi - dung phat hanh ban hong.
foreach ($f in 'KiemSoatMay.ps1', 'Cai-dat-goi.ps1') {
  $loi = $null
  [void][System.Management.Automation.Language.Parser]::ParseFile(
    (Join-Path $thuMuc $f), [ref]$null, [ref]$loi)
  if ($loi) { throw "$f loi cu phap: $($loi[0].Message)" }
}

if (Test-Path $exe) { Remove-Item $exe -Force }

$noiDungSed = @"
[Version]
Class=IEXPRESS
SEDVersion=3
[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=0
HideExtractAnimation=1
UseLongFileName=1
InsideCompressed=0
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=N
InstallPrompt=%InstallPrompt%
DisplayLicense=%DisplayLicense%
FinishMessage=%FinishMessage%
TargetName=%TargetName%
FriendlyName=%FriendlyName%
AppLaunched=%AppLaunched%
PostInstallCmd=%PostInstallCmd%
AdminQuietInstCmd=%AdminQuietInstCmd%
UserQuietInstCmd=%UserQuietInstCmd%
SourceFiles=SourceFiles
[Strings]
InstallPrompt=
DisplayLicense=
FinishMessage=
TargetName=$exe
FriendlyName=Kiem soat may - Thoi gian bieu
AppLaunched=powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -WindowStyle Hidden -File Cai-dat-goi.ps1
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
FILE0=KiemSoatMay.ps1
FILE1=Khoi-dong.vbs
FILE2=Cai-dat-goi.ps1
[SourceFiles]
SourceFiles0=$thuMuc\
[SourceFiles0]
%FILE0%=
%FILE1%=
%FILE2%=
"@

# SED ghi bang bang ma ANSI cua he thong (khong phai Ascii) de duong dan co
# ky tu ngoai ASCII - vd ten tai khoan tieng Viet - khong bi thanh dau '?'.
[System.IO.File]::WriteAllText($sed, $noiDungSed, [System.Text.Encoding]::Default)

# Don rac tu lan chay truoc (IExpress de lai thu muc IXP*.TMP trong %TEMP%).
Get-ChildItem $env:TEMP -Filter 'IXP*.TMP' -ErrorAction SilentlyContinue |
  Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "Dang dung bo cai bang IExpress..."
# Chi dung /N (dung SED, khong hien wizard) va ten SED TUONG DOI theo thu muc
# hien hanh. KHONG dung /Q va KHONG dua duong dan tuyet doi co dau ngoac -
# ca hai deu lam IExpress thoat ma 1 ma khong tao gi.
$p = Start-Process -FilePath "$env:SystemRoot\System32\iexpress.exe" `
       -ArgumentList '/N', '_build.sed' -WorkingDirectory $thuMuc -Wait -PassThru
Start-Sleep -Milliseconds 500
Remove-Item $sed -Force -ErrorAction SilentlyContinue

if (-not (Test-Path $exe)) {
  throw "IExpress khong tao ra $exe (ma thoat $($p.ExitCode))"
}
$kb = [math]::Round((Get-Item $exe).Length / 1KB, 1)
Write-Host "Xong: $exe ($kb KB)"
