@echo off
chcp 65001 >nul 2>&1
title NutriAI - 一键启动

echo ========================================
echo   NutriAI 一键启动脚本
echo   最后更新: 2026-09-23
echo ========================================
echo.

:: ========== 环境变量 ==========
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.9"
set "MAVEN_HOME=D:\apache-maven-3.9.9"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

set "ROOT=%~dp0"
set "FRONTEND=%ROOT%frontend"
set "STATIC=%ROOT%backend\src\main\resources\static"
set "BACKEND=%ROOT%backend"

:: ========== 检查 Java ==========
echo [1/5] 检查 Java 环境...
where java >nul 2>&1
if errorlevel 1 (
    echo [错误] 找不到 Java，请检查 JAVA_HOME 设置
    pause
    exit /b 1
)
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VER=%%v
echo       Java 版本: %JAVA_VER%

:: ========== 同步前端文件 ==========
echo.
echo [2/5] 同步前端文件到后端 static...
if not exist "%FRONTEND%\index.html" (
    echo [警告] 找不到 frontend\index.html，跳过同步
) else (
    if not exist "%STATIC%" mkdir "%STATIC%"
    copy /Y "%FRONTEND%\index.html" "%STATIC%\index.html" >nul
    if not exist "%STATIC%\css" mkdir "%STATIC%\css"
    copy /Y "%FRONTEND%\css\*.*" "%STATIC%\css\" >nul 2>&1
    if not exist "%STATIC%\js" mkdir "%STATIC%\js"
    copy /Y "%FRONTEND%\js\*.*" "%STATIC%\js\" >nul 2>&1
    echo       前端 → static 同步完成
)

:: ========== Maven 打包 ==========
echo.
echo [3/5] 检查项目构建...
cd /d "%BACKEND%"

if not exist "target\nutri-ai-0.1.0.jar" (
    echo       未找到 jar 包，开始 Maven 打包（首次需要下载依赖）...
    call mvn clean package -DskipTests -q
    if errorlevel 1 (
        echo [错误] 打包失败！
        pause
        exit /b 1
    )
    echo       打包完成！
) else (
    echo       jar 包已存在，跳过打包（如需重新打包请删除 target 目录）
)

:: ========== 释放端口 ==========
echo.
echo [4/5] 检查端口 8080...
netstat -ano | findstr ":8080 " | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 (
    echo [警告] 端口被占用，正在释放...
    for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080 " ^| findstr "LISTENING"') do (
        taskkill /F /PID %%p >nul 2>&1
    )
    timeout /t 2 /nobreak >nul
) else (
    echo       端口 8080 空闲
)

:: ========== 启动 ==========
echo.
echo [5/5] 启动 NutriAI...
echo ========================================
echo   启动后浏览器访问: http://localhost:8080
echo   API 健康检查: http://localhost:8080/api/health
echo   关闭本窗口 = 停止服务
echo ========================================
echo.

start "" http://localhost:8080
java -jar target\nutri-ai-0.1.0.jar --server.port=8080

pause