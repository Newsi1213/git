@echo off
chcp 936 >nul
setlocal EnableExtensions
title CampusQuest 启动

if not exist "%~dp0server\cq-env.bat" (
  echo [错误] 缺少配置文件：%~dp0server\cq-env.bat
  pause
  exit /b 1
)
call "%~dp0server\cq-env.bat"

echo ==========================================
echo    CampusQuest 校园互助悬赏平台 - 启动
echo ==========================================
echo.

if not exist "%CQ_TOMCAT_HOME%\bin\catalina.bat" (
  echo [错误] 找不到 Tomcat：%CQ_TOMCAT_HOME%\bin\catalina.bat
  echo        请确认 tools\tomcat9 目录完整且没有被移动。
  pause
  exit /b 1
)
if not exist "%CQ_JAVA_HOME%\bin\java.exe" (
  echo [错误] 找不到 JDK：%CQ_JAVA_HOME%\bin\java.exe
  echo        请修改 server\cq-env.bat 里的 CQ_JAVA_HOME。
  pause
  exit /b 1
)
if not exist "%CQ_SERVER_DIR%\start.ps1" (
  echo [错误] 找不到启动脚本：%CQ_SERVER_DIR%\start.ps1
  pause
  exit /b 1
)

set "CODE=000"
for /f "usebackq delims=" %%i in (`powershell -NoProfile -Command "try{(Invoke-WebRequest -UseBasicParsing -Uri '%CQ_APP_URL%' -TimeoutSec 5).StatusCode}catch{0}"`) do set "CODE=%%i"

if "%CODE%"=="200" (
  echo [状态] 服务已经在运行，不重复启动。
  goto :open_browser
)

netstat -ano | findstr /C:"LISTENING" | findstr /C:":%CQ_APP_PORT% " >nul
if not errorlevel 1 (
  echo [错误] %CQ_APP_PORT% 端口已被其他程序占用，无法启动 Tomcat。
  echo        可在命令提示符执行 netstat -ano ^| findstr :%CQ_APP_PORT% 查看占用进程。
  pause
  exit /b 1
)

echo [1/2] 正在启动 Tomcat，请稍候（最长约 60 秒）...
powershell -NoProfile -ExecutionPolicy Bypass -File "%CQ_SERVER_DIR%\start.ps1"
if errorlevel 1 (
  echo.
  echo [错误] Tomcat 启动失败。常见原因：
  echo        1^) 端口被其他程序占用；
  echo        2^) JDK 路径不正确；
  echo        3^) MySQL 未启动，数据库连接失败。
  echo        日志位置：%CQ_TOMCAT_HOME%\logs\
  pause
  exit /b 1
)

set "CODE=000"
for /f "usebackq delims=" %%i in (`powershell -NoProfile -Command "try{(Invoke-WebRequest -UseBasicParsing -Uri '%CQ_APP_URL%' -TimeoutSec 5).StatusCode}catch{0}"`) do set "CODE=%%i"

if not "%CODE%"=="200" (
  echo [警告] Tomcat 已启动，但页面暂时返回 %CODE%，请稍后刷新。
) else (
  echo [2/2] 服务已就绪：%CQ_APP_URL%
)

:open_browser
echo 正在用默认浏览器打开登录页...
start "" "%CQ_APP_URL%"
echo.
echo 启动完成。关闭本窗口不会停止服务；停止请双击「停止CampusQuest.bat」。
ping -n 5 127.0.0.1 >nul
exit /b 0
