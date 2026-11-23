@echo off
chcp 65001 >nul
setlocal EnableExtensions
set "PORT=8080"

echo ========================================
echo   Stopping NutriAI Online
echo ========================================
echo.

REM ---- 停止 tunnel ----
echo [1/2] 停止 Cloudflare tunnel ...
tasklist | findstr /I "cloudflared" >nul 2>&1
if errorlevel 1 (
    echo      没有运行中的 tunnel
) else (
    taskkill /IM cloudflared.exe /F >nul 2>&1
    echo      已停止 tunnel
)

REM ---- 停止后端 ----
echo [2/2] 停止后端 (port %PORT%) ...
set "FOUND="
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%PORT%" ^| findstr "LISTENING"') do (
    set "FOUND=1"
    taskkill /PID %%p /F >nul 2>&1
    echo      已停止进程 PID=%%p
)
if not defined FOUND echo      端口 %PORT% 上没有进程。

echo.
echo Done.
pause