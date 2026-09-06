#Requires -Version 5.1
<#
=====================================================================
  Bo cai Kiem soat may - chay tu KiemSoatMay-Setup.exe

  Setup.exe bung ba tep (KiemSoatMay.ps1, Khoi-dong.vbs, chinh tep nay)
  vao mot thu muc tam roi chay tep nay. Viec cua no:

    1. Chep KiemSoatMay.ps1 + Khoi-dong.vbs vao
         %LOCALAPPDATA%\ThoiGianBieu\app\
       (noi o lau dai, khong phu thuoc thu muc giai nen tam).
    2. Dang ky tac vu Task Scheduler tro vao ban da chep do, voi du cac
       tuy chon pin / khong gioi han thoi gian / tu hoi phuc moi 30 phut.
    3. Kiem lai tung tuy chon, khoi dong app, bao ket qua.

  Neu tac vu cu do mot ban cai co quyen quan tri tao ra, tep nay tu xin
  nang quyen mot lan (hop thoai UAC). Cai moi hoan toan thi khong can.
=====================================================================
#>
param(
  [switch]$Go,               # go cai dat
  [switch]$KhongHienKetQua,  # in ra man hinh thay vi hien hop thoai (de test)
  [switch]$DaNangQuyen,      # noi bo: ban chay sau khi nang quyen
  # noi bo: danh tinh + thu muc cua NGUOI DUNG THAT, do ban chua nang quyen
  # truyen sang. Sau khi UAC, $env:USERNAME/$env:LOCALAPPDATA co the la cua
  # tai khoan quan tri khac -> phai dung cac gia tri nay.
  [string]$NguoiDungThat = '',
  [string]$LocalAppDataThat = ''
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms

$TenTacVu = 'ThoiGianBieu-KiemSoatMay'
$ThuMucNguon = Split-Path -Parent $MyInvocation.MyCommand.Path

$NguoiDung = if ($NguoiDungThat) { $NguoiDungThat } else { "$env:USERDOMAIN\$env:USERNAME" }
$LocalAD   = if ($LocalAppDataThat) { $LocalAppDataThat } else { $env:LOCALAPPDATA }
$ThuMucApp = Join-Path $LocalAD 'ThoiGianBieu\app'
$ThuMucCH  = Join-Path $LocalAD 'ThoiGianBieu'
$Vbs       = Join-Path $ThuMucApp 'Khoi-dong.vbs'

function Hop([string]$noi, [string]$tieuDe, $icon = 'Information') {
  if ($KhongHienKetQua) { Write-Host "[$tieuDe] $noi"; return }
  [System.Windows.Forms.MessageBox]::Show($noi, $tieuDe,
    [System.Windows.Forms.MessageBoxButtons]::OK,
    [System.Windows.Forms.MessageBoxIcon]::$icon) | Out-Null
}

function La-Admin {
  ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
   ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

# schtasks ghi ra stderr ca khi chi la "khong tim thay tac vu". Voi
# $ErrorActionPreference='Stop' + 2>&1 thi PowerShell 5.1 bien dong stderr do
# thanh loi ket thuc. Boc lai o day: tra ve @{ Code; Out } va khong nem loi.
function Sch {
  $cu = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  $ra = & schtasks.exe @args 2>&1 | Out-String
  $ma = $LASTEXITCODE
  $ErrorActionPreference = $cu
  [pscustomobject]@{ Code = $ma; Out = $ra }
}

function Dung-AppDangChay {
  Get-WmiObject Win32_Process -Filter "Name='powershell.exe' OR Name='wscript.exe'" |
    Where-Object { $_.CommandLine -and
      ($_.CommandLine -like '*KiemSoatMay.ps1*' -or $_.CommandLine -like '*ThoiGianBieu\app\Khoi-dong*') } |
    ForEach-Object { try { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } catch { } }
}

<# Xoa tac vu cu. Tra ve $true neu sau do khong con tac vu nao. #>
function Xoa-TacVuCu {
  if ((Sch /Query /TN $TenTacVu).Code -ne 0) { return $true }   # von khong co
  Sch /Delete /TN $TenTacVu /F | Out-Null
  return ((Sch /Query /TN $TenTacVu).Code -ne 0)                # khong con -> xoa duoc
}

<# Chay lai chinh minh sau khi nang quyen. Chep san toan bo tep sang mot thu
   muc tam do minh quan ly (thu muc giai nen cua Setup.exe se bi don ngay khi
   ban goc thoat), roi doi ban nang quyen chay xong. #>
function Chay-Lai-NangQuyen {
  $tam = Join-Path $env:TEMP ('tgb-setup-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
  New-Item -ItemType Directory -Path $tam -Force | Out-Null
  Get-ChildItem $ThuMucNguon -File | Copy-Item -Destination $tam -Force
  $ds = [System.Collections.Generic.List[string]]@('-NoProfile', '-ExecutionPolicy', 'Bypass',
        '-STA', '-WindowStyle', 'Hidden', '-File', (Join-Path $tam 'Cai-dat-goi.ps1'), '-DaNangQuyen',
        '-NguoiDungThat', $NguoiDung, '-LocalAppDataThat', $env:LOCALAPPDATA)
  if ($Go) { $ds.Add('-Go') }
  if ($KhongHienKetQua) { $ds.Add('-KhongHienKetQua') }
  try {
    $p = Start-Process powershell.exe -Verb RunAs -PassThru -Wait -ArgumentList $ds.ToArray()
    $ma = $p.ExitCode
  } catch {
    Hop ("Can quyen quan tri de sua tac vu tu chay da co san.`r`n`r`n" +
         "Ban da bam Khong o hop thoai Windows, hoac may chan nang quyen. " +
         "Thu chuot phai KiemSoatMay-Setup.exe -> Run as administrator.") 'Kiem soat may' 'Warning'
    $ma = 1
  }
  Remove-Item $tam -Recurse -Force -ErrorAction SilentlyContinue
  exit $ma
}

# ==================== GO CAI DAT ====================

if ($Go) {
  if (-not (Xoa-TacVuCu)) {
    if (-not (La-Admin) -and -not $DaNangQuyen) { Chay-Lai-NangQuyen }
    Sch /Delete /TN $TenTacVu /F | Out-Null
  }
  Dung-AppDangChay
  Hop ("Da go tac vu tu chay.`r`n`r`n" +
       "Thu muc $ThuMucApp va cau hinh (may.json, nhat ky) van giu nguyen - " +
       "xoa tay neu muon don han.`r`n`r`n" +
       "Neu app dang khoa may, chuot phai icon khien o khay he thong roi chon " +
       "'Thoat (can ma)'.") 'Go Kiem soat may'
  return
}

# ==================== CAI DAT ====================

$psNguon  = Join-Path $ThuMucNguon 'KiemSoatMay.ps1'
$vbsNguon = Join-Path $ThuMucNguon 'Khoi-dong.vbs'
foreach ($f in @($psNguon, $vbsNguon)) {
  if (-not (Test-Path $f)) {
    Hop "Thieu tep trong bo cai: $f`r`n`r`nBo cai co the da hong, tai lai." 'Loi bo cai' 'Error'
    exit 1
  }
}

# Tac vu cu (neu co) do ban cai quan tri tao ra thi phai nang quyen moi sua duoc.
if (-not (La-Admin) -and -not $DaNangQuyen) {
  if (-not (Xoa-TacVuCu)) { Chay-Lai-NangQuyen }
}

# 1. Chep vao noi o lau dai (them ca chinh bo cai nay + mot .cmd de go)
try {
  Dung-AppDangChay
  Start-Sleep -Milliseconds 400
  New-Item -ItemType Directory -Path $ThuMucApp -Force | Out-Null
  Copy-Item $psNguon  (Join-Path $ThuMucApp 'KiemSoatMay.ps1') -Force
  Copy-Item $vbsNguon (Join-Path $ThuMucApp 'Khoi-dong.vbs')   -Force
  Copy-Item (Join-Path $ThuMucNguon 'Cai-dat-goi.ps1') (Join-Path $ThuMucApp 'Cai-dat-goi.ps1') -Force
  @'
@echo off
rem Go Kiem soat may. Nhan dup tep nay.
powershell -NoProfile -ExecutionPolicy Bypass -STA -File "%~dp0Cai-dat-goi.ps1" -Go
pause
'@ | Set-Content -Path (Join-Path $ThuMucApp 'Go-cai-dat.cmd') -Encoding Ascii
} catch {
  Hop "Khong chep duoc tep vao $ThuMucApp`r`n`r`n$($_.Exception.Message)" 'Loi cai dat' 'Error'
  exit 1
}

# 2. Dang ky tac vu. Dung bang XML vi chi XML moi dat duoc tuy chon pin va
#    ExecutionTimeLimit. Xem desktop/README.md.
$xml = @"
<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.2" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <RegistrationInfo>
    <Description>Thoi gian bieu - khoa man hinh theo gio da hen. Tu chay lai moi 30 phut neu bi tat.</Description>
  </RegistrationInfo>
  <Triggers>
    <LogonTrigger>
      <Enabled>true</Enabled>
      <UserId>$NguoiDung</UserId>
    </LogonTrigger>
    <CalendarTrigger>
      <Repetition>
        <Interval>PT30M</Interval>
        <Duration>P1D</Duration>
        <StopAtDurationEnd>false</StopAtDurationEnd>
      </Repetition>
      <StartBoundary>2026-01-01T00:05:00</StartBoundary>
      <Enabled>true</Enabled>
      <ScheduleByDay>
        <DaysInterval>1</DaysInterval>
      </ScheduleByDay>
    </CalendarTrigger>
  </Triggers>
  <Principals>
    <Principal id="Author">
      <UserId>$NguoiDung</UserId>
      <LogonType>InteractiveToken</LogonType>
      <RunLevel>LeastPrivilege</RunLevel>
    </Principal>
  </Principals>
  <Settings>
    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
    <AllowHardTerminate>true</AllowHardTerminate>
    <StartWhenAvailable>true</StartWhenAvailable>
    <RunOnlyIfNetworkAvailable>false</RunOnlyIfNetworkAvailable>
    <IdleSettings>
      <StopOnIdleEnd>false</StopOnIdleEnd>
      <RestartOnIdle>false</RestartOnIdle>
    </IdleSettings>
    <AllowStartOnDemand>true</AllowStartOnDemand>
    <Enabled>true</Enabled>
    <Hidden>false</Hidden>
    <RunOnlyIfIdle>false</RunOnlyIfIdle>
    <WakeToRun>false</WakeToRun>
    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>
    <Priority>7</Priority>
  </Settings>
  <Actions Context="Author">
    <Exec>
      <Command>wscript.exe</Command>
      <Arguments>"$Vbs"</Arguments>
    </Exec>
  </Actions>
</Task>
"@

$fileXml = Join-Path $env:TEMP 'tgb-kiem-soat-may.xml'
$xml | Out-File -FilePath $fileXml -Encoding Unicode -Force
Sch /Delete /TN $TenTacVu /F | Out-Null
$tao = Sch /Create /TN $TenTacVu /XML $fileXml /F
Remove-Item $fileXml -Force -ErrorAction SilentlyContinue

if ($tao.Code -ne 0) {
  Hop ("Khong dang ky duoc tac vu tu chay:`r`n" + $tao.Out + "`r`n`r`n" +
       "Thu chuot phai KiemSoatMay-Setup.exe -> Run as administrator.") 'Loi cai dat' 'Error'
  exit 1
}

# 3. Kiem lai dung nhung gi vua dat
$chiTiet = (Sch /Query /TN $TenTacVu /XML).Out
$kiem = [ordered]@{
  'Chay duoc khi dung pin'         = ($chiTiet -match '<DisallowStartIfOnBatteries>false<')
  'Khong dung khi chuyen sang pin' = ($chiTiet -match '<StopIfGoingOnBatteries>false<')
  'Khong gioi han thoi gian chay'  = ($chiTiet -match '<ExecutionTimeLimit>PT0S<')
  'Chay khi dang nhap Windows'     = ($chiTiet -match '<LogonTrigger>')
  'Tu hoi phuc moi 30 phut'        = ($chiTiet -match '<Interval>PT30M<')
}
$dong = @(); $hong = 0
foreach ($k in $kiem.Keys) {
  if ($kiem[$k]) { $dong += "   [OK]   $k" } else { $dong += "   [HONG] $k"; $hong++ }
}

# 4. Khoi dong qua chinh tac vu vua tao (vua la phep thu that)
Sch /Run /TN $TenTacVu | Out-Null
Start-Sleep -Seconds 8
$dangChay = Get-WmiObject Win32_Process -Filter "Name='powershell.exe'" |
            Where-Object { $_.CommandLine -like '*ThoiGianBieu\app\KiemSoatMay.ps1*' }
if ($dangChay) { $dong += '   [OK]   App dang chay (icon hinh khien o khay he thong)' }
else { $dong += '   [HONG] App chua khoi dong duoc'; $hong++ }

$maCu = Test-Path (Join-Path $ThuMucCH 'may.json')
$noiDung =
  "Da cai Kiem soat may vao:`r`n   $ThuMucApp`r`n`r`n" +
  ($dong -join "`r`n") + "`r`n`r`n" +
  $(if ($maCu) {
      "Da co ma mo khoa tu truoc - dung luon ma cu."
    } else {
      "LAN DAU: chuot phai icon hinh khien o khay he thong -> Cai dat, dat ma mo " +
      "khoa toi thieu 10 ky tu (go giong nhau o ca hai o) roi bam Luu. Chua co ma " +
      "thi app KHONG khoa gi ca."
    })

if ($hong -gt 0) {
  Hop ($noiDung + "`r`n`r`nCo muc [HONG] - thu chay lai bang Run as administrator.") 'Kiem soat may' 'Warning'
  exit 1
}
Hop $noiDung 'Kiem soat may - da cai xong'
