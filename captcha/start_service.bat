@echo off
echo ========================================
echo Starting OCR Microservice
echo ========================================

cd /d "%~dp0"

REM 激活虚拟环境
call .venv\Scripts\activate.bat

REM 安装Flask依赖（首次运行）
pip install flask flask-cors -q

REM 启动服务
python ocr_service.py

pause
