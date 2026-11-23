@echo off
setlocal EnableExtensions
set "PORT=8080"
if not "%~1"=="" set "PORT=%~1"

echo Stopping NutriAI on port %PORT% ...
set "FOUND="
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%PORT%" ^| findstr "LISTENING"') do (
    set "FOUND=1"
    taskkill /PID %%p /F >nul 2>&1
    echo Stopped process PID=%%p
)
if not defined FOUND echo No process is listening on port %PORT%.
echo Done.
pause
