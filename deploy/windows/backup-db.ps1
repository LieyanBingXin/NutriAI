# NutriAI - backup MySQL database on Windows
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$configFile = Join-Path $here 'config.ps1'
if (-not (Test-Path $configFile)) {
    Write-Host "config.ps1 not found. Copy config.example.ps1 and edit it." -ForegroundColor Red
    exit 1
}
. $configFile

$dump = $NutriAI.MysqlDump
if (-not (Test-Path $dump)) {
    $candidate = Get-ChildItem 'C:\Program Files\MySQL' -Recurse -Filter mysqldump.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) { $dump = $candidate.FullName }
}
if (-not (Test-Path $dump)) {
    Write-Host "mysqldump.exe not found. Set MysqlDump in config.ps1." -ForegroundColor Red
    exit 1
}

$backupDir = Join-Path $NutriAI.ProjectRoot 'backups'
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$sql = Join-Path $backupDir "nutriai-$stamp.sql"
$zip = "$sql.zip"

$env:MYSQL_PWD = $NutriAI.MysqlPassword
try {
    & $dump -h $NutriAI.MysqlHost -P $NutriAI.MysqlPort -u $NutriAI.MysqlUser `
        --single-transaction --quick --routines --events --default-character-set=utf8mb4 `
        --result-file="$sql" $NutriAI.MysqlDb
    if ($LASTEXITCODE -ne 0) { throw "mysqldump failed with exit code $LASTEXITCODE" }
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

if ((Get-Item $sql).Length -le 0) { throw 'Backup file is empty' }
Compress-Archive -Path $sql -DestinationPath $zip -Force
Remove-Item $sql -Force

$retention = [int]$NutriAI.BackupRetentionDays
if ($retention -gt 0) {
    Get-ChildItem $backupDir -Filter 'nutriai-*.sql.zip' |
        Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$retention) } |
        Remove-Item -Force
}
Write-Host "Backup created: $zip" -ForegroundColor Green
