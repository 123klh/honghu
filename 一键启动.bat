@echo off
title HongHu - Start All
setlocal

set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"
set "FRONTEND=%ROOT%frontend"
set "JAR=%BACKEND%\target\honghu-backend-1.0.1.jar"

echo ============================================
echo   HongHu (bobo gongkao) - one click start
echo ============================================
echo.

REM ---- 1. check backend jar, build if missing ----
if not exist "%JAR%" (
    echo [backend] jar not found, building with mvnw...
    pushd "%BACKEND%"
    call mvnw.cmd clean package -DskipTests
    popd
    if not exist "%JAR%" (
        echo [ERROR] backend build failed, check the log above.
        pause
        exit /b 1
    )
)

REM ---- 2. check frontend deps, install if missing ----
if not exist "%FRONTEND%\node_modules" (
    echo [frontend] node_modules not found, running npm install...
    pushd "%FRONTEND%"
    call npm install
    popd
)

REM ---- 3. start backend in a new window ----
echo [backend] starting on http://localhost:8080 ...
start "HongHu-Backend" cmd /k "cd /d %BACKEND% && java -jar %JAR%"

REM ---- 4. start frontend in a new window ----
echo [frontend] starting on http://localhost:5173 ...
start "HongHu-Frontend" cmd /k "cd /d %FRONTEND% && npm run dev"

echo.
echo ============================================
echo   Services launched in two new windows
echo   Frontend : http://localhost:5173
echo   Backend  : http://localhost:8080
echo   API doc  : http://localhost:8080/swagger-ui.html
echo ============================================
echo.
echo First launch may take 10-15 seconds to be ready.
echo Close a window to stop that service, or run stop.bat
pause
