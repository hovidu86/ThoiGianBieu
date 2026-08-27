#Requires -Version 5.1
<#
=====================================================================
  Đăng ký Kiểm soát máy chạy tự động, chạy mãi, thiết lập một lần.

  Bốn điểm khác với `schtasks` dựng bằng dòng lệnh — cả bốn đều là lý do
  khiến tác vụ im lặng không chạy:
    1. DisallowStartIfOnBatteries = false — mặc định Windows KHÔNG khởi
       động tác vụ khi máy đang chạy pin. Laptop rút sạc là app không bao
       giờ tự chạy. Đây là lỗi đã gặp thật.
    2. StopIfGoingOnBatteries = false — đang chạy mà rút sạc cũng bị giết.
    3. ExecutionTimeLimit = PT0S — mặc định 72 giờ, quá hạn thì bị giết.
    4. Lịch lặp mỗi 30 phút — app tắt vì bất cứ lý do gì thì chậm nhất nửa
       tiếng sau tự sống lại. Bản đang chạy giữ một mutex nên các lần gọi
       thừa tự thoát lặng lẽ, không nhân bản.
=====================================================================
#>
param(
  [switch]$Go,
  [string]$Ten = 'ThoiGianBieu-KiemSoatMay',
  [switch]$ChiKiemTra,  # đăng ký, kiểm tra, xoá lại - không khởi động app
  # Chỉ dùng khi muốn ép một tài khoản cụ thể. Để trống thì tự dò, xem
  # hàm Tim-NguoiDungThat bên dưới.
  [string]$NguoiDung = ''
)

<#
  Tài khoản đang thật sự ngồi trước máy.

  Không đọc $env:USERNAME được: script này chạy sau khi nâng quyền, nên trên
  máy dùng tài khoản thường nó sẽ ra tên người quản trị vừa bấm Yes ở hộp thoại
  UAC, và tác vụ đăng ký xong không bao giờ chạy cho người dùng thật.

  Cũng không truyền qua tham số dòng lệnh được: tên tài khoản trên máy này là
  "HOANG VIET DUNG", có dấu cách, mà Start-Process nối các tham số bằng dấu
  cách và không tự bọc nháy — sang tới cmd thì %1 chỉ còn "PC\HOANG".

  Cách chắc chắn: hỏi chủ sở hữu tiến trình explorer.exe. Đó luôn là người
  đang đăng nhập, không phải người vừa nâng quyền.
#>
function Tim-NguoiDungThat {
  try {
    $vo = Get-WmiObject Win32_Process -Filter "Name='explorer.exe'" | Select-Object -First 1
    if ($vo) {
      $chu = $vo.GetOwner()
      if ($chu -and $chu.User) { return ($chu.Domain + '\' + $chu.User) }
    }
  } catch { }
  return "$env:USERDOMAIN\$env:USERNAME"
}

$TenTacVu = $Ten
$ThuMuc = Split-Path -Parent $MyInvocation.MyCommand.Path
$Vbs = Join-Path $ThuMuc 'Khoi-dong.vbs'

function Bao([string]$dat, [string]$viec) {
  Write-Host ('    ' + $dat + ' ' + $viec)
}

if ($Go) {
  schtasks /Delete /TN $TenTacVu /F 2>&1 | Out-Null
  if ($LASTEXITCODE -eq 0) { Write-Host '  Da go khoi danh sach tu chay.' }
  else { Write-Host '  Khong tim thay tac vu nao de go.' }
  return
}

if (-not (Test-Path $Vbs)) {
  Write-Host "  [LOI] Khong thay $Vbs"
  exit 1
}

$nguoiDung = if ($NguoiDung -ne '') { $NguoiDung } else { Tim-NguoiDungThat }
Write-Host ('  Tac vu se chay duoi tai khoan: ' + $nguoiDung)

# Dựng thẳng XML thay vì dùng dòng lệnh schtasks: chỉ XML mới đặt được
# các tuỳ chọn pin và giới hạn thời gian chạy.
$xml = @"
<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.2" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <RegistrationInfo>
    <Description>Thoi gian bieu - khoa man hinh theo gio da hen. Tu chay lai moi 30 phut neu bi tat.</Description>
  </RegistrationInfo>
  <Triggers>
    <LogonTrigger>
      <Enabled>true</Enabled>
      <UserId>$nguoiDung</UserId>
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
      <UserId>$nguoiDung</UserId>
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

# schtasks /XML chỉ đọc được file UTF-16.
$fileXml = Join-Path $env:TEMP 'tgb-kiem-soat-may.xml'
$xml | Out-File -FilePath $fileXml -Encoding Unicode -Force

$ketQua = schtasks /Create /TN $TenTacVu /XML $fileXml /F 2>&1
$maLoi = $LASTEXITCODE
Remove-Item $fileXml -Force -ErrorAction SilentlyContinue

if ($maLoi -ne 0) {
  Write-Host '  [LOI] Khong dang ky duoc tac vu tu chay:'
  Write-Host ('        ' + ($ketQua -join ' '))
  Write-Host '        Thu chuot phai file Cai-dat.cmd -> Run as administrator.'
  exit 1
}
Write-Host '  Da dang ky tac vu tu chay.'

# Đọc lại đúng những thứ vừa đặt. Không tin là đã đúng, phải kiểm.
$chiTiet = schtasks /Query /TN $TenTacVu /XML 2>&1 | Out-String
$kiemTra = @(
  @{ ten = 'Chay duoc khi dung pin';         dat = ($chiTiet -match '<DisallowStartIfOnBatteries>false<') }
  @{ ten = 'Khong dung khi chuyen sang pin'; dat = ($chiTiet -match '<StopIfGoingOnBatteries>false<') }
  @{ ten = 'Khong gioi han thoi gian chay';  dat = ($chiTiet -match '<ExecutionTimeLimit>PT0S<') }
  @{ ten = 'Chay khi dang nhap Windows';     dat = ($chiTiet -match '<LogonTrigger>') }
  @{ ten = 'Tu hoi phuc moi 30 phut';        dat = ($chiTiet -match '<Interval>PT30M<') }
)
$hong = 0
foreach ($k in $kiemTra) {
  if ($k.dat) { Bao '[OK]  ' $k.ten } else { Bao '[HONG]' $k.ten; $hong++ }
}

if ($ChiKiemTra) {
  schtasks /Delete /TN $TenTacVu /F 2>&1 | Out-Null
  Bao '[OK]  ' 'Da xoa tac vu kiem thu'
  if ($hong -gt 0) { exit 1 }
  return
}

# Khởi động qua chính tác vụ vừa tạo, không dùng Start-Process. Hai cái lợi:
# đây là phép thử thật cho tác vụ, và app chạy dưới quyền của tác vụ
# (LeastPrivilege) chứ không thừa hưởng quyền quản trị của bộ cài.
schtasks /Run /TN $TenTacVu 2>&1 | Out-Null
Start-Sleep -Seconds 8
$dangChay = Get-WmiObject Win32_Process -Filter "Name='powershell.exe'" |
            Where-Object { $_.CommandLine -like '*KiemSoatMay.ps1*' }
if ($dangChay) {
  Bao '[OK]  ' 'App dang chay (icon hinh khien o khay he thong)'
} else {
  $hong++
  Bao '[HONG]' 'App khong khoi dong duoc. Chay tay de xem loi:'
  Write-Host ('           powershell -ExecutionPolicy Bypass -File "' +
              (Join-Path $ThuMuc 'KiemSoatMay.ps1') + '"')
}

if ($hong -gt 0) { exit 1 }
