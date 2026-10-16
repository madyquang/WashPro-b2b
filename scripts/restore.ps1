<#
.SYNOPSIS
  Khôi phục CSDL WashPro từ một bản sao lưu .sql.gz (do scripts\backup.ps1 hoặc dịch vụ backup của Docker tạo).

.DESCRIPTION
  GHI ĐÈ toàn bộ dữ liệu của CSDL đích, nên bắt buộc thêm -Yes. Dừng backend trước khi khôi phục, chạy lại sau đó.
  Mặc định khôi phục vào CSDL trong washpro-b2b\.env; -Database để khôi phục ra CSDL khác (VD để xem thử).
  Ảnh đính kèm: chép lại thư mục uploads trong thư mục sao lưu vào UPLOADS_DIR (mặc định washpro-b2b\uploads).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\restore.ps1 -File backups\washpro-20261016-020000.sql.gz -Yes
  powershell -ExecutionPolicy Bypass -File scripts\restore.ps1 -File backups\washpro-20261016-020000.sql.gz -Database washpro_xem_thu -Yes
#>
param(
    [Parameter(Mandatory = $true)][string]$File,
    [string]$Database = '',
    [switch]$Yes,
    [string]$MySqlBin = ''
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$envFile = Join-Path $root 'washpro-b2b\.env'
$File = (Resolve-Path $File).Path

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
if (-not $Database) { $Database = $Matches[3] }
if ($Database -notmatch '^[A-Za-z0-9_]+$') { throw "Tên CSDL không hợp lệ: $Database" }
$dbUser = if ($cfg.DB_USERNAME) { $cfg.DB_USERNAME } else { 'root' }
if (-not $cfg.DB_PASSWORD) { throw "Chưa có DB_PASSWORD trong $envFile" }

if (-not $Yes) { throw "Khôi phục sẽ GHI ĐÈ CSDL '$Database'. Chạy lại kèm -Yes nếu chắc chắn." }

if (-not $MySqlBin) {
    $found = Get-Command mysql -ErrorAction SilentlyContinue
    if ($found) { $MySqlBin = Split-Path $found.Source }
    else {
        $MySqlBin = Get-ChildItem 'C:\Program Files\MySQL' -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'bin' } |
            Where-Object { Test-Path (Join-Path $_ 'mysql.exe') } | Select-Object -First 1
    }
}
$mysql = Join-Path $MySqlBin 'mysql.exe'
if (-not (Test-Path $mysql)) { throw 'Không tìm thấy mysql.exe, hãy truyền -MySqlBin "<thư mục bin của MySQL>"' }

# Giải nén ra file tạm rồi cho mysql đọc bằng lệnh "source" (tránh PowerShell đổi bảng mã khi chuyển hướng <)
$sql = Join-Path ([IO.Path]::GetTempPath()) ("washpro-restore-{0}.sql" -f [Guid]::NewGuid())
$in = [IO.File]::OpenRead($File)
$unzip = New-Object IO.Compression.GZipStream($in, [IO.Compression.CompressionMode]::Decompress)
$out = [IO.File]::Create($sql)
try { $unzip.CopyTo($out) } finally { $out.Dispose(); $unzip.Dispose(); $in.Dispose() }

$env:MYSQL_PWD = $cfg.DB_PASSWORD
try {
    & $mysql -h $dbHost -P $dbPort -u $dbUser -e "DROP DATABASE IF EXISTS ``$Database``; CREATE DATABASE ``$Database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    if ($LASTEXITCODE -ne 0) { throw "Không tạo lại được CSDL (mã $LASTEXITCODE)" }
    & $mysql -h $dbHost -P $dbPort -u $dbUser --default-character-set=utf8mb4 $Database -e ("source " + $sql.Replace('\', '/'))
    if ($LASTEXITCODE -ne 0) { throw "Nạp bản sao lưu lỗi (mã $LASTEXITCODE)" }
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    Remove-Item $sql -ErrorAction SilentlyContinue
}

$backupUploads = Join-Path (Split-Path $File) 'uploads'
$uploads = if ($cfg.UPLOADS_DIR) { $cfg.UPLOADS_DIR } else { Join-Path $root 'washpro-b2b\uploads' }
if (Test-Path $backupUploads) {
    robocopy $backupUploads $uploads /E /XC /XN /XO /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "Chép lại ảnh đính kèm lỗi (robocopy mã $LASTEXITCODE)" }
}
Write-Output "Đã khôi phục $File vào CSDL $Database"
