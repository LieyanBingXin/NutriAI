@echo off
setlocal EnableExtensions
cd /d "%~dp0"

REM ============================================================
REM  NutriAI launcher (Java 21 + MySQL)
REM  Requires MySQL 8 running on localhost:3306
REM  Default login: root / bingxin  (override with MYSQL_PASSWORD)
REM ============================================================

set "JAVA_HOME=D:\java\21"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] Java 21 not found: %JAVA_HOME%
    echo         Please edit JAVA_HOME at the top of start.bat
    pause
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

if not defined PORT set "PORT=8080"
REM Default profile: mysql. Override with NUTRIAI_PROFILE or .env.bat
set "SPRING_PROFILE=mysql"
if defined NUTRIAI_PROFILE set "SPRING_PROFILE=%NUTRIAI_PROFILE%"
if exist ".env.bat" call ".env.bat"

set "JAR=backend\target\nutri-ai-0.1.0.jar"

echo ========================================
echo   NutriAI launcher
echo   Profile: %SPRING_PROFILE%   Port: %PORT%
echo ========================================

if exist "%JAR%" goto run

echo [1/2] Jar not found, building with Maven...
set "MVN="
where mvn >nul 2>nul && set "MVN=mvn"
if not defined MVN if exist "D:\apache-maven-3.9.9\bin\mvn.cmd" set "MVN=D:\apache-maven-3.9.9\bin\mvn.cmd"
if not defined MVN if defined MAVEN_HOME if exist "%MAVEN_HOME%\bin\mvn.cmd" set "MVN=%MAVEN_HOME%\bin\mvn.cmd"
if not defined MVN (
    echo [ERROR] Maven not found. Please run manually:
    echo         cd backend ^&^& mvn clean package -DskipTests
    pause
    exit /b 1
)
pushd backend
call "%MVN%" clean package -DskipTests -q
if errorlevel 1 (
    popd
    echo [ERROR] Maven build failed.
    pause
    exit /b 1
)
popd

:run
echo [2/2] Starting backend...
cd backend
start "NutriAI-Backend" "%JAVA_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -jar "target\nutri-ai-0.1.0.jar" --spring.profiles.active=%SPRING_PROFILE% --server.port=%PORT%
cd ..

timeout /t 8 /nobreak >nul
start "" "http://localhost:%PORT%"
echo.
echo Started: http://localhost:%PORT%
echo Demo account: demo2026 / NutriAI@123
echo To stop the server, run stop.bat
pause
