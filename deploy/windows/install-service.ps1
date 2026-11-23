#Requires -RunAsAdministrator
# NutriAI - install as a Windows startup task + firewall rule
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$configFile = Join-Path $here 'config.ps1'
if (-not (Test-Path $configFile)) {
    Copy-Item (Join-Path $here 'config.example.ps1') $configFile
    Write-Host "Created config.ps1 from example. Please edit it, then run this script again." -ForegroundColor Yellow
    exit 1
}
. $configFile

$root = $NutriAI.ProjectRoot
$runner = Join-Path $here 'run-nutriai.ps1'
if (-not (Test-Path $NutriAI.JarPath)) {
    Write-Host "Jar not found: $($NutriAI.JarPath). Run: cd backend; mvn clean package -DskipTests" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path (Join-Path $NutriAI.JavaHome 'bin\java.exe'))) {
    Write-Host "Java not found: $($NutriAI.JavaHome)" -ForegroundColor Red
    exit 1
}

$taskName = 'NutriAI'
$action = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$runner`""
$trigger = New-ScheduledTaskTrigger -AtStartup
try { $trigger.Delay = 'PT30S' } catch { }
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero)

Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal `
    -Settings $settings -Description 'NutriAI nutrition service' -Force | Out-Null

$ruleName = 'NutriAI Web'
if (-not (Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Action Allow -Protocol TCP `
        -LocalPort $NutriAI.Port -Profile Private, Domain | Out-Null
}

Start-ScheduledTask -TaskName $taskName
Write-Host "Installed scheduled task '$taskName' (runs at startup as SYSTEM)." -ForegroundColor Green
Write-Host "Waiting for http://localhost:$($NutriAI.Port) ..."
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 2
    try {
        $res = Invoke-RestMethod -Uri "http://127.0.0.1:$($NutriAI.Port)/api/health" -TimeoutSec 3
        if ($res.code -eq 200) {
            Write-Host "NutriAI is running: http://localhost:$($NutriAI.Port)" -ForegroundColor Green
            exit 0
        }
    } catch { }
}
Write-Host "Task registered but health check failed. See $root\logs." -ForegroundColor Yellow
