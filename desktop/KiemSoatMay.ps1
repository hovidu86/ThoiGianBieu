#Requires -Version 5.1
<#
=====================================================================
  Thời gian biểu · Module Kiểm soát máy (Windows)

  Tới giờ đã hẹn: tắt màn hình + khoá máy. Muốn dùng lại phải nhập mã
  dài tối thiểu 10 ký tự HAI LẦN, cách nhau 15 giây. Sau đó cứ mỗi 15
  phút lại khoá tiếp, cho tới giờ kết thúc. Trước mỗi lần khoá đều có
  cảnh báo ở các mốc 15 / 5 / 1 phút.

  Mọi con số trên đều sửa được trong cửa sổ Cài đặt (chuột phải vào
  icon ở khay hệ thống). Cấu hình lưu tại:
      %LOCALAPPDATA%\ThoiGianBieu\may.json
  Mã mở khoá chỉ lưu dạng băm SHA-256, không lưu mã gốc.
=====================================================================
#>
param(
  [switch]$CaiDat,   # mở thẳng cửa sổ cài đặt
  [switch]$KhoaThu   # khoá ngay lập tức, để thử
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName PresentationFramework
Add-Type -AssemblyName PresentationCore
Add-Type -AssemblyName WindowsBase
Add-Type -AssemblyName System.Xaml
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName Microsoft.VisualBasic

Add-Type -Namespace TGB -Name Win -MemberDefinition @'
[DllImport("user32.dll")]
public static extern IntPtr SendMessage(IntPtr hWnd, uint Msg, IntPtr wParam, IntPtr lParam);
[DllImport("user32.dll")]
public static extern bool LockWorkStation();

[DllImport("user32.dll", SetLastError=true)]
static extern IntPtr OpenInputDesktop(uint dwFlags, bool fInherit, uint dwDesiredAccess);
[DllImport("user32.dll")]
static extern bool CloseDesktop(IntPtr hDesktop);

// Đang ở màn hình khoá / màn hình bảo mật của Windows hay không. Khi đó tiến
// trình của người dùng không mở được "input desktop", OpenInputDesktop trả về
// null. Không cần đăng ký sự kiện phiên (vốn chạy trên luồng khác, dễ hỏng).
public static bool ManHinhKhoaDangHien() {
  IntPtr h = OpenInputDesktop(0, false, 0x0001 /*DESKTOP_READOBJECTS*/);
  if (h == IntPtr.Zero) return true;
  CloseDesktop(h);
  return false;
}
'@

# ==================== ĐƯỜNG DẪN & CẤU HÌNH ====================

$script:ThuMuc      = Join-Path $env:LOCALAPPDATA 'ThoiGianBieu'
$script:FileCauHinh = Join-Path $script:ThuMuc 'may.json'
$script:FileNhatKy  = Join-Path $script:ThuMuc 'nhat-ky-may.csv'
if (-not (Test-Path $script:ThuMuc)) {
  New-Item -ItemType Directory -Path $script:ThuMuc -Force | Out-Null
}

$script:MacDinh = [ordered]@{
  gioKhoa         = '22:45'   # tới giờ này là khoá lần đầu
  gioKetThuc      = '05:30'   # sau giờ này thôi khoá, chờ tối hôm sau
  lapLaiPhut      = 15        # mở khoá xong, bao lâu thì khoá lại
  canhBaoPhut     = @(15, 5, 1)
  khoangCachGiay  = 15        # hai lần nhập mã phải cách nhau chừng này
  doDaiMaToiThieu = 10
  maBam           = ''        # SHA-256 của mã mở khoá
  khoaWindows     = $true     # có khoá luôn phiên Windows không
}

function Ghi-NhatKy([string]$viec, [string]$chiTiet) {
  try {
    if (-not (Test-Path $script:FileNhatKy)) {
      'thoi_diem,su_kien,chi_tiet' | Out-File $script:FileNhatKy -Encoding utf8
    }
    $dong = '{0},{1},"{2}"' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $viec, ($chiTiet -replace '"', "'")
    Add-Content -Path $script:FileNhatKy -Value $dong -Encoding UTF8
  } catch { }
}

function Doc-CauHinh {
  $c = [ordered]@{}
  foreach ($k in $script:MacDinh.Keys) { $c[$k] = $script:MacDinh[$k] }
  if (Test-Path $script:FileCauHinh) {
    try {
      $doc = Get-Content $script:FileCauHinh -Raw -Encoding UTF8 | ConvertFrom-Json
      foreach ($k in @($c.Keys)) {
        $p = $doc.PSObject.Properties[$k]
        if ($p -and $null -ne $p.Value) { $c[$k] = $p.Value }
      }
    } catch {
      Ghi-NhatKy 'loi-cau-hinh' $_.Exception.Message
    }
  }
  $c['canhBaoPhut'] = @($c['canhBaoPhut'] |
    ForEach-Object { [int]$_ } |
    Where-Object { $_ -gt 0 } |
    Sort-Object -Descending -Unique)
  return $c
}

function Luu-CauHinh($c) {
  ($c | ConvertTo-Json -Depth 5) | Out-File -FilePath $script:FileCauHinh -Encoding utf8 -Force
}

function Bam-Ma([string]$ma) {
  if ([string]::IsNullOrEmpty($ma)) { return '' }
  $sha = [System.Security.Cryptography.SHA256]::Create()
  try {
    $bytes = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($ma))
    return (($bytes | ForEach-Object { $_.ToString('x2') }) -join '')
  } finally { $sha.Dispose() }
}

# ==================== TÍNH MỐC THỜI GIAN ====================

function Lay-Moc([string]$hhmm, [datetime]$goc) {
  $p = ([string]$hhmm).Split(':')
  return $goc.Date.AddHours([int]$p[0]).AddMinutes([int]$p[1])
}

<# Đang nằm trong khoảng "giờ khoá -> giờ kết thúc" hay không.
   Khoảng này thường vắt qua nửa đêm, ví dụ 22:45 -> 05:30. #>
function Trong-KhoangKhoa([datetime]$luc) {
  $batDau  = Lay-Moc $script:CH.gioKhoa    $luc
  $ketThuc = Lay-Moc $script:CH.gioKetThuc $luc
  if ($ketThuc -le $batDau) {
    return ($luc -ge $batDau) -or ($luc -lt $ketThuc)
  }
  return ($luc -ge $batDau) -and ($luc -lt $ketThuc)
}

function Moc-KhoaDauTien([datetime]$tu) {
  $batDau = Lay-Moc $script:CH.gioKhoa $tu
  if ($tu -lt $batDau) { return $batDau }
  return $batDau.AddDays(1)
}

