#Requires -Version 5.1
<#
  Sinh android/version.json từ app/build.gradle. App đọc tệp này trên GitHub để
  biết có bản mới hay không, nên nó phải luôn khớp với APK vừa dựng.
#>
$goc = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradle = Get-Content (Join-Path $goc 'app\build.gradle') -Raw

$ma = [regex]::Match($gradle, 'versionCode\s+(\d+)').Groups[1].Value
$ten = [regex]::Match($gradle, "versionName\s+'([^']+)'").Groups[1].Value
if (-not $ma -or -not $ten) {
  Write-Host '  [LOI] Khong doc duoc versionCode/versionName tu build.gradle'
  exit 1
}

$tepJson = Join-Path $goc 'version.json'
$ghiChu = ''
if (Test-Path $tepJson) {
  try { $ghiChu = (Get-Content $tepJson -Raw -Encoding UTF8 | ConvertFrom-Json).ghiChu } catch { }
}

$json = [ordered]@{
  versionCode = [int]$ma
  versionName = $ten
  apk = 'https://github.com/hovidu86/ThoiGianBieu/raw/main/android/dist-apk/ThoiGianBieu.apk'
  ghiChu = $ghiChu
}
# KHÔNG dùng Out-File -Encoding utf8: PowerShell 5.1 sẽ chèn BOM, mà BOM đứng
# đầu tệp làm bộ đọc JSON bên Android ném lỗi ngay dòng đầu tiên.
$khongBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($tepJson, ($json | ConvertTo-Json -Depth 3), $khongBom)
Write-Host ('  version.json -> ' + $ten + ' (versionCode ' + $ma + ')')
