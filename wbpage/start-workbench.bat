@echo off
REM Workbench static server - one-click start (double-click)
REM Actual logic lives in start-workbench.ps1 (resolves node, idempotent, detached start)
title Workbench Static Server
powershell -ExecutionPolicy Bypass -NoProfile -File "%~dp0start-workbench.ps1"
echo.
echo Server runs in background; you can close this window safely.
echo URL: http://127.0.0.1:8088/%E4%B8%AA%E4%BA%BA%E5%8A%9E%E5%85%AC%E5%B7%A5%E4%BD%9C%E5%8F%B0.html
pause >nul
