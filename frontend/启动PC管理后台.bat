@echo off
REM Mall-X PC admin dashboard dev server (port 5173)
REM Prerequisite: Node.js installed, node_modules ready (npm install if not)
cd /d %~dp0pc
echo.
echo Starting PC admin dashboard: http://localhost:5173
echo Close this window to stop the dev server.
echo.
call npm run dev
pause
