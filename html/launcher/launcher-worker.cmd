@echo off
setlocal EnableExtensions
chcp 65001 >nul

for %%I in ("%~dp0..") do set "HTML_ROOT=%%~fI"
for %%I in ("%HTML_ROOT%\..") do set "PROJECT_ROOT=%%~fI"
set "SERVER_ROOT=%PROJECT_ROOT%\server"
for /f %%I in ('powershell.exe -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "RUN_STAMP=%%I"
set "LOG_FILE=%HTML_ROOT%\server\data\logs\startup-%RUN_STAMP%.log"
set "NODE_HOME=D:\ruanjian\node.js"
set "NPM_CONFIG_CACHE=%PROJECT_ROOT%\.npm-cache"
set "NPM_CONFIG_UPDATE_NOTIFIER=false"
set "NPM_CONFIG_AUDIT=false"
set "NPM_CONFIG_FUND=false"
set "PORT=3000"
set "HUIMENG_DEBUG=1"
if not exist "%HTML_ROOT%\server\data\logs" mkdir "%HTML_ROOT%\server\data\logs"
>"%LOG_FILE%" echo [%date% %time%] Starting shared website and Android API...

if exist "%NODE_HOME%\node.exe" (
    set "NODE_EXE=%NODE_HOME%\node.exe"
    set "NPM_CMD=%NODE_HOME%\npm.cmd"
    set "PATH=%NODE_HOME%;%PATH%"
) else (
    for /f "delims=" %%I in ('where node.exe 2^>nul') do if not defined NODE_EXE set "NODE_EXE=%%I"
    for /f "delims=" %%I in ('where npm.cmd 2^>nul') do if not defined NPM_CMD set "NPM_CMD=%%I"
)
if not defined NODE_EXE goto node_missing
if not exist "%PROJECT_ROOT%\node_modules\vite\bin\vite.js" goto install
if not exist "%PROJECT_ROOT%\node_modules\typescript\bin\tsc" goto install
if not exist "%SERVER_ROOT%\node_modules\better-sqlite3\build\Release\better_sqlite3.node" goto install_check
if not exist "%SERVER_ROOT%\node_modules\express" goto install_check
if not exist "%SERVER_ROOT%\dist\main.js" goto install_check
if not exist "%HTML_ROOT%\dist\index.html" goto install_check
if not exist "%SERVER_ROOT%\dist\scripts\migrate.js" goto install_check
goto prepare

:install_check
"%NODE_EXE%" --version >>"%LOG_FILE%" 2>&1
if errorlevel 1 goto node_missing

if not exist "%SERVER_ROOT%\node_modules\better-sqlite3\build\Release\better_sqlite3.node" goto install
if not exist "%SERVER_ROOT%\node_modules\express" goto install
goto prepare

:install
if not defined NPM_CMD goto npm_missing
echo [%date% %time%] Installing shared API dependencies with project-local cache...>>"%LOG_FILE%"
cd /d "%PROJECT_ROOT%"
call "%NPM_CMD%" ci --cache "%NPM_CONFIG_CACHE%" --no-audit --no-fund >>"%LOG_FILE%" 2>&1
if errorlevel 1 goto install_error

:prepare
if not exist "%SERVER_ROOT%\.env" copy "%SERVER_ROOT%\.env.example" "%SERVER_ROOT%\.env" >>"%LOG_FILE%" 2>&1
goto build_api

:migrate
echo [%date% %time%] Checking shared database and demo data...>>"%LOG_FILE%"
cd /d "%SERVER_ROOT%"
"%NODE_EXE%" "%HTML_ROOT%\launcher\prepare.mjs" >>"%LOG_FILE%" 2>&1
if errorlevel 1 goto database_error

goto build_site

:build_api
if not defined NPM_CMD goto npm_missing
echo [%date% %time%] Building API...>>"%LOG_FILE%"
cd /d "%PROJECT_ROOT%"
"%NODE_EXE%" "%HTML_ROOT%\launcher\prepare.mjs" --build server >>"%LOG_FILE%" 2>&1
if errorlevel 1 goto build_error
goto migrate

:build_site
if not defined NPM_CMD goto npm_missing
echo [%date% %time%] Building admin website...>>"%LOG_FILE%"
cd /d "%PROJECT_ROOT%"
"%NODE_EXE%" "%HTML_ROOT%\launcher\prepare.mjs" --build html >>"%LOG_FILE%" 2>&1
if errorlevel 1 goto build_error

:start_service
echo [%date% %time%] Shared service: http://127.0.0.1:3000 ; Android emulator: http://10.0.2.2:3000 >>"%LOG_FILE%"
cd /d "%SERVER_ROOT%"
rem Runtime output is captured in hourly logs; release the startup log so it can expire.
"%NODE_EXE%" "%HTML_ROOT%\launcher\service-runner.mjs"
set "SERVICE_EXIT=%errorlevel%"
if not "%SERVICE_EXIT%"=="0" echo [%date% %time%] ERROR: Service exited with code %SERVICE_EXIT%. See hourly runtime logs.>>"%LOG_FILE%"
exit /b %SERVICE_EXIT%

:node_missing
echo ERROR: Node.js was not found. Expected: D:\ruanjian\node.js\node.exe>>"%LOG_FILE%"
exit /b 10
:npm_missing
echo ERROR: npm.cmd was not found. Expected: D:\ruanjian\node.js\npm.cmd>>"%LOG_FILE%"
exit /b 11
:install_error
echo ERROR: npm ci failed. See details above; cache: %NPM_CONFIG_CACHE%>>"%LOG_FILE%"
exit /b 12
:database_error
echo ERROR: Database migration or seed failed. See details above.>>"%LOG_FILE%"
exit /b 13
:build_error
echo ERROR: Project build failed. See details above.>>"%LOG_FILE%"
exit /b 14
