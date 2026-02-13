#!/usr/bin/env python3
"""
测试OCR微服务
"""
import requests
import os

def test_health():
    """测试健康检查接口"""
    print("Testing /health endpoint...")
    try:
        response = requests.get("http://localhost:5000/health", timeout=5)
        print(f"Status: {response.status_code}")
        print(f"Response: {response.json()}")
        return response.status_code == 200
    except Exception as e:
        print(f"Error: {e}")
        return False

def test_ocr():
    """测试OCR接口"""
    print("\nTesting /ocr endpoint...")
    
    # 使用测试图片
    test_image = r"F:\AutoMark\AI-dama\captcha\20260122144332_62_269.jpg"
    
    if not os.path.exists(test_image):
        print(f"Test image not found: {test_image}")
        return False
    
    try:
        with open(test_image, 'rb') as f:
            files = {'image': ('test.jpg', f, 'image/jpeg')}
            response = requests.post("http://localhost:5000/ocr", files=files, timeout=60)
        
        print(f"Status: {response.status_code}")
        
        if response.status_code == 200:
            result = response.json()
            print(f"Request ID: {result.get('request_id')}")
            print(f"Status: {result.get('status')}")
            print(f"Processing time: {result.get('processing_time_ms')} ms")
            print(f"Confidence: {result.get('confidence_score')}")
            print(f"Text regions: {len(result.get('text_regions', []))}")
            print(f"Full text preview: {result.get('full_text', '')[:100]}...")
            return True
        else:
            print(f"Error: {response.text}")
            return False
            
    except Exception as e:
        print(f"Error: {e}")
        return False

if __name__ == "__main__":
    print("=" * 60)
    print("OCR Microservice Test")
    print("=" * 60)
    
    # 测试健康检查
    if test_health():
        print("✅ Health check passed")
    else:
        print("❌ Health check failed")
        exit(1)
    
    # 测试OCR
    if test_ocr():
        print("\n✅ OCR test passed")
    else:
        print("\n❌ OCR test failed")
        exit(1)
    
    print("\n" + "=" * 60)
    print("All tests passed!")
    print("=" * 60)
