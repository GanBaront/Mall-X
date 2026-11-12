@echo off
REM Mall-X mobile H5 dev server (port 5175)
REM Prerequisite: Node.js installed, node_modules ready (npm install if not)
cd /d "%~dp0uniapp\mobile"
echo.
echo Starting mobile H5 preview: http://localhost:5175
echo Close this window to stop the dev server.
echo.
call npm run dev:h5
pause