<# Đặt mốc khoá kế tiếp, xoá dấu các cảnh báo đã bắn của mốc cũ. #>
function Dat-MocKhoa([datetime]$moc) {
  $script:MocKhoa = $moc
  $script:DaCanhBao = @{}

  # Mốc cảnh báo nào đã nằm trong quá khứ ngay lúc lên lịch thì coi như đã bắn.
  # Thiếu bước này, mở khoá lúc 23:00 với lịch lặp 15 phút sẽ nổ liền một lúc
  # cả ba cảnh báo 15, 5 và 1 phút cho lần khoá chỉ còn 15 phút nữa.
  $bayGio = Get-Date
  foreach ($m in $script:CH.canhBaoPhut) {
    if ($bayGio -ge $moc.AddMinutes(-$m)) { $script:DaCanhBao['w' + $m] = $true }
  }
  if ($script:Tray) {
    $script:Tray.Text = 'Thời gian biểu - khoá lúc ' + $moc.ToString('HH:mm')
  }
  if ($script:MucTrangThai) {
    $script:MucTrangThai.Text = 'Lần khoá kế tiếp: ' + $moc.ToString('HH:mm dd/MM')
  }
}

function Tat-ManHinh {
  try {
    [void][TGB.Win]::SendMessage([IntPtr]0xFFFF, 0x0112, [IntPtr]0xF170, [IntPtr]2)
  } catch { }
}

# ==================== CỬA SỔ CẢNH BÁO ====================

$script:XamlCanhBao = @'
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        WindowStyle="None" ResizeMode="NoResize" Topmost="True" ShowInTaskbar="False"
        AllowsTransparency="True" Background="Transparent"
        SizeToContent="WidthAndHeight" WindowStartupLocation="Manual">
  <Border Background="#F21E293B" CornerRadius="14" Padding="26,18"
          BorderBrush="#F59E0B" BorderThickness="2">
    <StackPanel>
      <TextBlock x:Name="txtTieuDe" Text="Sắp khoá máy" FontFamily="Segoe UI" FontSize="22"
                 FontWeight="SemiBold" Foreground="#FBBF24"/>
      <TextBlock x:Name="txtNoiDung" Text="" FontFamily="Segoe UI" FontSize="15"
                 Foreground="#E2E8F0" Margin="0,8,0,0" MaxWidth="460" TextWrapping="Wrap"/>
    </StackPanel>
  </Border>
</Window>
'@

function Hien-CanhBao([int]$conPhut) {
  try {
    $w = [System.Windows.Markup.XamlReader]::Parse($script:XamlCanhBao)
    if ($conPhut -le 1) {
      $noi = 'Còn 1 phút. Lưu lại việc đang làm ngay bây giờ.'
    } else {
      $noi = 'Còn ' + $conPhut + ' phút nữa máy sẽ tắt màn hình và khoá. Mở lại phải nhập mã hai lần, cách nhau ' + $script:CH.khoangCachGiay + ' giây.'
    }
    $w.FindName('txtNoiDung').Text = $noi
    $w.FindName('txtTieuDe').Text  = 'Khoá máy sau ' + $conPhut + ' phút'

    $man = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
    $w.Show()
    $w.Left = $man.Left + ($man.Width - $w.ActualWidth) / 2
    $w.Top  = $man.Top + 60
    $w.Topmost = $true

    $hetGio = New-Object System.Windows.Threading.DispatcherTimer
    $hetGio.Interval = [TimeSpan]::FromSeconds(12)
    $hetGio.Add_Tick({
      $this.Stop()
      try { $w.Close() } catch { }
    }.GetNewClosure())
    $hetGio.Start()

    [System.Media.SystemSounds]::Exclamation.Play()
  } catch {
    Ghi-NhatKy 'loi-canh-bao' $_.Exception.Message
  }
}

# ==================== CỬA SỔ KHOÁ ====================

$script:XamlKhoa = @'
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        WindowStyle="None" ResizeMode="NoResize" Topmost="True" ShowInTaskbar="False"
        WindowState="Maximized" Background="#050810">
  <Grid>
    <StackPanel VerticalAlignment="Center" HorizontalAlignment="Center" Width="620">
      <TextBlock x:Name="txtDongHo" Text="--:--" FontFamily="Segoe UI Light" FontSize="82"
                 Foreground="#334155" HorizontalAlignment="Center"/>
      <TextBlock Text="MÁY ĐÃ KHOÁ THEO THỜI GIAN BIỂU" FontFamily="Segoe UI" FontSize="17"
                 FontWeight="SemiBold" Foreground="#7DD3FC" HorizontalAlignment="Center"
                 Margin="0,14,0,0"/>
      <TextBlock x:Name="txtHuongDan" TextWrapping="Wrap" FontFamily="Segoe UI" FontSize="14"
                 Foreground="#94A3B8" HorizontalAlignment="Center" TextAlignment="Center"
                 Margin="0,14,0,0"/>

      <Border Background="#0F172A" CornerRadius="12" Padding="24" Margin="0,26,0,0"
              BorderBrush="#1E293B" BorderThickness="1">
        <StackPanel>
          <TextBlock Text="Bước 1 - nhập mã mở khoá" FontFamily="Segoe UI"
                     FontSize="13" Foreground="#E2E8F0"/>
          <PasswordBox x:Name="pw1" FontFamily="Consolas" FontSize="18" Padding="10,8"
                       Margin="0,8,0,0" Background="#1E293B" Foreground="#F8FAFC"
                       BorderBrush="#334155" BorderThickness="1"/>
          <Button x:Name="btn1" Content="Xác nhận lần 1" FontFamily="Segoe UI" FontSize="14"
                  Padding="14,8" Margin="0,10,0,0" HorizontalAlignment="Left"
                  Background="#2563EB" Foreground="White" BorderThickness="0" Cursor="Hand"/>

          <Border Background="#111C31" CornerRadius="8" Padding="12" Margin="0,18,0,0">
            <TextBlock x:Name="txtDem" Text="Chờ sau khi nhập đúng lần 1." FontFamily="Segoe UI"
                       FontSize="14" Foreground="#64748B" HorizontalAlignment="Center"/>
          </Border>

          <TextBlock x:Name="lblB2" Text="Bước 2 - nhập lại đúng mã đó" FontFamily="Segoe UI"
                     FontSize="13" Foreground="#475569" Margin="0,18,0,0"/>
          <PasswordBox x:Name="pw2" FontFamily="Consolas" FontSize="18" Padding="10,8"
                       Margin="0,8,0,0" Background="#131C2E" Foreground="#F8FAFC"
                       BorderBrush="#1E293B" BorderThickness="1" IsEnabled="False"/>
          <Button x:Name="btn2" Content="Mở khoá" FontFamily="Segoe UI" FontSize="14"
                  Padding="14,8" Margin="0,10,0,0" HorizontalAlignment="Left"
                  Background="#334155" Foreground="#94A3B8" BorderThickness="0"
                  IsEnabled="False" Cursor="Hand"/>
        </StackPanel>
      </Border>

      <TextBlock x:Name="txtTrangThai" Text="" FontFamily="Segoe UI" FontSize="14"
                 Foreground="#F87171" HorizontalAlignment="Center" Margin="0,16,0,0"
                 TextWrapping="Wrap" TextAlignment="Center"/>
    </StackPanel>
  </Grid>
