#!/usr/bin/env python3
"""
测试Agent微服务
"""
import requests
import json


BASE_URL = "http://localhost:5001"


def test_health():
    """测试健康检查"""
    print("\n" + "=" * 60)
    print("Testing /health endpoint...")
    print("=" * 60)
    
    response = requests.get(f"{BASE_URL}/health")
    print(f"Status: {response.status_code}")
    print(f"Response: {json.dumps(response.json(), indent=2, ensure_ascii=False)}")
    return response.status_code == 200


def test_initial_mode():
    """测试initial模式"""
    print("\n" + "=" * 60)
    print("Testing initial mode...")
    print("=" * 60)
    
    data = {
        "task_mode": "initial",
        "ocr_texts": [
            "尊敬的启迪科技团队：您好！",
            "我是王雷，联系电话：+86 138 0000 0000",
            "邮箱：wang.lei@xundamedia.com",
            "公司地址：北京市朝阳区建国路88号"
        ]
    }
    
    response = requests.post(f"{BASE_URL}/agent/analyze", json=data)
    print(f"Status: {response.status_code}")
    
    if response.status_code == 200:
        result = response.json()
        print(f"\nSummary: {result['summary']}")
        print(f"\nMask Recommendations ({len(result['mask_recommendations'])}):")
        for rec in result['mask_recommendations']:
            print(f"  - {rec['text']} ({rec['category']}) - {rec['action']}")
            print(f"    Reason: {rec['reason']}")
            print(f"    Confidence: {rec['confidence']}")
        
        print(f"\nPrivacy Categories:")
        for category, items in result['privacy_categories'].items():
            if items:
                print(f"  - {category}: {items}")
        
        return True
    else:
        print(f"Error: {response.text}")
        return False


def test_iterative_mode():
    """测试iterative模式"""
    print("\n" + "=" * 60)
    print("Testing iterative mode...")
    print("=" * 60)
    
    data = {
        "task_mode": "iterative",
        "ocr_texts": [
            "尊敬的启迪科技团队：您好！",
            "我是王雷，联系电话：+86 138 0000 0000"
        ],
        "current_masked": ["王雷", "+86 138 0000 0000"],
        "user_feedback": {
            "clicked_char": "启",
            "context_window": "尊敬的启迪科技团队：您好！",
            "action_type": "add",
            "natural_language": None
        }
    }
    
    response = requests.post(f"{BASE_URL}/agent/analyze", json=data)
    print(f"Status: {response.status_code}")
    
    if response.status_code == 200:
        result = response.json()
        print(f"\nSummary: {result['summary']}")
        print(f"\nMask Recommendations:")
        for rec in result['mask_recommendations']:
            print(f"  - {rec['text']} ({rec['category']}) - {rec['action']}")
            print(f"    Reason: {rec['reason']}")
        
        print(f"\nLearned Rules:")
        for rule in result['learned_rules']:
            print(f"  - {rule}")
        
        return True
    else:
        print(f"Error: {response.text}")
        return False


def test_batch_mode():
    """测试batch模式"""
    print("\n" + "=" * 60)
    print("Testing batch mode...")
    print("=" * 60)
    
    data = {
        "task_mode": "batch",
        "ocr_texts": [
            "收件人：李华",
            "公司：迅达广告有限公司",
            "地址：上海市浦东新区世纪大道100号",
            "电话：+86 139 0000 0000"
        ],
        "batch_rules": {
            "must_mask": ["姓名", "电话", "公司名称"],
            "skip": ["地址"],
            "learned_patterns": [
                "公司名称需要打码",
                "地址不打码"
            ]
        }
    }
    
    response = requests.post(f"{BASE_URL}/agent/analyze", json=data)
    print(f"Status: {response.status_code}")
    
    if response.status_code == 200:
        result = response.json()
        print(f"\nSummary: {result['summary']}")
        print(f"\nMask Recommendations:")
        for rec in result['mask_recommendations']:
            print(f"  - {rec['text']} ({rec['category']}) - {rec['action']}")
        
        return True
    else:
        print(f"Error: {response.text}")
        return False


if __name__ == "__main__":
    print("=" * 60)
    print("Agent Microservice Test Suite")
    print("=" * 60)
    
    # 测试健康检查
    if not test_health():
        print("\n❌ Health check failed. Is the service running?")
        exit(1)
    
    print("\n✅ Health check passed")
    
    # 测试initial模式
    if test_initial_mode():
        print("\n✅ Initial mode test passed")
    else:
        print("\n❌ Initial mode test failed")
    
    # 测试iterative模式
    if test_iterative_mode():
        print("\n✅ Iterative mode test passed")
    else:
        print("\n❌ Iterative mode test failed")
    
    # 测试batch模式
    if test_batch_mode():
        print("\n✅ Batch mode test passed")
    else:
        print("\n❌ Batch mode test failed")
    
    print("\n" + "=" * 60)
    print("All tests completed!")
    print("=" * 60)
