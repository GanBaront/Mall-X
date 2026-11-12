@echo off
docker compose down
echo Mall-X Docker stopped. (Data volume kept; use "docker compose down -v" to reset database.)
pause