</Window>
'@

$script:XamlPhu = @'
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        WindowStyle="None" ResizeMode="NoResize" Topmost="True" ShowInTaskbar="False"
        Background="#050810" WindowStartupLocation="Manual"/>
'@

<#
  Che nốt các màn hình phụ bằng cửa sổ đen.

  Screen.Bounds đo bằng pixel vật lý, còn WPF đặt cửa sổ theo DIP. Ở mức phóng
  to 150% mà gán thẳng số này thì cửa sổ che lệch chỗ và lòi ra một mảng màn
  hình vẫn dùng được. Phải quy đổi qua ma trận của chính cửa sổ khoá.
#>
function Che-ManHinhPhu($cuaSoGoc) {
  $script:CuaSoPhu = @()
  # Mỗi cửa sổ che dựng lên đều cướp tiêu điểm của cửa sổ khoá. Không dựng cờ
  # này thì mỗi màn hình phụ lại đẻ ra một dòng "né tránh" giả trong nhật ký,
  # làm hỏng đúng cái nhật ký dùng để phát hiện né tránh thật.
  $script:DangCheManHinh = $true

  $doi = $null
  try {
    $nguon = [System.Windows.PresentationSource]::FromVisual($cuaSoGoc)
    if ($nguon) { $doi = $nguon.CompositionTarget.TransformFromDevice }
  } catch { }

  foreach ($man in [System.Windows.Forms.Screen]::AllScreens) {
    if ($man.Primary) { continue }
    try {
      $w = [System.Windows.Markup.XamlReader]::Parse($script:XamlPhu)
      $tren = New-Object System.Windows.Point($man.Bounds.Left, $man.Bounds.Top)
      $duoi = New-Object System.Windows.Point($man.Bounds.Right, $man.Bounds.Bottom)
      if ($doi) {
        $tren = $doi.Transform($tren)
        $duoi = $doi.Transform($duoi)
      }
      $w.Left   = $tren.X
      $w.Top    = $tren.Y
      $w.Width  = $duoi.X - $tren.X
      $w.Height = $duoi.Y - $tren.Y
      $w.Show()
      $script:CuaSoPhu += $w
    } catch { }
  }

  $script:DangCheManHinh = $false
}

