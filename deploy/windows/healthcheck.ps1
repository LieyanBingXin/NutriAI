# NutriAI - Windows health check
# Optional alert webhook: set $env:NUTRIAI_ALERT_WEBHOOK or pass -Webhook.
param(
    [string]$Webhook = $env:NUTRIAI_ALERT_WEBHOOK,
    [switch]$RestartTask
)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$configFile = Join-Path $here 'config.ps1'
if (Test-Path $configFile) { . $configFile } else { . (Join-Path $here 'config.example.ps1') }

$logDir = Join-Path $NutriAI.ProjectRoot 'logs'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$logFile = Join-Path $logDir 'healthcheck.log'
$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
$url = "http://127.0.0.1:$($NutriAI.Port)/api/health"

function Write-HealthLog([string]$text) {
    Add-Content -Path $logFile -Value "[$stamp] $text" -Encoding UTF8
}

try {
    $res = Invoke-RestMethod -Uri $url -TimeoutSec 8
    if ($res.code -eq 200) {
        Write-HealthLog "OK $url"
        exit 0
    }
    throw "status=$($res.code)"
} catch {
    Write-HealthLog "FAIL $url $($_.Exception.Message)"
}

if ($RestartTask) {
    try {
        Start-ScheduledTask -TaskName 'NutriAI'
        Start-Sleep -Seconds 20
        $res = Invoke-RestMethod -Uri $url -TimeoutSec 8
        if ($res.code -eq 200) {
            Write-HealthLog 'RECOVERED after task restart'
            exit 0
        }
    } catch {
        Write-HealthLog "restart failed: $($_.Exception.Message)"
    }
}

if ($Webhook) {
    $payload = @{ text = "NutriAI health check failed at $stamp"; content = "NutriAI health check failed at $stamp" } | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri $Webhook -Method Post -ContentType 'application/json' -Body $payload -TimeoutSec 10 | Out-Null
        Write-HealthLog 'alert webhook sent'
    } catch {
        Write-HealthLog "webhook failed: $($_.Exception.Message)"
    }
}

exit 1
