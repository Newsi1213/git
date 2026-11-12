@echo off
chcp 936 >nul
setlocal EnableExtensions
title CampusQuest 停止

if not exist "%~dp0server\cq-env.bat" (
  echo [错误] 缺少配置文件：%~dp0server\cq-env.bat
  pause
  exit /b 1
)
call "%~dp0server\cq-env.bat"

echo ==========================================
echo    CampusQuest 校园互助悬赏平台 - 停止
echo ==========================================
echo.

netstat -ano | findstr /C:"LISTENING" | findstr /C:":%CQ_APP_PORT% " >nul
if errorlevel 1 (
  echo [状态] 当前没有在运行（端口 %CQ_APP_PORT% 未监听）。
  ping -n 5 127.0.0.1 >nul
  exit /b 0
)

echo 正在停止 Tomcat，请稍候...
powershell -NoProfile -ExecutionPolicy Bypass -File "%CQ_SERVER_DIR%\stop.ps1"
if errorlevel 1 (
  echo.
  echo [错误] 停止失败。请查看日志：%CQ_TOMCAT_HOME%\logs\
  echo        或以管理员身份结束残留的 java 进程后重试。
  pause
  exit /b 1
)

netstat -ano | findstr /C:"LISTENING" | findstr /C:":%CQ_APP_PORT% " >nul
if not errorlevel 1 (
  echo [警告] 端口 %CQ_APP_PORT% 仍在监听，停止未完成，请稍后重试。
  pause
  exit /b 1
)

echo.
echo 已停止，端口 %CQ_APP_PORT% 已释放。
ping -n 5 127.0.0.1 >nul
exit /b 0