function Khoa-May {
  if ($script:DangKhoa) { return }
  if ($script:CH.maBam -eq '') { return }
  Ghi-NhatKy 'khoa' ('mốc ' + $script:MocKhoa.ToString('HH:mm'))

  # Dựng cửa sổ trước, dựng cờ DangKhoa sau. Đặt cờ sớm rồi giữa chừng hỏng là
  # app thôi khoá vĩnh viễn trong im lặng, vì mọi lần Khoa-May sau đó đều thoát
  # ngay ở dòng đầu. Hỏng thì vẫn phải tắt màn hình, chỉ mất phần đòi mã.
  try {
    $win = [System.Windows.Markup.XamlReader]::Parse($script:XamlKhoa)
  } catch {
    Ghi-NhatKy 'loi-khoa' ('không dựng được cửa sổ khoá: ' + $_.Exception.Message)
    Tat-ManHinh
    if ($script:CH.khoaWindows) { try { [void][TGB.Win]::LockWorkStation() } catch { } }
    Dat-MocKhoa ((Get-Date).AddMinutes([int]$script:CH.lapLaiPhut))
    return
  }
  $script:CuaSoKhoa = $win

  $pw1  = $win.FindName('pw1');  $pw2  = $win.FindName('pw2')
  $btn1 = $win.FindName('btn1'); $btn2 = $win.FindName('btn2')
  $lblB2        = $win.FindName('lblB2')
  $txtDem       = $win.FindName('txtDem')
  $txtTrangThai = $win.FindName('txtTrangThai')
  $txtDongHo    = $win.FindName('txtDongHo')

  $win.FindName('txtHuongDan').Text =
    'Nhập mã mở khoá, đợi ' + $script:CH.khoangCachGiay + ' giây, rồi nhập lại đúng mã đó lần nữa. ' +
    'Nhập sai bất kỳ lần nào là phải làm lại từ đầu.'

  # QUAN TRỌNG về phạm vi biến: mọi handler dưới đây đều .GetNewClosure(). Closure
  # tạo BÊN TRONG một hàm chỉ sao chép biến CỤC BỘ của hàm; biến $script: (dù đặt
  # ở đâu) KHÔNG lọt vào, và bên trong closure '$script:X' đọc ra $null.
  #  - Lỗi cũ 1: 'if ($script:BuocMo -ne 1) { return }' luôn đúng -> nút "Xác nhận
  #    lần 1" vô dụng. Nay dùng hashtable cục bộ $tt (sao chép theo THAM CHIẾU nên
  #    mọi closure sửa chung một đối tượng).
  #  - Lỗi cũ 2: '$script:CH.maBam' trong closure là $null -> so mã luôn sai, không
  #    ai mở khoá được. Nay bắt $script:CH vào biến cục bộ $ch trước khi dựng closure.
  $ch = $script:CH
  $tt = @{ buoc = 1; conLai = 0 }   # buoc: 1 = chờ lần 1, 2 = đếm ngược, 3 = chờ lần 2

  $datLai = {
    $tt.buoc = 1
    $tt.conLai = 0
    $pw1.Password = ''; $pw2.Password = ''
    $pw1.IsEnabled = $true;  $btn1.IsEnabled = $true
    $pw2.IsEnabled = $false; $btn2.IsEnabled = $false
    $btn2.Background = '#334155'; $btn2.Foreground = '#94A3B8'
    $lblB2.Foreground = '#475569'
    $txtDem.Text = 'Chờ sau khi nhập đúng lần 1.'
    $pw1.Focus() | Out-Null
  }.GetNewClosure()

  # Đồng hồ và đếm ngược dùng chung một nhịp 1 giây
  $nhip = New-Object System.Windows.Threading.DispatcherTimer
  $nhip.Interval = [TimeSpan]::FromSeconds(1)
  $nhip.Add_Tick({
    try {
      $txtDongHo.Text = (Get-Date).ToString('HH:mm')
      if ($tt.buoc -eq 2) {
        $tt.conLai = $tt.conLai - 1
        if ($tt.conLai -gt 0) {
          $txtDem.Text = 'Chờ thêm ' + $tt.conLai + ' giây nữa mới được nhập lần 2...'
        } else {
          $tt.buoc = 3
          $txtDem.Text = 'Được rồi. Nhập lại mã lần 2.'
          $lblB2.Foreground = '#E2E8F0'
          $pw2.IsEnabled = $true
          $btn2.IsEnabled = $true
          $btn2.Background = '#16A34A'
          $btn2.Foreground = 'White'
          $pw2.Focus() | Out-Null
        }
      }
    } catch { Ghi-NhatKy 'loi-nhip-khoa' $_.Exception.Message }
  }.GetNewClosure())
  $nhip.Start()
  $script:NhipKhoa = $nhip

  $btn1.Add_Click({
    if ($tt.buoc -ne 1) { return }
    if ((Bam-Ma $pw1.Password) -eq $ch.maBam) {
      $tt.buoc = 2
      $tt.conLai = [int]$ch.khoangCachGiay
      $pw1.IsEnabled = $false; $btn1.IsEnabled = $false
      $txtTrangThai.Text = ''
      $txtDem.Text = 'Chờ thêm ' + $tt.conLai + ' giây nữa mới được nhập lần 2...'
    } else {
      $txtTrangThai.Text = 'Sai mã. Nhập lại.'
      Ghi-NhatKy 'sai-ma' 'lần 1'
      $pw1.Password = ''
      [System.Media.SystemSounds]::Hand.Play()
    }
  }.GetNewClosure())

  $btn2.Add_Click({
    if ($tt.buoc -ne 3) { return }
    if ((Bam-Ma $pw2.Password) -eq $ch.maBam) {
      Mo-Khoa
    } else {
      $txtTrangThai.Text = 'Sai mã ở lần 2. Làm lại từ đầu, kể cả thời gian chờ.'
      Ghi-NhatKy 'sai-ma' 'lần 2 - phải làm lại từ đầu'
      [System.Media.SystemSounds]::Hand.Play()
      & $datLai
    }
  }.GetNewClosure())

  $bamPhim = {
    param($nguon, $bienCo)
    if ($bienCo.Key -eq 'Return') {
      if ($nguon -eq $pw1) {
        $btn1.RaiseEvent((New-Object System.Windows.RoutedEventArgs([System.Windows.Controls.Primitives.ButtonBase]::ClickEvent)))
      } else {
        $btn2.RaiseEvent((New-Object System.Windows.RoutedEventArgs([System.Windows.Controls.Primitives.ButtonBase]::ClickEvent)))
      }
    }
  }.GetNewClosure()
  $pw1.Add_KeyDown($bamPhim)
  $pw2.Add_KeyDown($bamPhim)

  # Không cho đóng bằng Alt+F4
  $win.Add_Closing({
    if (-not $script:ChoPhepDong) { $_.Cancel = $true }
  })
  # Bị mất tiêu điểm thì giành lại - nhưng KHÔNG gọi Activate() thẳng trong đây.
  # Activate() có thể làm cửa sổ deactivate tiếp, tạo vòng Deactivated -> Activate
  # -> Deactivated chạy đệ quy trên luồng giao diện tới tràn ngăn xếp: app treo
  # rồi thoát hẳn, chỉ một cú click là thoát được khoá. Thay bằng: đẩy việc giành
  # tiêu điểm ra khỏi ngăn xếp sự kiện qua BeginInvoke, kèm cờ chống tái nhập.
  # Nếu người dùng tạo input liên tục làm đói hàng đợi, nhịp chính (mỗi 5 giây,
  # là HÀM nên đọc $script: sống) vẫn gọi lại Gianh-TieuDiem như lưới an toàn.
  $win.Add_Deactivated({
    if (-not $script:DangKhoa -or $script:DangCheManHinh) { return }

    # Chỉ BỎ QUA việc ghi nhật ký né tránh khi đang ở màn hình khoá Windows -
    # KHÔNG bỏ qua việc giành lại tiêu điểm. Nếu ManHinhKhoaDangHien() báo nhầm
    # là đang khoá mà ta lại thôi giành tiêu điểm thì hoá ra một cú click lách
    # được (fail-open). Giành lại tiêu điểm lúc thật sự khoá thì cũng vô hại
    # (Activate() không làm gì trên màn hình bảo mật).
    if (-not $script:PhienBiKhoa -and -not $script:DaGhiNeTranh) {
      $script:DaGhiNeTranh = $true
      Ghi-NhatKy 'ne-tranh' 'cửa sổ khoá bị mất tiêu điểm'
    }

    if ($script:DangGianhTieuDiem) { return }   # đã có một lượt đang chờ chạy
    $script:DangGianhTieuDiem = $true
    $script:CuaSoKhoa.Dispatcher.BeginInvoke(
      [System.Windows.Threading.DispatcherPriority]::Input,
      [action]{ Gianh-TieuDiem }) | Out-Null
  })

  $script:DangKhoa = $true
  $script:DaGhiNeTranh = $false
  $win.Show()
  Che-ManHinhPhu $win
  $win.Activate() | Out-Null
  $pw1.Focus() | Out-Null
  $txtDongHo.Text = (Get-Date).ToString('HH:mm')

  Tat-ManHinh
  if ($script:CH.khoaWindows) {
    # Bật cờ ngay để dòng "ne-tranh" đầu tiên (do chính LockWorkStation làm mất
    # tiêu điểm) không bị ghi. Cờ này CHỈ tắt việc GHI NHẬT KÝ, không tắt việc
    # giành tiêu điểm - nên đặt sai cũng không mở đường lách. Nhịp chính soát
    # lại bằng ManHinhKhoaDangHien() mỗi 5 giây.
    try { [void][TGB.Win]::LockWorkStation() } catch { }
    $script:PhienBiKhoa = $true
  }
}

# Giành lại tiêu điểm cho cửa sổ khoá. Là HÀM (không phải closure) nên mọi biến
# $script: đọc ra giá trị SỐNG. Gọi từ: nhánh BeginInvoke của Add_Deactivated, và
# nhịp chính mỗi 5 giây (lưới an toàn khi hàng đợi giao diện bị đói).
function Gianh-TieuDiem {
  $script:DangGianhTieuDiem = $false
  if (-not $script:DangKhoa) { return }
  if ($script:DangCheManHinh) { return }   # đang tự dựng cửa sổ che màn hình phụ
  if (-not $script:CuaSoKhoa) { return }
  # KHÔNG chặn theo $script:PhienBiKhoa: nếu đang khoá Windows thật thì Activate()
  # vô hại; nếu cờ báo nhầm thì vẫn phải giành lại được tiêu điểm.
  try {
    if (-not $script:CuaSoKhoa.IsActive) {
      $script:CuaSoKhoa.Topmost = $true
      $script:CuaSoKhoa.Activate() | Out-Null
    }
  } catch { }
}

