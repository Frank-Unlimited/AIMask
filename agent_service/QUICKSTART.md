# Agent微服务 - 快速启动指南

## 🚀 5分钟快速启动

### 步骤1: 配置API Key

```bash
cd agent_service
cp .env.example .env
```

编辑`.env`文件，配置你的API Key：

```env
# 使用OpenAI（推荐）
LLM_PROVIDER=openai
LLM_MODEL=gpt-4
OPENAI_API_KEY=sk-your-key-here

# 或使用通义千问
# LLM_PROVIDER=qwen
# LLM_MODEL=qwen-max
# QWEN_API_KEY=sk-your-key-here
```

### 步骤2: 启动服务

**Windows:**
```bash
start_service.bat
```

**Linux/Mac:**
```bash
chmod +x start_service.sh
./start_service.sh
```

服务启动在 `http://localhost:5001`

### 步骤3: 测试服务

打开新终端：

```bash
cd agent_service
python test_agent.py
```

## 📝 使用示例

### 示例1: 初始打码

```bash
curl -X POST http://localhost:5001/agent/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "task_mode": "initial",
    "ocr_texts": [
      "我是王雷，电话：+86 138 0000 0000"
    ]
  }'
```

### 示例2: 意图推断

```bash
curl -X POST http://localhost:5001/agent/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "task_mode": "iterative",
    "ocr_texts": ["尊敬的启迪科技团队：您好！"],
    "current_masked": [],
    "user_feedback": {
      "clicked_char": "启",
      "context_window": "尊敬的启迪科技团队：您好！",
      "action_type": "add"
    }
  }'
```

### 示例3: 批量打码

```bash
curl -X POST http://localhost:5001/agent/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "task_mode": "batch",
    "ocr_texts": ["收件人：李华"],
    "batch_rules": {
      "must_mask": ["姓名"],
      "skip": ["地址"],
      "learned_patterns": []
    }
  }'
```

## 🔧 与Android集成

在Android的`MosaicUtils.kt`中调用：

```kotlin
suspend fun callAgentService(
    taskMode: String,
    ocrTexts: List<String>,
    currentMasked: List<String>? = null,
    userFeedback: Map<String, Any>? = null
): String? = withContext(Dispatchers.IO) {
    try {
        val agentServiceUrl = "http://192.168.1.100:5001/agent/analyze"
        
        val requestBody = JSONObject().apply {
            put("task_mode", taskMode)
            put("ocr_texts", JSONArray(ocrTexts))
            currentMasked?.let { put("current_masked", JSONArray(it)) }
            userFeedback?.let { put("user_feedback", JSONObject(it)) }
        }
        
        val url = URL(agentServiceUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        
        connection.outputStream.use { os ->
            os.write(requestBody.toString().toByteArray())
        }
        
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e("AgentService", "Error: ${e.message}")
        null
    }
}
```

## 📊 性能优化

### 1. 使用更快的模型

```env
# 开发环境：使用GPT-3.5（更快更便宜）
LLM_MODEL=gpt-3.5-turbo

# 生产环境：使用GPT-4（更准确）
LLM_MODEL=gpt-4
```

### 2. 调整温度参数

```env
# 更稳定的输出
TEMPERATURE=0.1

# 更有创造性的输出
TEMPERATURE=0.5
```

### 3. 批量处理优化

batch模式会更快，因为Prompt更简单。

## ❓ 常见问题

### Q: 服务启动失败？
A: 检查.env文件是否配置，API Key是否有效

### Q: LLM响应慢？
A: 切换到gpt-3.5-turbo或通义千问

### Q: JSON解析失败？
A: 降低temperature到0.1，或增加max_tokens

### Q: 如何部署到云端？
A: 使用Docker：
```bash
docker build -t agent-service .
docker run -p 5001:5001 --env-file .env agent-service
```

## 📚 更多文档

- [完整API文档](README.md)
- [架构设计](../通信架构分析.md)
- [Prompt工程](prompts.py)
