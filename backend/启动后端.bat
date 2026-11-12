@echo off
REM Mall-X backend launcher: mgr(10011) + alipay(10012) + gateway(10010/https)
REM Prerequisite: MySQL running (root/123456), JDK 21 on PATH
cd /d %~dp0
REM Load local secrets from the project-root .env (git-ignored, never committed).
REM If .env is absent, the application.yml demo placeholders are used instead.
if exist "..\.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("..\.env") do set "%%A=%%B"
)
start "mall-mgr" cmd /c "java -jar mall-mgr\target\mall-mgr-1.0.0.jar"
start "mall-alipay" cmd /c "java -jar mall-alipay\target\mall-alipay-1.0.0.jar"
timeout /t 3 /nobreak >nul
start "mall-gateway" cmd /c "java -jar mall-gateway\target\mall-gateway-1.0.0.jar"
echo.
echo Backend starting:
echo   mall-mgr    : http://localhost:10011
echo   mall-alipay : http://localhost:10012
echo   mall-gateway: https://localhost:10010  (frontend /gateway proxy target)
echo.
echo Close each window to stop the corresponding service.
pause
