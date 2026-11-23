@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"

REM ============================================================
REM  NutriAI 对外服务启动脚本（后端 + Cloudflare Tunnel）
REM  手动运行，不做开机自启。本地开发/测试请用 start.bat
REM  停止服务请运行 serve-stop.bat
REM ============================================================

set "JAVA_HOME=D:\java\21"
set "JAR=backend\target\nutri-ai-0.1.0.jar"
set "SPRING_PROFILE=mysql"
set "PORT=8080"
set "CLOUDFLARED=D:\cloudflared\cloudflared.exe"
REM 代理端口（socks5）。开着代理则 tunnel 自动走它，没开则直连
set "PROXY_PORT=65532"

echo ========================================
echo   NutriAI Online - 后端 + Tunnel
echo ========================================
echo.

REM ---- 检查 Java ----
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [错误] 找不到 Java 21: %JAVA_HOME%
    echo        请修改脚本顶部的 JAVA_HOME
    pause
    exit /b 1
)

REM ---- 检查 jar，不存在则提示先用 start.bat 构建 ----
if not exist "%JAR%" (
    echo [错误] 找不到 jar: %JAR%
    echo        请先运行 start.bat 完成构建，再运行本脚本
    pause
    exit /b 1
)

REM ---- 检查 MySQL ----
netstat -ano | findstr "LISTENING" | findstr ":3306" >nul 2>&1
if errorlevel 1 (
    echo [警告] 未检测到 MySQL 3306 端口，后端可能启动失败
)

REM ---- 启动后端 ----
echo [1/3] 启动后端 (port %PORT%) ...
start "NutriAI-Backend" "%JAVA_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -jar "%JAR%" --spring.profiles.active=%SPRING_PROFILE% --server.port=%PORT%

REM ---- 等待后端起来 ----
echo       等待后端启动 ...
timeout /t 8 /nobreak >nul

REM ---- 检测代理，设置 tunnel 走代理 ----
set "HTTPS_PROXY="
set "HTTP_PROXY="
netstat -ano | findstr "LISTENING" | findstr ":%PROXY_PORT%" >nul 2>&1
if not errorlevel 1 (
    set "HTTPS_PROXY=socks5://127.0.0.1:%PROXY_PORT%"
    set "HTTP_PROXY=socks5://127.0.0.1:%PROXY_PORT%"
    echo [i] 检测到代理 %PROXY_PORT%，tunnel 走代理
) else (
    echo [i] 未检测到代理，tunnel 直连
)

REM ---- 启动 tunnel ----
echo [2/3] 启动 Cloudflare tunnel ...
start "NutriAI-Tunnel" "%CLOUDFLARED%" tunnel run nutriai

echo [3/3] 完成。
echo.
echo   本地:  http://localhost:%PORT%
echo   公网:  https://nutri-ai.top
echo.
echo   停止服务请运行 serve-stop.bat
pause