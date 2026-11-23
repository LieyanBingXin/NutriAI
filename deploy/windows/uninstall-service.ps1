#Requires -RunAsAdministrator
# NutriAI - remove startup task, stop process, remove firewall rule
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$configFile = Join-Path $here 'config.ps1'
if (Test-Path $configFile) { . $configFile } else { . (Join-Path $here 'config.example.ps1') }

$taskName = 'NutriAI'
if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) {
    Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
    Write-Host "Removed scheduled task '$taskName'."
}

$pidFile = Join-Path $NutriAI.ProjectRoot 'server.pid'
if (Test-Path $pidFile) {
    $procId = (Get-Content $pidFile -Raw).Trim()
    if ($procId -match '^\d+$') {
        Stop-Process -Id ([int]$procId) -Force -ErrorAction SilentlyContinue
        Write-Host "Stopped process PID=$procId."
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

$ruleName = 'NutriAI Web'
if (Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue) {
    Remove-NetFirewallRule -DisplayName $ruleName
    Write-Host "Removed firewall rule '$ruleName'."
}
Write-Host "NutriAI service removed." -ForegroundColor Green
