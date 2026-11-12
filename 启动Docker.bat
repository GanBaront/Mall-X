@echo off
chcp 65001 >nul
echo ============================================
echo  Mall-X Docker deployment starting...
echo ============================================
docker compose up -d --build
if errorlevel 1 (
  echo.
  echo [ERROR] Docker compose failed. Is Docker Desktop running?
  pause
  exit /b 1
)
echo.
echo All services started:
echo   PC admin  : http://localhost:5173   (admin / admin123)
echo   Mobile H5 : http://localhost:5175   (user / 123456, merchant / 123456)
echo   Gateway   : https://localhost:10010
echo   Mock pay  : http://localhost:10012/mock/cashier?payOrderId=xxx
echo   MySQL     : localhost:3307  root / 123456  (db: mallDb)
pause
