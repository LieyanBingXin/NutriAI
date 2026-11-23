# NutriAI launcher (PowerShell + Java 21)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$env:JAVA_HOME = 'D:\java\21'
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path $java)) {
    Write-Host "[ERROR] Java 21 not found: $env:JAVA_HOME" -ForegroundColor Red
    exit 1
}

if (-not $env:PORT) { $env:PORT = '8080' }
# Default profile: mysql. Override with NUTRIAI_PROFILE.
$env:SPRING_PROFILE = 'mysql'
if ($env:NUTRIAI_PROFILE) { $env:SPRING_PROFILE = $env:NUTRIAI_PROFILE }

$jar = Join-Path $root 'backend\target\nutri-ai-0.1.0.jar'
if (-not (Test-Path $jar)) {
    Write-Host '[1/2] Jar not found, building with Maven...' -ForegroundColor Yellow
    $mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
    if (-not $mvn -and (Test-Path 'D:\apache-maven-3.9.9\bin\mvn.cmd')) {
        $mvn = 'D:\apache-maven-3.9.9\bin\mvn.cmd'
    }
    if (-not $mvn) {
        Write-Host '[ERROR] Maven not found. Run: cd backend; mvn clean package -DskipTests' -ForegroundColor Red
        exit 1
    }
    Push-Location (Join-Path $root 'backend')
    try {
        & $mvn clean package -DskipTests -q
        if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }
    } finally {
        Pop-Location
    }
}

Write-Host "[2/2] Starting NutriAI (profile=$env:SPRING_PROFILE, port=$env:PORT)..." -ForegroundColor Green
$javaArgs = @(
    '-Dfile.encoding=UTF-8',
    '-Duser.timezone=Asia/Shanghai',
    '-jar', 'target\nutri-ai-0.1.0.jar',
    "--spring.profiles.active=$env:SPRING_PROFILE",
    "--server.port=$env:PORT"
)
# Run from backend so relative paths resolve consistently
Push-Location (Join-Path $root 'backend')
try {
    & $java @javaArgs
} finally {
    Pop-Location
}
