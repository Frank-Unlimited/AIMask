"""
Agent微服务 - 隐私信息识别和打码决策
统一Agent处理三种任务：initial, iterative, batch
"""
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import List, Dict, Optional
import uvicorn
import time

from agent import UnifiedAgent
from config import Config

app = FastAPI(title="Privacy Masking Agent Service", version="1.0.0")

# CORS配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 初始化Agent
config = Config()
agent = UnifiedAgent(config)


# ============ 请求/响应模型 ============

class UserFeedback(BaseModel):
    clicked_char: str = Field(..., description="用户点击的1-2个字符")
    context_window: str = Field(..., description="包含该字符的完整文本段落")
    action_type: str = Field(..., description="add或remove")
    natural_language: Optional[str] = Field(None, description="可选的自然语言指令")


class BatchRules(BaseModel):
    must_mask: List[str] = Field(..., description="必须打码的分类")
    skip: List[str] = Field(..., description="跳过的分类")
    learned_patterns: List[str] = Field(default=[], description="学习到的规则")


class AnalyzeRequest(BaseModel):
    task_mode: str = Field(..., description="initial, iterative, 或 batch")
    ocr_texts: List[str] = Field(..., description="OCR识别的文本段落列表")
    current_masked: Optional[List[str]] = Field(None, description="当前已打码的内容")
    user_feedback: Optional[UserFeedback] = Field(None, description="用户反馈")
    batch_rules: Optional[BatchRules] = Field(None, description="批量规则")
    context: Optional[Dict] = Field(default={}, description="额外上下文")


class MaskRecommendation(BaseModel):
    text: str
    action: str
    category: str
    reason: str
    confidence: float


class AnalyzeResponse(BaseModel):
    status: str
    mask_recommendations: List[MaskRecommendation]
    privacy_categories: Dict[str, List[str]]
    summary: str
    learned_rules: List[str] = []
    meta: Dict


# ============ API端点 ============

@app.get("/health")
async def health_check():
    """健康检查"""
    return {
        "status": "healthy",
        "service": "agent-service",
        "version": "2.0.0",
        "model": config.LLM_MODEL,
        "provider": config.LLM_PROVIDER,
        "framework": "Google ADK" if config.LLM_PROVIDER == "gemini" else "OpenAI SDK"
    }


@app.post("/agent/analyze", response_model=AnalyzeResponse)
async def analyze(request: AnalyzeRequest):
    """
    统一分析接口
    根据task_mode处理不同任务：initial, iterative, batch
    """
    start_time = time.time()
    request_id = f"req_{int(time.time() * 1000)}"
    
    try:
        # 验证输入
        if request.task_mode not in ["initial", "iterative", "batch"]:
            raise HTTPException(
                status_code=400,
                detail=f"Invalid task_mode: {request.task_mode}"
            )
        
        if not request.ocr_texts:
            raise HTTPException(
                status_code=400,
                detail="ocr_texts cannot be empty"
            )
        
        # iterative模式验证
        if request.task_mode == "iterative":
            if not request.current_masked:
                raise HTTPException(
                    status_code=400,
                    detail="current_masked is required for iterative mode"
                )
            if not request.user_feedback:
                raise HTTPException(
                    status_code=400,
                    detail="user_feedback is required for iterative mode"
                )
        
        # batch模式验证
        if request.task_mode == "batch":
            if not request.batch_rules:
                raise HTTPException(
                    status_code=400,
                    detail="batch_rules is required for batch mode"
                )
        
        # 调用Agent
        result = agent.analyze(
            task_mode=request.task_mode,
            ocr_texts=request.ocr_texts,
            current_masked=request.current_masked,
            user_feedback=request.user_feedback.dict() if request.user_feedback else None,
            batch_rules=request.batch_rules.dict() if request.batch_rules else None,
            context=request.context
        )
        
        # 添加元数据
        processing_time = int((time.time() - start_time) * 1000)
        result["meta"] = {
            "request_id": request_id,
            "task_mode": request.task_mode,
            "processing_time_ms": processing_time,
            "model_used": config.LLM_MODEL,
            "provider": config.LLM_PROVIDER
        }
        
        return result
        
    except HTTPException:
        raise
    except Exception as e:
        print(f"Error processing request: {e}")
        import traceback
        traceback.print_exc()
        raise HTTPException(
            status_code=500,
            detail=f"Internal server error: {str(e)}"
        )


@app.post("/agent/generate-batch-rules")
async def generate_batch_rules(interaction_history: List[Dict]):
    """
    从交互历史生成批量规则
    """
    try:
        rules = agent.generate_batch_rules(interaction_history)
        return {
            "status": "success",
            "batch_rules": rules
        }
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Error generating batch rules: {str(e)}"
        )


if __name__ == "__main__":
    print("=" * 60)
    print("Agent Microservice v2.0 - Powered by Google ADK")
    print("=" * 60)
    print(f"LLM Provider: {config.LLM_PROVIDER}")
    print(f"LLM Model: {config.LLM_MODEL}")
    print(f"Framework: {'Google ADK' if config.LLM_PROVIDER == 'gemini' else 'OpenAI SDK'}")
    print(f"\nEndpoints:")
    print("  - GET  /health                      - Health check")
    print("  - POST /agent/analyze               - Unified analysis")
    print("  - POST /agent/generate-batch-rules  - Generate batch rules")
    print(f"\nStarting server on http://0.0.0.0:{config.PORT}")
    print("=" * 60)

    uvicorn.run(app, host="0.0.0.0", port=config.PORT)
