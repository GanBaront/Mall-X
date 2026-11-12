@echo off
REM Generate a local self-signed TLS keystore for mall-gateway.
REM The .p12 file is git-ignored. NEVER commit a private key.
setlocal
cd /d %~dp0
set STORE=mall-gateway\src\main\resources\ssl.p12
set PASS=MallXGw2026Local
if exist "%STORE%" (
  echo [OK] Keystore already exists: %STORE%
  echo      To rotate it, delete the file and run this script again.
  exit /b 0
)
echo Generating self-signed certificate for localhost ...
keytool -genkeypair -alias mallgw -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore "%STORE%" -storepass %PASS% -keypass %PASS% -dname "CN=localhost, OU=MallX, O=MallX, L=Hangzhou, ST=Zhejiang, C=CN" -validity 3650 -ext "SAN=dns:localhost,ip:127.0.0.1"
if errorlevel 1 (
  echo [ERROR] keytool failed. Make sure JDK 21 bin is on PATH.
  exit /b 1
)
echo [OK] Created %STORE%
echo      Password matches MALL_SSL_PASSWORD in application.yml
endlocal
