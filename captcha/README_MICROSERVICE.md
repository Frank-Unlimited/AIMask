# OCR微服务部署指南

## 架构变更

### 旧架构（本地进程调用）
```
Android App → ProcessBuilder → Python脚本 → 文件系统 → Android App
```

### 新架构（微服务）
```
Android App → HTTP Request → OCR Service (Flask) → HTTP Response → Android App
```

## 优势

✅ **前后端分离**：Android和Python独立部署
✅ **模型常驻内存**：避免重复加载，响应更快
✅ **易于扩展**：可部署多个实例负载均衡
✅ **跨平台**：任何设备都可调用
✅ **易于维护**：独立更新不影响客户端

## 快速启动

### 1. 启动OCR服务

**Windows:**
```bash
cd F:\AutoMark\AI-dama\captcha
start_service.bat
```

**Linux/Mac:**
```bash
cd /path/to/AI-dama/captcha
chmod +x start_service.sh
./start_service.sh
```

**手动启动:**
```bash
cd captcha
.venv\Scripts\activate  # Windows
source .venv/bin/activate  # Linux/Mac

pip install flask flask-cors
python ocr_service.py
```

服务将在 `http://0.0.0.0:5000` 启动

### 2. 测试服务

```bash
python test_service.py
```

或使用curl:
```bash
# 健康检查
curl http://localhost:5000/health

# OCR识别
curl -X POST -F "image=@test.jpg" http://localhost:5000/ocr
```

### 3. 配置Android客户端

修改 `MosaicUtils.kt` 中的服务地址：
```kotlin
val ocrServiceUrl = "http://192.168.1.100:5000/ocr"  // 改为实际IP
```

**注意事项：**
- 如果服务在本机：使用 `http://localhost:5000/ocr`
- 如果服务在局域网：使用 `http://192.168.x.x:5000/ocr`
- 如果服务在云端：使用 `http://your-domain.com:5000/ocr`
- Android模拟器访问本机：使用 `http://10.0.2.2:5000/ocr`

## API文档

### 1. 健康检查

**请求:**
```
GET /health
```

**响应:**
```json
{
  "status": "healthy",
  "device": "cuda",
  "model_loaded": true
}
```

### 2. OCR识别（文件上传）

**请求:**
```
POST /ocr
Content-Type: multipart/form-data

image: <binary file>
```

**响应:**
```json
{
  "request_id": "req_1769068424",
  "status": "success",
  "processing_time_ms": 1234,
  "confidence_score": 0.9861,
  "image_metadata": {
    "dimensions": {"width": 1080, "height": 2941},
    "orientation": "portrait"
  },
  "text_regions": [...],
  "full_text": "识别的完整文本"
}
```

### 3. OCR识别（Base64）

**请求:**
```
POST /ocr/base64
Content-Type: application/json

{
  "image": "data:image/jpeg;base64,/9j/4AAQSkZJRg..."
}
```

**响应:** 同上

## 部署选项

### 选项1：本地开发（当前配置）
- 服务运行在开发机
- Android通过局域网访问
- 适合开发测试

### 选项2：云服务器部署

**使用Docker:**
```dockerfile
FROM python:3.11-slim

WORKDIR /app
COPY captcha/ /app/
RUN pip install -r requirements_service.txt

EXPOSE 5000
CMD ["python", "ocr_service.py"]
```

**部署命令:**
```bash
docker build -t ocr-service .
docker run -d -p 5000:5000 ocr-service
```

### 选项3：使用Gunicorn（生产环境）

```bash
pip install gunicorn

# 启动4个worker进程
gunicorn -w 4 -b 0.0.0.0:5000 ocr_service:app
```

### 选项4：使用Nginx反向代理

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location /ocr {
        proxy_pass http://localhost:5000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        client_max_body_size 10M;
    }
}
```

## 性能优化

### 1. 模型预加载
服务启动时加载模型，避免首次请求慢

### 2. 多进程部署
```bash
gunicorn -w 4 ocr_service:app  # 4个worker
```

### 3. GPU加速
确保PyTorch能访问GPU：
```python
device = 'cuda' if torch.cuda.is_available() else 'cpu'
```

### 4. 请求队列
使用Celery处理异步任务：
```python
from celery import Celery

celery = Celery('ocr_tasks', broker='redis://localhost:6379')

@celery.task
def process_ocr(image_data):
    return process_image(image_data)
```

## 安全建议

### 1. 添加认证
```python
from flask import request

API_KEY = "your-secret-key"

@app.before_request
def check_auth():
    if request.headers.get('X-API-Key') != API_KEY:
        return jsonify({"error": "Unauthorized"}), 401
```

### 2. 限流
```python
from flask_limiter import Limiter

limiter = Limiter(app, key_func=lambda: request.remote_addr)

@app.route('/ocr', methods=['POST'])
@limiter.limit("10 per minute")
def ocr_endpoint():
    ...
```

### 3. HTTPS
使用Let's Encrypt免费证书：
```bash
certbot --nginx -d your-domain.com
```

## 监控与日志

### 1. 添加日志
```python
import logging

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('ocr_service.log'),
        logging.StreamHandler()
    ]
)
```

### 2. 性能监控
```python
import time

@app.before_request
def start_timer():
    request.start_time = time.time()

@app.after_request
def log_request(response):
    duration = time.time() - request.start_time
    logging.info(f"{request.method} {request.path} - {response.status_code} - {duration:.3f}s")
    return response
```

## 故障排查

### 问题1：服务无法启动
- 检查端口5000是否被占用：`netstat -ano | findstr :5000`
- 检查Python环境是否正确
- 查看错误日志

### 问题2：Android无法连接
- 检查防火墙设置
- 确认IP地址正确
- 使用curl测试服务是否可访问
- Android模拟器使用 `10.0.2.2` 而非 `localhost`

### 问题3：OCR识别慢
- 检查是否使用GPU：查看日志中的 `device`
- 考虑使用更小的模型
- 增加worker进程数

### 问题4：内存占用高
- 限制并发请求数
- 使用模型量化
- 定期重启服务

## 下一步

- [ ] 添加API认证
- [ ] 部署到云服务器
- [ ] 配置HTTPS
- [ ] 添加请求限流
- [ ] 实现批量处理接口
- [ ] 添加监控告警
