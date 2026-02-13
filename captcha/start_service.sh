#!/bin/bash
echo "========================================"
echo "Starting OCR Microservice"
echo "========================================"

cd "$(dirname "$0")"

# 激活虚拟环境
source .venv/bin/activate

# 安装Flask依赖（首次运行）
pip install flask flask-cors -q

# 启动服务
python ocr_service.py
