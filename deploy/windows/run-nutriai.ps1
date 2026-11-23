# NutriAI - Windows background launcher (called by the scheduled task)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$configFile = Join-Path $here 'config.ps1'
if (-not (Test-Path $configFile)) {
    $configFile = Join-Path $here 'config.example.ps1'
}
. $configFile

$root = $NutriAI.ProjectRoot
$java = Join-Path $NutriAI.JavaHome 'bin\java.exe'
$logDir = Join-Path $root 'logs'
$tmpDir = Join-Path $root '.tmp'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null

function Test-NutriAI {
    try {
        $res = Invoke-RestMethod -Uri "http://127.0.0.1:$($NutriAI.Port)/api/health" -TimeoutSec 3
        return $res.code -eq 200
    } catch { return $false }
}

if (Test-NutriAI) {
    Write-Host 'NutriAI is already running, skip duplicate start.'
    exit 0
}

$env:SPRING_PROFILE = $NutriAI.Profile
$env:PORT = "$($NutriAI.Port)"
$env:MYSQL_HOST = $NutriAI.MysqlHost
$env:MYSQL_PORT = "$($NutriAI.MysqlPort)"
$env:MYSQL_DB = $NutriAI.MysqlDb
$env:MYSQL_USER = $NutriAI.MysqlUser
$env:MYSQL_PASSWORD = $NutriAI.MysqlPassword
$env:APP_BASE_URL = $NutriAI.BaseUrl
$env:LOG_LEVEL = $NutriAI.LogLevel
$env:LOG_DIR = $logDir
$env:AI_API_ENDPOINT = $NutriAI.AiEndpoint
$env:AI_MODEL = $NutriAI.AiModel
if ($NutriAI.AiApiKey) {
    $env:AI_API_KEY = $NutriAI.AiApiKey
} else {
    Remove-Item Env:AI_API_KEY -ErrorAction SilentlyContinue
}
$env:JAVA_HOME = $NutriAI.JavaHome

$stamp = Get-Date -Format 'yyyyMMdd'
$stdout = Join-Path $logDir "console-$stamp.out.log"
$stderr = Join-Path $logDir "console-$stamp.err.log"

$javaArgs = @(
    "-Djava.io.tmpdir=$tmpDir",
    '-Dfile.encoding=UTF-8',
    '-Duser.timezone=Asia/Shanghai',
    '-XX:MaxRAMPercentage=70',
    '-XX:+ExitOnOutOfMemoryError',
    '-jar', $NutriAI.JarPath,
    "--spring.profiles.active=$($NutriAI.Profile)",
    "--server.port=$($NutriAI.Port)"
)

Write-Host "Starting NutriAI: $java $($javaArgs -join ' ')"
$proc = Start-Process -FilePath $java -ArgumentList $javaArgs -WorkingDirectory (Join-Path $root 'backend') -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
$proc.Id | Set-Content -Path (Join-Path $root 'server.pid') -Encoding ascii

$ok = $false
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 2
    if (Test-NutriAI) { $ok = $true; break }
    if ($proc.HasExited) { break }
}
if (-not $ok) {
    Write-Error "NutriAI failed to start. Check $stderr"
    exit 1
}
Write-Host "NutriAI started. PID=$($proc.Id) URL=http://localhost:$($NutriAI.Port)"