function Mo-Khoa {
  Ghi-NhatKy 'mo-khoa' 'nhập đúng mã hai lần'
  # Hạ cờ trước khi đóng, nếu không sự kiện Deactivated lúc đóng lại tưởng là
  # người dùng né tránh và giành lại tiêu điểm của một cửa sổ đang đóng dở.
  $script:DangKhoa = $false
  $script:ChoPhepDong = $true
  $script:DangGianhTieuDiem = $false
  $script:PhienBiKhoa = $false
  try { $script:NhipKhoa.Stop() } catch { }
  foreach ($w in $script:CuaSoPhu) { try { $w.Close() } catch { } }
  $script:CuaSoPhu = @()
  try { $script:CuaSoKhoa.Close() } catch { }
  $script:CuaSoKhoa = $null
  $script:ChoPhepDong = $false
  $script:DangKhoa = $false

  $tiep = (Get-Date).AddMinutes([int]$script:CH.lapLaiPhut)
  if (-not (Trong-KhoangKhoa $tiep)) { $tiep = Moc-KhoaDauTien $tiep }
  Dat-MocKhoa $tiep
}

# ==================== CỬA SỔ CÀI ĐẶT ====================

$script:XamlCaiDat = @'
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="Kiểm soát máy - Cài đặt" Width="560" SizeToContent="Height"
        WindowStartupLocation="CenterScreen" ResizeMode="NoResize" Background="#0F172A">
  <StackPanel Margin="26">
    <TextBlock Text="Kiểm soát máy" FontFamily="Segoe UI" FontSize="24" FontWeight="SemiBold"
               Foreground="#F8FAFC"/>
    <TextBlock Text="Thời gian biểu - module Windows" FontFamily="Segoe UI" FontSize="13"
               Foreground="#64748B" Margin="0,2,0,18"/>

    <TextBlock Text="Giờ khoá lần đầu (HH:mm)" FontFamily="Segoe UI" FontSize="13" Foreground="#94A3B8"/>
    <TextBox x:Name="txtGioKhoa" FontFamily="Consolas" FontSize="15" Padding="8,6" Margin="0,4,0,12"
             Background="#1E293B" Foreground="#F8FAFC" BorderBrush="#334155"/>

    <TextBlock Text="Giờ kết thúc - sau mốc này thôi khoá (HH:mm)" FontFamily="Segoe UI" FontSize="13" Foreground="#94A3B8"/>
    <TextBox x:Name="txtGioKetThuc" FontFamily="Consolas" FontSize="15" Padding="8,6" Margin="0,4,0,12"
             Background="#1E293B" Foreground="#F8FAFC" BorderBrush="#334155"/>

    <TextBlock Text="Mở khoá xong thì bao lâu khoá lại (phút)" FontFamily="Segoe UI" FontSize="13" Foreground="#94A3B8"/>
    <TextBox x:Name="txtLapLai" FontFamily="Consolas" FontSize="15" Padding="8,6" Margin="0,4,0,12"
             Background="#1E293B" Foreground="#F8FAFC" BorderBrush="#334155"/>

    <TextBlock Text="Cảnh báo trước bao nhiêu phút - cách nhau bởi dấu phẩy" FontFamily="Segoe UI" FontSize="13" Foreground="#94A3B8"/>
    <TextBox x:Name="txtCanhBao" FontFamily="Consolas" FontSize="15" Padding="8,6" Margin="0,4,0,12"
             Background="#1E293B" Foreground="#F8FAFC" BorderBrush="#334155"/>

    <TextBlock Text="Khoảng cách giữa hai lần nhập mã (giây)" FontFamily="Segoe UI" FontSize="13" Foreground="#94A3B8"/>
    <TextBox x:Name="txtKhoangCach" FontFamily="Consolas" FontSize="15" Padding="8,6" Margin="0,4,0,12"
             Background="#1E293B" Foreground="#F8FAFC" BorderBrush="#334155"/>

    <TextBlock x:Name="lblMa" Text="Đổi mã mở khoá - nhập hai lần cho khớp" FontFamily="Segoe UI" FontSize="13" Foreground="#94A3B8"/>
    <PasswordBox x:Name="pwMa" FontFamily="Consolas" FontSize="15" Padding="8,6" Margin="0,4,0,4"
                 Background="#1E293B" Foreground="#F8FAFC" BorderBrush="#334155"/>
    <PasswordBox x:Name="pwMa2" FontFamily="Consolas" FontSize="15" Padding="8,6" Margin="0,0,0,4"
                 Background="#1E293B" Foreground="#F8FAFC" BorderBrush="#334155"/>
    <TextBlock x:Name="txtGoiYMa" Text="Để trống hai ô này nếu không muốn đổi mã." FontFamily="Segoe UI"
               FontSize="12" Foreground="#64748B" Margin="0,0,0,12"/>

    <CheckBox x:Name="chkKhoaWin" Content="Khoá luôn phiên Windows (phải đăng nhập lại)"
              FontFamily="Segoe UI" FontSize="13" Foreground="#CBD5E1" Margin="0,0,0,16"/>

    <TextBlock x:Name="txtLoi" Text="" FontFamily="Segoe UI" FontSize="13" Foreground="#F87171"
               TextWrapping="Wrap" Margin="0,0,0,10"/>

    <StackPanel Orientation="Horizontal">
      <Button x:Name="btnLuu" Content="Lưu cài đặt" FontFamily="Segoe UI" FontSize="14" Padding="18,9"
              Background="#2563EB" Foreground="White" BorderThickness="0" Cursor="Hand"/>
      <Button x:Name="btnHuy" Content="Đóng" FontFamily="Segoe UI" FontSize="14" Padding="18,9"
              Margin="10,0,0,0" Background="#1E293B" Foreground="#94A3B8" BorderThickness="0" Cursor="Hand"/>
    </StackPanel>
  </StackPanel>
</Window>
'@

<#
  Kiểm tra dữ liệu nhập ở cửa sổ Cài đặt. Tách hẳn khỏi giao diện để kiểm thử
  được độc lập — trước đây phần này nằm trong sự kiện bấm nút, hỏng thì im lặng.
  Trả về @{ loi = @(...); giaTri = @{...} }
