#!/usr/bin/env python3
"""
OCR功能快速测试脚本
验证命令行参数传递是否正常工作
"""
import os
import sys
import subprocess
import json

def test_ocr_script():
    """测试OCR脚本的命令行参数功能"""
    
    # 路径配置
    captcha_root = r"F:\AutoMark\AI-dama\captcha"
    python_exe = os.path.join(captcha_root, ".venv", "Scripts", "python.exe")
    script_path = os.path.join(captcha_root, "ocr_project", "run_ocr.py")
    test_image = os.path.join(captcha_root, "20260122144332_62_269.jpg")
    result_path = os.path.join(captcha_root, "ocr_project", "result.json")
    
    # 验证文件存在
    print("=" * 60)
    print("OCR功能测试")
    print("=" * 60)
    
    print(f"\n1. 检查Python环境: {python_exe}")
    if not os.path.exists(python_exe):
        print(f"   ❌ Python可执行文件不存在")
        return False
    print(f"   ✅ Python环境正常")
    
    print(f"\n2. 检查OCR脚本: {script_path}")
    if not os.path.exists(script_path):
        print(f"   ❌ OCR脚本不存在")
        return False
    print(f"   ✅ OCR脚本存在")
    
    print(f"\n3. 检查测试图片: {test_image}")
    if not os.path.exists(test_image):
        print(f"   ❌ 测试图片不存在")
        return False
    print(f"   ✅ 测试图片存在")
    
    # 执行OCR
    print(f"\n4. 执行OCR识别...")
    try:
        cmd = [python_exe, script_path, "--image", test_image]
        print(f"   命令: {' '.join(cmd)}")
        
        result = subprocess.run(
            cmd,
            cwd=captcha_root,
            capture_output=True,
            text=True,
            timeout=60
        )
        
        if result.returncode != 0:
            print(f"   ❌ OCR执行失败 (退出码: {result.returncode})")
            print(f"   标准输出:\n{result.stdout}")
            print(f"   错误输出:\n{result.stderr}")
            return False
        
        print(f"   ✅ OCR执行成功")
        print(f"\n   输出预览:")
        for line in result.stdout.splitlines()[:10]:
            print(f"      {line}")
        
    except subprocess.TimeoutExpired:
        print(f"   ❌ OCR执行超时")
        return False
    except Exception as e:
        print(f"   ❌ 执行异常: {e}")
        return False
    
    # 验证结果文件
    print(f"\n5. 验证结果文件: {result_path}")
    if not os.path.exists(result_path):
        print(f"   ❌ 结果文件未生成")
        return False
    
    print(f"   ✅ 结果文件已生成")
    
    # 解析JSON
    print(f"\n6. 解析JSON结果...")
    try:
        with open(result_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        print(f"   ✅ JSON格式正确")
        print(f"\n   结果摘要:")
        print(f"      - 请求ID: {data.get('request_id', 'N/A')}")
        print(f"      - 状态: {data.get('status', 'N/A')}")
        print(f"      - 处理时间: {data.get('processing_time_ms', 'N/A')} ms")
        print(f"      - 置信度: {data.get('confidence_score', 'N/A')}")
        
        metadata = data.get('image_metadata', {})
        dims = metadata.get('dimensions', {})
        print(f"      - 图片尺寸: {dims.get('width', 'N/A')} x {dims.get('height', 'N/A')}")
        
        regions = data.get('text_regions', [])
        print(f"      - 识别区域数: {len(regions)}")
        
        if regions:
            print(f"\n   前5个识别区域:")
            for i, region in enumerate(regions[:5]):
                text = region.get('text', '')
                bbox = region.get('bounding_box', {})
                conf = bbox.get('confidence', 0)
                print(f"      [{i+1}] '{text}' (置信度: {conf:.4f})")
        
    except json.JSONDecodeError as e:
        print(f"   ❌ JSON解析失败: {e}")
        return False
    except Exception as e:
        print(f"   ❌ 读取异常: {e}")
        return False
    
    print("\n" + "=" * 60)
    print("✅ 所有测试通过！OCR功能正常")
    print("=" * 60)
    return True

if __name__ == "__main__":
    success = test_ocr_script()
    sys.exit(0 if success else 1)
