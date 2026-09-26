@echo off
title HongHu - Stop

echo Stopping processes on port 8080 (backend) and 5173 (frontend)...
echo.

for %%P in (8080 5173) do (
    for /f "tokens=5" %%I in ('netstat -ano ^| findstr ":%%P" ^| findstr LISTENING') do (
        echo Killing process on port %%P PID=%%I
        taskkill /F /PID %%I >nul 2>&1
    )
)

echo.
echo Done. All related services stopped.
pause