#>
function Kiem-CaiDat([string]$gioKhoa, [string]$gioKet, [string]$lapLai,
                     [string]$canhBao, [string]$khoang,
                     [string]$ma, [string]$ma2, [bool]$daCoMa) {
  $loi = @()

  if ($gioKhoa -notmatch '^([01][0-9]|2[0-3]):[0-5][0-9]$') {
    $loi += 'Giờ khoá phải đúng dạng HH:mm, ví dụ 22:45. Đang nhập: "' + $gioKhoa + '"'
  }
  if ($gioKet -notmatch '^([01][0-9]|2[0-3]):[0-5][0-9]$') {
    $loi += 'Giờ kết thúc phải đúng dạng HH:mm, ví dụ 05:30. Đang nhập: "' + $gioKet + '"'
  }
  if ($lapLai -notmatch '^[0-9]+$' -or [int]('0' + $lapLai) -lt 1) {
    $loi += 'Số phút lặp lại phải là số nguyên từ 1 trở lên.'
  }
  if ($khoang -notmatch '^[0-9]+$' -or [int]('0' + $khoang) -lt 1) {
    $loi += 'Khoảng cách hai lần nhập phải là số giây từ 1 trở lên.'
  }

  $mocCanhBao = @()
  foreach ($m in ($canhBao -split ',')) {
    $m = $m.Trim()
    if ($m -eq '') { continue }
    if ($m -notmatch '^[0-9]+$') {
      $loi += 'Mốc cảnh báo "' + $m + '" không phải số phút.'
      continue
    }
    $mocCanhBao += [int]$m
  }

  $maBamMoi = $null
  if ($ma -ne '' -or $ma2 -ne '' -or -not $daCoMa) {
    if ($ma.Length -lt [int]$script:CH.doDaiMaToiThieu) {
      $loi += 'Mã mở khoá phải dài tối thiểu ' + $script:CH.doDaiMaToiThieu +
              ' ký tự. Đang nhập ' + $ma.Length + ' ký tự.'
    } elseif ($ma -ne $ma2) {
      $loi += 'Hai ô mã không khớp nhau. Gõ lại cả hai ô.'
    } else {
      $maBamMoi = Bam-Ma $ma
    }
  }

  $soLapLai = 0; if ($lapLai -match '^[0-9]+$') { $soLapLai = [int]$lapLai }
  $soKhoang = 0; if ($khoang -match '^[0-9]+$') { $soKhoang = [int]$khoang }

  return @{
    loi = $loi
    giaTri = @{
      gioKhoa        = $gioKhoa
      gioKetThuc     = $gioKet
      lapLaiPhut     = $soLapLai
      khoangCachGiay = $soKhoang
      canhBaoPhut    = @($mocCanhBao | Sort-Object -Descending -Unique)
      maBam          = $maBamMoi   # $null = giữ nguyên mã cũ
    }
  }
}

<# Ghi cấu hình mới xuống đĩa và tính lại mốc khoá kế tiếp. #>
function Ap-CaiDat($giaTri, [bool]$khoaWindows) {
  $script:CH.gioKhoa        = $giaTri.gioKhoa
  $script:CH.gioKetThuc     = $giaTri.gioKetThuc
  $script:CH.lapLaiPhut     = $giaTri.lapLaiPhut
  $script:CH.khoangCachGiay = $giaTri.khoangCachGiay
  $script:CH.canhBaoPhut    = $giaTri.canhBaoPhut
  $script:CH.khoaWindows    = $khoaWindows
  if ($null -ne $giaTri.maBam) { $script:CH.maBam = $giaTri.maBam }

  Luu-CauHinh $script:CH
  Ghi-NhatKy 'sua-cai-dat' ('khoá ' + $giaTri.gioKhoa + ', kết thúc ' + $giaTri.gioKetThuc +
                            ', lặp ' + $giaTri.lapLaiPhut + ' phút, cảnh báo ' +
                            ($giaTri.canhBaoPhut -join '/'))

  $now = Get-Date
  if (Trong-KhoangKhoa $now) { Dat-MocKhoa $now.AddMinutes(1) }
  else { Dat-MocKhoa (Moc-KhoaDauTien $now) }

  # Trả về mốc vừa đặt. Người gọi (sự kiện bấm Lưu) là một .GetNewClosure() nên
  # $script:MocKhoa bên trong nó là bản sao ĐÓNG BĂNG từ lúc mở cửa sổ, đọc thẳng
  # sẽ ra giờ cũ. Phải lấy qua giá trị trả về này.
  return $script:MocKhoa
}

