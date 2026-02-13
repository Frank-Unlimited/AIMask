"""
OCR微服务 - Flask实现
提供HTTP API接口供Android客户端调用
"""
import os
import sys
from pathlib import Path

# Add mineru path
mineru_path = os.getenv('MINERU_PATH', r'f:\AutoMark\AI-dama\captcha\mineru-master')
if not os.path.exists(mineru_path):
    script_dir = os.path.dirname(os.path.abspath(__file__))
    mineru_path = os.path.join(script_dir, 'mineru-master')
mineru_path = os.path.abspath(mineru_path)
if mineru_path not in sys.path:
    sys.path.insert(0, mineru_path)

import json
import time
import base64
import numpy as np
import cv2
import torch
from flask import Flask, request, jsonify
from flask_cors import CORS
from werkzeug.utils import secure_filename

from mineru.backend.pipeline.model_init import ocr_model_init
from mineru.utils.post_char_bbox_converter import PostCharBboxConverter

app = Flask(__name__)
CORS(app)  # 允许跨域

# 全局变量：OCR模型（启动时加载一次，避免重复加载）
ocr_model = None
converter = PostCharBboxConverter()
device = 'cuda' if torch.cuda.is_available() else 'cpu'

def init_model():
    """初始化OCR模型"""
    global ocr_model
    if ocr_model is None:
        print(f"Initializing OCR model on {device}...")
        ocr_model = ocr_model_init(lang='ch', det_db_box_thresh=0.3)
        print("OCR model loaded successfully")

def process_image(image_data):
    """处理图片并返回OCR结果"""
    # 解码图片
    nparr = np.frombuffer(image_data, np.uint8)
    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    
    if img is None:
        raise ValueError("Failed to decode image")
    
    h, w = img.shape[:2]
    
    # 执行OCR
    start_time = time.time()
    ocr_result = ocr_model(img)
    
    if isinstance(ocr_result, tuple) and len(ocr_result) == 2:
        dt_boxes, rec_res = ocr_result
    else:
        dt_boxes, rec_res = [], []
    
    processing_time = (time.time() - start_time) * 1000
    
    # 处理结果
    text_regions = []
    full_text = ""
    line_number = 1
    total_confidence = 0
    char_count = 0
    
    if not isinstance(dt_boxes, list):
        dt_boxes = dt_boxes.tolist() if hasattr(dt_boxes, 'tolist') else []
    if not isinstance(rec_res, list):
        rec_res = rec_res.tolist() if hasattr(rec_res, 'tolist') else []
    
    for box, rec in zip(dt_boxes, rec_res):
        text = rec[0]
        score = rec[1]
        
        if isinstance(box, np.ndarray):
            box = box.tolist()
        
        x_coords = [p[0] for p in box]
        y_coords = [p[1] for p in box]
        x0, x1 = min(x_coords), max(x_coords)
        y0, y1 = min(y_coords), max(y_coords)
        line_bbox = [x0, y0, x1, y1]
        
        try:
            char_bboxes = converter.calculate_char_bboxes(text, line_bbox)
        except Exception as e:
            print(f"Error converting char box: {e}")
            char_bboxes = []
        
        for j, char in enumerate(text):
            if j < len(char_bboxes):
                c_bbox = char_bboxes[j]
                vertices = [
                    {"x": float(c_bbox[0]), "y": float(c_bbox[1])},
                    {"x": float(c_bbox[2]), "y": float(c_bbox[1])},
                    {"x": float(c_bbox[2]), "y": float(c_bbox[3])},
                    {"x": float(c_bbox[0]), "y": float(c_bbox[3])}
                ]
                
                region = {
                    "region_id": f"region_{len(text_regions)+1:03d}",
                    "text": char,
                    "bounding_box": {
                        "vertices": vertices,
                        "confidence": float(score)
                    },
                    "attributes": {
                        "line_number": line_number,
                        "font_size": int(c_bbox[3] - c_bbox[1]),
                        "text_color": "#000000",
                        "background_color": "#FFFFFF"
                    }
                }
                text_regions.append(region)
                total_confidence += score
                char_count += 1
        
        full_text += text + "\n"
        line_number += 1
    
    avg_confidence = total_confidence / char_count if char_count > 0 else 0.0
    
    return {
        "request_id": f"req_{int(time.time())}",
        "status": "success",
        "processing_time_ms": int(processing_time),
        "confidence_score": round(avg_confidence, 4),
        "image_metadata": {
            "dimensions": {"width": w, "height": h},
            "orientation": "portrait" if h >= w else "landscape"
        },
        "text_regions": text_regions,
        "full_text": full_text.strip()
    }

@app.route('/health', methods=['GET'])
def health_check():
    """健康检查接口"""
    return jsonify({
        "status": "healthy",
        "device": device,
        "model_loaded": ocr_model is not None
    })

@app.route('/ocr', methods=['POST'])
def ocr_endpoint():
    """OCR识别接口"""
    try:
        # 检查是否有文件上传
        if 'image' not in request.files:
            return jsonify({
                "status": "error",
                "message": "No image file provided"
            }), 400
        
        file = request.files['image']
        if file.filename == '':
            return jsonify({
                "status": "error",
                "message": "Empty filename"
            }), 400
        
        # 读取图片数据
        image_data = file.read()
        
        # 处理图片
        result = process_image(image_data)
        return jsonify(result)
        
    except Exception as e:
        print(f"Error processing request: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({
            "status": "error",
            "message": str(e)
        }), 500

@app.route('/ocr/base64', methods=['POST'])
def ocr_base64_endpoint():
    """OCR识别接口 - Base64格式"""
    try:
        data = request.get_json()
        if not data or 'image' not in data:
            return jsonify({
                "status": "error",
                "message": "No image data provided"
            }), 400
        
        # 解码Base64
        image_base64 = data['image']
        if ',' in image_base64:
            image_base64 = image_base64.split(',')[1]
        
        image_data = base64.b64decode(image_base64)
        
        # 处理图片
        result = process_image(image_data)
        return jsonify(result)
        
    except Exception as e:
        print(f"Error processing request: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({
            "status": "error",
            "message": str(e)
        }), 500

if __name__ == '__main__':
    print("=" * 60)
    print("OCR Microservice Starting...")
    print("=" * 60)
    
    # 初始化模型
    init_model()
    
    print("\nService ready!")
    print("Endpoints:")
    print("  - GET  /health          - Health check")
    print("  - POST /ocr             - OCR (multipart/form-data)")
    print("  - POST /ocr/base64      - OCR (JSON base64)")
    print("\nStarting server on http://0.0.0.0:5000")
    print("=" * 60)
    
    # 启动服务
    app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)
