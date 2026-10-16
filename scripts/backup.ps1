<#
.SYNOPSIS
  Sao lưu CSDL WashPro (mysqldump, nén .sql.gz) và ảnh đính kèm khi chạy trực tiếp trên Windows (không dùng Docker).

.DESCRIPTION
  Đọc thông tin kết nối từ washpro-b2b\.env (DB_URL, DB_USERNAME, DB_PASSWORD). Mật khẩu truyền cho mysqldump qua
  biến môi trường MYSQL_PWD, không xuất hiện trên dòng lệnh. Bản sao lưu cũ hơn -KeepDays ngày bị xóa.

  Chạy tự động mỗi ngày lúc 2 giờ sáng (Task Scheduler), mở PowerShell với quyền của tài khoản chạy ứng dụng:
    schtasks /Create /TN "WashPro sao luu" /SC DAILY /ST 02:00 /TR "powershell -NoProfile -ExecutionPolicy Bypass -File \"<đường dẫn>\scripts\backup.ps1\""

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\backup.ps1
  powershell -ExecutionPolicy Bypass -File scripts\backup.ps1 -OutDir D:\SaoLuu -KeepDays 30
#>
param(
    [string]$OutDir = (Join-Path $PSScriptRoot '..\backups'),
    [int]$KeepDays = 14,
    [string]$MySqlBin = ''
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$envFile = Join-Path $root 'washpro-b2b\.env'

# ----- Đọc cấu hình kết nối từ .env
$cfg = @{}
if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile -Encoding UTF8) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') { $cfg[$Matches[1]] = $Matches[2].Trim('"') }
    }
}
$url = if ($cfg.DB_URL) { $cfg.DB_URL } else { 'jdbc:mysql://localhost:3306/washpro_b2b' }
if ($url -notmatch '^jdbc:mysql://([^:/?]+)(?::(\d+))?/([^?]+)') { throw "DB_URL không đúng dạng jdbc:mysql://host:port/db: $url" }
$dbHost = $Matches[1]
$dbPort = if ($Matches[2]) { $Matches[2] } else { '3306' }
$dbName = $Matches[3]
$dbUser = if ($cfg.DB_USERNAME) { $cfg.DB_USERNAME } else { 'root' }
if (-not $cfg.DB_PASSWORD) { throw "Chưa có DB_PASSWORD trong $envFile" }

# ----- Tìm mysqldump
if (-not $MySqlBin) {
    $found = Get-Command mysqldump -ErrorAction SilentlyContinue
    if ($found) { $MySqlBin = Split-Path $found.Source }
    else {
        $MySqlBin = Get-ChildItem 'C:\Program Files\MySQL' -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'bin' } |
            Where-Object { Test-Path (Join-Path $_ 'mysqldump.exe') } | Select-Object -First 1
    }
}
$mysqldump = Join-Path $MySqlBin 'mysqldump.exe'
if (-not (Test-Path $mysqldump)) { throw 'Không tìm thấy mysqldump.exe, hãy truyền -MySqlBin "<thư mục bin của MySQL>"' }

New-Item -ItemType Directory -Force $OutDir | Out-Null
$OutDir = (Resolve-Path $OutDir).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$sql = Join-Path $OutDir "washpro-$stamp.sql"
$gz = "$sql.gz"

# ----- Dump (ghi thẳng ra file bằng --result-file: tránh PowerShell đổi bảng mã khi chuyển hướng >)
$env:MYSQL_PWD = $cfg.DB_PASSWORD
try {
    & $mysqldump -h $dbHost -P $dbPort -u $dbUser --single-transaction --routines --triggers `
        --set-gtid-purged=OFF --default-character-set=utf8mb4 "--result-file=$sql" $dbName
    if ($LASTEXITCODE -ne 0) { throw "mysqldump lỗi (mã $LASTEXITCODE)" }
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

# ----- Nén gzip (cùng định dạng với bản sao lưu Docker, khôi phục bằng backup/restore.sh hoặc gunzip | mysql)
$in = [IO.File]::OpenRead($sql)
$out = [IO.File]::Create("$gz.part")
$zip = New-Object IO.Compression.GZipStream($out, [IO.Compression.CompressionLevel]::Optimal)
try { $in.CopyTo($zip) } finally { $zip.Dispose(); $out.Dispose(); $in.Dispose() }
Move-Item "$gz.part" $gz
Remove-Item $sql

# ----- Ảnh đính kèm: chép thêm file mới (tên file ngẫu nhiên, không bao giờ bị sửa)
$uploads = if ($cfg.UPLOADS_DIR) { $cfg.UPLOADS_DIR } else { Join-Path $root 'washpro-b2b\uploads' }
if (Test-Path $uploads) {
    robocopy $uploads (Join-Path $OutDir 'uploads') /E /XC /XN /XO /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "Chép ảnh đính kèm lỗi (robocopy mã $LASTEXITCODE)" }
}

# ----- Xóa bản sao lưu quá hạn
Get-ChildItem $OutDir -Filter 'washpro-*.sql.gz' |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$KeepDays) } | Remove-Item

$size = '{0:N1} MB' -f ((Get-Item $gz).Length / 1MB)
Write-Output "Đã sao lưu $gz ($size); giữ $KeepDays ngày"
