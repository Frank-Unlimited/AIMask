@echo off
echo ========================================
echo Starting Agent Microservice
echo ========================================

cd /d "%~dp0"

REM 检查虚拟环境
if not exist "venv" (
    echo Creating virtual environment...
    python -m venv venv
)

REM 激活虚拟环境
call venv\Scripts\activate.bat

REM 安装依赖
echo Installing dependencies...
pip install -r requirements.txt -q

REM 检查.env文件
if not exist ".env" (
    echo Warning: .env file not found!
    echo Please copy .env.example to .env and configure your API keys
    pause
    exit /b 1
)

REM 启动服务
echo Starting Agent Service...
python app.py

pause
