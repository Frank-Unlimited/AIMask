# Agent微服务 - 隐私信息识别和打码决策

## 概述

基于 **Google ADK + Gemini** 构建的统一Agent，处理三种任务模式：
- **initial**: 初始打码分析
- **iterative**: 意图推断（从字符级反馈推断完整意图）
- **batch**: 批量应用规则

## 技术栈

- **Agent框架**: Google ADK (Agent Development Kit)
- **大语言模型**: Google Gemini (默认 gemini-2.0-flash)
- **Web框架**: FastAPI
- **备用模型**: OpenAI GPT-4, 通义千问

## 快速启动

### 1. 配置环境

```bash
# 复制配置文件
cp .env.example .env

# 编辑.env，配置 Google API Key
# 从 https://aistudio.google.com/apikey 获取
```

### 2. 安装依赖

```bash
pip install -r requirements.txt
```

### 3. 启动服务

**Windows:**
```bash
start_service.bat
```

**Linux/Mac:**
```bash
chmod +x start_service.sh
./start_service.sh
```

服务将在 `http://localhost:5001` 启动

### 4. 测试服务

```bash
python test_agent.py
```

## API文档

### 1. 健康检查

```http
GET /health
```

**响应:**
```json
{
  "status": "healthy",
  "service": "agent-service",
  "version": "2.0.0",
  "model": "gemini-2.0-flash",
  "provider": "gemini",
  "framework": "Google ADK"
}
```

### 2. 统一分析接口

```http
POST /agent/analyze
Content-Type: application/json
```

#### Initial模式

**请求:**
```json
{
  "task_mode": "initial",
  "ocr_texts": [
    "尊敬的启迪科技团队：您好！",
    "我是王雷，联系电话：+86 138 0000 0000"
  ]
}
```

**响应:**
```json
{
  "status": "success",
  "mask_recommendations": [
    {
      "text": "启迪科技",
      "action": "add",
      "category": "公司名称",
      "reason": "识别为公司名称",
      "confidence": 0.85
    },
    {
      "text": "王雷",
      "action": "add",
      "category": "姓名",
      "reason": "识别为人名",
      "confidence": 0.95
    }
  ],
  "privacy_categories": {
    "姓名": ["王雷"],
    "电话": ["+86 138 0000 0000"],
    "邮箱": [],
    "公司名称": ["启迪科技"],
    "地址": [],
    "银行卡号": [],
    "身份证号": []
  },
  "summary": "识别到4类隐私信息，建议打码4处",
  "learned_rules": [],
  "meta": {
    "request_id": "req_xxx",
    "task_mode": "initial",
    "processing_time_ms": 1234,
    "model_used": "gemini-2.0-flash",
    "provider": "gemini"
  }
}
```

#### Iterative模式

**请求:**
```json
{
  "task_mode": "iterative",
  "ocr_texts": [
    "尊敬的启迪科技团队：您好！",
    "我是王雷"
  ],
  "current_masked": ["王雷"],
  "user_feedback": {
    "clicked_char": "启",
    "context_window": "尊敬的启迪科技团队：您好！",
    "action_type": "add",
    "natural_language": null
  }
}
```

**响应:**
```json
{
  "status": "success",
  "mask_recommendations": [
    {
      "text": "启迪科技",
      "action": "add",
      "category": "公司名称",
      "reason": "用户点击了'启'字，推断为公司名称'启迪科技'",
      "confidence": 0.92
    }
  ],
  "privacy_categories": {
    "姓名": ["王雷"],
    "公司名称": ["启迪科技"],
    ...
  },
  "summary": "根据用户点击推断需要打码公司名称",
  "learned_rules": ["公司名称需要打码"]
}
```

#### Batch模式

**请求:**
```json
{
  "task_mode": "batch",
  "ocr_texts": [
    "收件人：李华",
    "公司：迅达广告有限公司"
  ],
  "batch_rules": {
    "must_mask": ["姓名", "公司名称"],
    "skip": ["地址"],
    "learned_patterns": ["公司名称需要打码"]
  }
}
```

### 3. 生成批量规则

```http
POST /agent/generate-batch-rules
Content-Type: application/json
```

**请求:**
```json
[
  {
    "category": "公司名称",
    "action_type": "add"
  },
  {
    "category": "地址",
    "action_type": "remove"
  }
]
```

**响应:**
```json
{
  "status": "success",
  "batch_rules": {
    "must_mask": ["公司名称"],
    "skip": ["地址"],
    "learned_patterns": [
      "公司名称需要打码",
      "地址不需要打码"
    ]
  }
}
```

## 配置说明

### 支持的LLM提供商

1. **Google Gemini** (默认，推荐)
   ```env
   LLM_PROVIDER=gemini
   LLM_MODEL=gemini-2.0-flash
   GOOGLE_API_KEY=your_google_api_key
   ```

   可选模型:
   - `gemini-2.0-flash` - 最新快速模型 (推荐)
   - `gemini-1.5-pro` - 高性能模型
   - `gemini-1.5-flash` - 快速模型

2. **OpenAI** (备用)
   ```env
   LLM_PROVIDER=openai
   LLM_MODEL=gpt-4
   OPENAI_API_KEY=sk-xxx
   ```

3. **通义千问** (备用)
   ```env
   LLM_PROVIDER=qwen
   LLM_MODEL=qwen-max
   QWEN_API_KEY=sk-xxx
   ```

### 性能参数

- `TEMPERATURE`: 0.3 (低温度保证稳定性)
- `MAX_TOKENS`: 2000

## 架构说明

```
agent_service/
├── app.py              # FastAPI主入口
├── agent.py            # UnifiedAgent核心逻辑
├── prompts.py          # Prompt管理
├── llm_client.py       # LLM客户端 (Google ADK + 备用)
├── config.py           # 配置管理
├── requirements.txt    # 依赖 (google-genai)
├── .env.example        # 配置示例
└── test_agent.py       # 测试脚本
```

## 核心特性

1. **Google ADK集成**: 使用Google官方Agent开发框架
2. **Gemini模型**: 默认使用Gemini 2.0 Flash
3. **统一Agent**: 一个Agent处理三种任务模式
4. **意图推断**: 从字符级反馈推断完整意图
5. **规则学习**: 从用户操作中学习打码规则
6. **JSON响应**: 强制JSON输出格式

## 故障排查

### 问题1: google-genai 安装失败
```bash
pip install --upgrade pip
pip install google-genai
```

### 问题2: API Key 无效
- 确认从 https://aistudio.google.com/apikey 获取
- 检查是否启用了 Gemini API

### 问题3: 服务无法启动
- 检查.env文件是否配置
- 检查端口5001是否被占用

### 问题4: LLM调用失败
- 检查网络连接（可能需要代理）
- 查看API配额限制

### 问题5: JSON解析失败
- Gemini 使用 response_mime_type="application/json" 强制JSON输出
- 如仍失败，降低temperature参数

## 获取 Google API Key

1. 访问 [Google AI Studio](https://aistudio.google.com/apikey)
2. 登录 Google 账号
3. 点击 "Create API Key"
4. 复制 API Key 到 .env 文件

## 更新日志

### v2.0.0
- 切换为 Google ADK + Gemini 作为默认LLM
- 添加 google-genai 依赖
- 支持 Gemini 2.0 Flash 模型
- 保留 OpenAI/Qwen 作为备用

### v1.0.0
- 初始版本，使用 OpenAI API