function Mo-CaiDat([bool]$batBuocDatMa) {
  if ($script:DangKhoa) { return }
  if ($script:CuaSoCaiDat) {
    try { $script:CuaSoCaiDat.Activate() | Out-Null; return } catch { }
  }

  $w = [System.Windows.Markup.XamlReader]::Parse($script:XamlCaiDat)
  $script:CuaSoCaiDat = $w

  $w.FindName('txtGioKhoa').Text      = $script:CH.gioKhoa
  $w.FindName('txtGioKetThuc').Text   = $script:CH.gioKetThuc
  $w.FindName('txtLapLai').Text       = [string]$script:CH.lapLaiPhut
  $w.FindName('txtCanhBao').Text      = ($script:CH.canhBaoPhut -join ', ')
  $w.FindName('txtKhoangCach').Text   = [string]$script:CH.khoangCachGiay
  $w.FindName('chkKhoaWin').IsChecked = [bool]$script:CH.khoaWindows
  $txtLoi = $w.FindName('txtLoi')

  if ($batBuocDatMa) {
    $w.FindName('lblMa').Text = 'Đặt mã mở khoá - tối thiểu ' + $script:CH.doDaiMaToiThieu + ' ký tự (nhập hai lần)'
    $w.FindName('txtGoiYMa').Text = 'Bắt buộc: chưa có mã thì app chưa khoá gì cả. Chọn chuỗi dài, khó gõ vội.'
    $txtLoi.Text = 'Chưa đặt mã mở khoá.'
  }

  # Bắt $script:CH vào biến cục bộ: các sự kiện bấm nút bên dưới đều .GetNewClosure()
  # nên không đọc được biến $script: (xem ghi chú dài trong Khoa-May).
  $ch = $script:CH

  $w.FindName('btnLuu').Add_Click({
    # Bọc toàn bộ trong try: lỗi trong sự kiện WPF vốn bị nuốt lặng, người dùng
    # bấm Lưu thấy không có gì xảy ra và tưởng đã lưu xong.
    try {
      $kq = Kiem-CaiDat `
              $w.FindName('txtGioKhoa').Text.Trim() `
              $w.FindName('txtGioKetThuc').Text.Trim() `
              $w.FindName('txtLapLai').Text.Trim() `
              $w.FindName('txtCanhBao').Text.Trim() `
              $w.FindName('txtKhoangCach').Text.Trim() `
              $w.FindName('pwMa').Password `
              $w.FindName('pwMa2').Password `
              ($ch.maBam -ne '')

      if ($kq.loi.Count -gt 0) {
        $txtLoi.Text = ($kq.loi -join '  ')
        [System.Windows.Forms.MessageBox]::Show(
          ($kq.loi -join "`r`n`r`n"), 'Chưa lưu được',
          [System.Windows.Forms.MessageBoxButtons]::OK,
          [System.Windows.Forms.MessageBoxIcon]::Warning) | Out-Null
        return
      }

      $mocMoi = Ap-CaiDat $kq.giaTri ([bool]$w.FindName('chkKhoaWin').IsChecked)

      $w.Close()   # Add_Closed lo việc dọn $script:CuaSoCaiDat
      Bao-Khay 'Đã lưu' ('Lần khoá kế tiếp: ' + $mocMoi.ToString('HH:mm dd/MM'))
      [System.Windows.Forms.MessageBox]::Show(
        'Đã lưu xong.' + "`r`n`r`n" +
        'Lần khoá kế tiếp: ' + $mocMoi.ToString('HH:mm dd/MM') + "`r`n" +
        'Cảnh báo trước đó: ' + ($kq.giaTri.canhBaoPhut -join ', ') + ' phút.',
        'Kiểm soát máy',
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Information) | Out-Null
    } catch {
      Ghi-NhatKy 'loi-luu' $_.Exception.Message
      [System.Windows.Forms.MessageBox]::Show(
        'Lỗi khi lưu: ' + $_.Exception.Message, 'Kiểm soát máy',
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
    }
  }.GetNewClosure())

  $w.FindName('btnHuy').Add_Click({
    if ($ch.maBam -eq '') {
      $txtLoi.Text = 'Phải đặt mã mở khoá trước đã, nếu không app không khoá được gì.'
      return
    }
    $w.Close()
  }.GetNewClosure())

  # Nút Đóng đã chặn khi chưa có mã, nhưng dấu X trên thanh tiêu đề thì không.
  # Bấm X lúc đó là app thành vô dụng mà không ai nhắc lấy một câu.
  $w.Add_Closing({
    if ($script:CH.maBam -eq '') {
      [System.Windows.Forms.MessageBox]::Show(
        'Chưa đặt mã mở khoá, nên app sẽ KHÔNG khoá gì cả.' + "`r`n`r`n" +
        'Mở lại bất cứ lúc nào: chuột phải icon hình khiên ở khay hệ thống, chọn Cài đặt.',
        'Kiểm soát máy',
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Warning) | Out-Null
    }
  })

  $w.Add_Closed({ $script:CuaSoCaiDat = $null })
  $w.Show()
  $w.Activate() | Out-Null
}

# ==================== KHAY HỆ THỐNG ====================

function Bao-Khay([string]$tieuDe, [string]$noiDung) {
  try {
    $script:Tray.BalloonTipTitle = $tieuDe
    $script:Tray.BalloonTipText  = $noiDung
    $script:Tray.ShowBalloonTip(4000)
  } catch { }
}

function Hoi-MaDeThoat {
  $ma = [Microsoft.VisualBasic.Interaction]::InputBox(
    'Nhập mã mở khoá để tắt hẳn Kiểm soát máy.', 'Thoát Kiểm soát máy', '')
  if ($ma -eq '') { return }
  if ((Bam-Ma $ma) -eq $script:CH.maBam) {
    Ghi-NhatKy 'thoat' 'người dùng tắt app bằng mã'
    try { $script:Tray.Visible = $false; $script:Tray.Dispose() } catch { }
    [System.Windows.Threading.Dispatcher]::CurrentDispatcher.InvokeShutdown()
  } else {
    Ghi-NhatKy 'sai-ma' 'khi thoát app'
    [System.Windows.Forms.MessageBox]::Show('Sai mã.', 'Kiểm soát máy') | Out-Null
  }
}

function Dung-Khay {
  $tray = New-Object System.Windows.Forms.NotifyIcon
  $tray.Icon = [System.Drawing.SystemIcons]::Shield
  $tray.Visible = $true
  $tray.Text = 'Thời gian biểu - Kiểm soát máy'

  $menu = New-Object System.Windows.Forms.ContextMenuStrip

  $mTrangThai = $menu.Items.Add('Lần khoá kế tiếp: --:--')
  $mTrangThai.Enabled = $false
  $menu.Items.Add('-') | Out-Null

  $mCaiDat = $menu.Items.Add('Cài đặt...')
  $mCaiDat.Add_Click({ Mo-CaiDat $false })

  $mKhoaNgay = $menu.Items.Add('Khoá ngay bây giờ')
  $mKhoaNgay.Add_Click({ Khoa-May })

  $mNhatKy = $menu.Items.Add('Mở nhật ký')
  $mNhatKy.Add_Click({
    if (Test-Path $script:FileNhatKy) { Start-Process notepad.exe $script:FileNhatKy }
    else { Bao-Khay 'Nhật ký' 'Chưa có gì được ghi.' }
  })

  $menu.Items.Add('-') | Out-Null
  $mThoat = $menu.Items.Add('Thoát (cần mã)')
  $mThoat.Add_Click({ Hoi-MaDeThoat })

  $tray.ContextMenuStrip = $menu
  $tray.Add_DoubleClick({ Mo-CaiDat $false })

  $script:Tray = $tray
  $script:MucTrangThai = $mTrangThai
}

# ==================== VÒNG LẶP CHÍNH ====================

function Nhip-Chinh {
  if ($script:DangKhoa) {
    # Đang khoá: lưới an toàn cho nhánh sự kiện Add_Deactivated - luôn giành lại
    # tiêu điểm. Đồng thời soát lại $script:PhienBiKhoa (chỉ ảnh hưởng việc GHI
    # nhật ký né tránh) bằng cách hỏi thẳng hệ thống thay vì suy từ sự kiện.
    try { $script:PhienBiKhoa = [bool][TGB.Win]::ManHinhKhoaDangHien() } catch { }
    Gianh-TieuDiem
    return
  }
  $script:PhienBiKhoa = $false
  if ($script:CH.maBam -eq '') {
    # Không có mã thì app không khoá gì cả. Nói thẳng ra ở khay hệ thống,
    # đừng để im lặng như thể đang hoạt động bình thường.
    if ($script:MucTrangThai) { $script:MucTrangThai.Text = 'CHƯA ĐẶT MÃ - app chưa khoá gì cả' }
    if ($script:Tray) { $script:Tray.Text = 'Kiểm soát máy - CHƯA ĐẶT MÃ' }

    # Nhắc lại mỗi 10 phút, nếu không thì đóng cửa sổ Cài đặt một lần là quên luôn.
    if ($null -eq $script:LanNhacCuoi -or
        ((Get-Date) - $script:LanNhacCuoi).TotalMinutes -ge 10) {
      $script:LanNhacCuoi = Get-Date
      Bao-Khay 'Chưa đặt mã mở khoá' 'App chưa khoá gì cả. Chuột phải icon khiên rồi chọn Cài đặt.'
    }
    return
  }

  $now = Get-Date

  foreach ($m in $script:CH.canhBaoPhut) {
    $khoa = 'w' + $m
    if ($script:DaCanhBao.ContainsKey($khoa)) { continue }
    $moc = $script:MocKhoa.AddMinutes(-$m)
    if ($now -ge $moc -and $now -lt $script:MocKhoa) {
      $script:DaCanhBao[$khoa] = $true
      Hien-CanhBao $m
    }
  }

  if ($now -ge $script:MocKhoa) {
    if (Trong-KhoangKhoa $now) { Khoa-May }
    else { Dat-MocKhoa (Moc-KhoaDauTien $now) }
    return
  }

  if ($script:MucTrangThai) {
    $script:MucTrangThai.Text = 'Lần khoá kế tiếp: ' + $script:MocKhoa.ToString('HH:mm dd/MM')
  }
}

# ==================== KHỞI ĐỘNG ====================

$script:DangKhoa          = $false
$script:ChoPhepDong       = $false
$script:CuaSoPhu          = @()
$script:CuaSoKhoa         = $null
$script:CuaSoCaiDat       = $null
$script:Tray              = $null
$script:MucTrangThai      = $null
$script:DaCanhBao         = @{}
$script:MocKhoa           = (Get-Date).AddYears(1)
$script:DangGianhTieuDiem = $false   # đang có một lượt giành lại tiêu điểm chờ chạy
# Bật ngay trước LockWorkStation(); tắt khi cửa sổ khoá được kích hoạt lại (người
# dùng đăng nhập Windows xong), khi mở khoá, hoặc ở nhịp chính lúc không khoá.
# Trong lúc bật thì cửa sổ khoá mất tiêu điểm KHÔNG bị tính là né tránh.
$script:PhienBiKhoa       = $false
$script:CH = Doc-CauHinh

# Một lỗi trên luồng giao diện SAU khi Dispatcher.Run() chạy mà không ai bắt sẽ
# làm vòng lặp thoát, kéo theo cả app - khoá mất tác dụng trong im lặng. Bắt ở
# đây, ghi nhật ký (có kìm nhịp để một lỗi lặp lại không spam file), rồi cho app
# chạy tiếp. (Đoạn khởi động ĐỒNG BỘ bên dưới chạy trước Run() nên không được
# lưới này che - nó có try/catch riêng.)
$script:LoiGiaoDienCuoi = @{ msg = ''; luc = [datetime]::MinValue }
try {
  [System.Windows.Threading.Dispatcher]::CurrentDispatcher.add_UnhandledException({
    param($nguon, $bienCo)
    try {
      $m = $bienCo.Exception.GetType().Name + ': ' + $bienCo.Exception.Message
      if ($m -ne $script:LoiGiaoDienCuoi.msg -or
          ((Get-Date) - $script:LoiGiaoDienCuoi.luc).TotalSeconds -ge 30) {
        $script:LoiGiaoDienCuoi = @{ msg = $m; luc = Get-Date }
        Ghi-NhatKy 'loi-giao-dien' $m
      }
    } catch { }
    $bienCo.Handled = $true
  })
} catch { }

# Chỉ cho một bản chạy cùng lúc. Giữ tham chiếu ở phạm vi script, nếu để biến
# cục bộ thì bộ dọn rác thu hồi mutex và bản thứ hai lại chạy được.
#
# Tác vụ tự chạy gọi lại file này mỗi 30 phút để tự hồi phục nếu app bị tắt.
# Lần gọi nào gặp bản đang chạy thì phải thoát LẶNG LẼ — báo hộp thoại ở đây
# là cứ nửa tiếng lại nhảy một cái pop-up.
$moiTao = $false
$script:MotBanDuyNhat = New-Object System.Threading.Mutex($true, 'Global\ThoiGianBieu-KiemSoatMay', [ref]$moiTao)
if (-not $moiTao) { return }

Dung-Khay
Ghi-NhatKy 'khoi-dong' ('PowerShell ' + $PSVersionTable.PSVersion)

# Đoạn này chạy TRƯỚC Dispatcher.Run() nên lưới UnhandledException chưa che được.
# Một lỗi ở đây mà không bắt là app tắt ngay, chưa kịp vào vòng lặp chính. Việc
# quan trọng nhất - đặt mốc khoá kế tiếp - làm riêng và bắt lỗi riêng, để phần
# phụ (mở Cài đặt, cảnh báo, thông báo khay) có hỏng cũng không kéo theo.
try {
  if ($script:CH.maBam -eq '' -or $CaiDat) { Mo-CaiDat ($script:CH.maBam -eq '') }
} catch { Ghi-NhatKy 'loi-khoi-dong' ('mở cài đặt: ' + $_.Exception.Message) }

if ($script:CH.maBam -ne '') {
  try {
    $now = Get-Date
    if (Trong-KhoangKhoa $now) { Dat-MocKhoa $now.AddMinutes(1) }
    else { Dat-MocKhoa (Moc-KhoaDauTien $now) }
  } catch { Ghi-NhatKy 'loi-khoi-dong' ('đặt mốc khoá: ' + $_.Exception.Message) }

  try {
    if (Trong-KhoangKhoa (Get-Date)) { Hien-CanhBao 1 }
    Bao-Khay 'Kiểm soát máy đang chạy' ('Khoá lúc ' + $script:MocKhoa.ToString('HH:mm dd/MM'))
  } catch { Ghi-NhatKy 'loi-khoi-dong' ('cảnh báo/thông báo: ' + $_.Exception.Message) }
}

if ($KhoaThu) { try { Khoa-May } catch { Ghi-NhatKy 'loi-khoi-dong' ('khoá thử: ' + $_.Exception.Message) } }

$nhipChinh = New-Object System.Windows.Threading.DispatcherTimer
$nhipChinh.Interval = [TimeSpan]::FromSeconds(5)
$nhipChinh.Add_Tick({
  try { Nhip-Chinh } catch { Ghi-NhatKy 'loi-nhip' $_.Exception.Message }
})
$nhipChinh.Start()

[System.Windows.Threading.Dispatcher]::Run()
