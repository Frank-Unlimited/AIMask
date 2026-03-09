"""隐私打码决策服务API"""
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional
from dotenv import load_dotenv
import os
import uvicorn

from agent import PrivacyMaskingAgent
from prompts.prompt_loader import list_prompt_versions
from session_manager import get_session_manager

# 加载环境变量
load_dotenv()

app = FastAPI(
    title="隐私打码决策服务",
    description="基于LLM的隐私信息识别和打码决策API",
    version="1.0.0"
)

# 添加CORS支持
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 初始化Agent
agent = PrivacyMaskingAgent(
    api_key=os.getenv("LLM_API_KEY"),
    api_base=os.getenv("LLM_API_BASE"),
    model=os.getenv("LLM_MODEL", "gpt-4"),
    prompt_version=os.getenv("PROMPT_VERSION", None)  # 从环境变量读取版本
)


class UserFeedback(BaseModel):
    """用户反馈模型"""
    clicked_char: str = Field(..., description="用户点击的1-2个字符")
    context_window: str = Field(..., description="包含该字符的完整文本段落")
    action_type: str = Field(..., description="操作类型: add 或 remove")
    natural_language: Optional[str] = Field(None, description="可选的自然语言指令")


class MaskingRequest(BaseModel):
    """打码决策请求模型"""
    ocr_texts: List[str] = Field(..., description="所有OCR文本")
    current_masked: List[Dict[str, str]] = Field(default=[], description="当前已打码的内容列表")
    user_feedback: UserFeedback = Field(..., description="用户反馈信息")
    session_id: Optional[str] = Field(None, description="会话ID，用于保持对话上下文")


class MaskRecommendation(BaseModel):
    """打码建议模型"""
    text: str = Field(..., description="完整的文本内容")
    action: str = Field(..., description="操作: add 或 remove")
    category: str = Field(..., description="隐私分类")
    reason: str = Field(..., description="决策理由")
    confidence: float = Field(..., ge=0.0, le=1.0, description="置信度 0.0-1.0")


class MaskingResponse(BaseModel):
    """打码决策响应模型"""
    mask_recommendations: List[MaskRecommendation]
    error: Optional[str] = None


@app.get("/")
async def root():
    """健康检查"""
    return {
        "service": "隐私打码决策服务",
        "status": "running",
        "version": "1.0.0"
    }


@app.post("/analyze", response_model=MaskingResponse)
async def analyze_masking(request: MaskingRequest):
    """
    分析用户反馈并给出打码建议
    
    - **ocr_texts**: 所有OCR识别的文本列表
    - **current_masked**: 当前已打码的内容（包含category和text）
    - **user_feedback**: 用户的点击反馈和操作意图
    - **session_id**: 可选的会话ID，用于保持对话上下文
    """
    try:
        session_manager = get_session_manager()
        
        # 获取会话历史
        history = None
        if request.session_id:
            history = session_manager.get_history(request.session_id)
        
        # 调用Agent分析
        result = agent.analyze(
            ocr_texts=request.ocr_texts,
            current_masked=request.current_masked,
            user_feedback=request.user_feedback.model_dump(),
            history=history
        )
        
        # 保存对话历史
        if request.session_id and "_user_message" in result:
            session_manager.add_message(
                request.session_id,
                "user",
                result.pop("_user_message")
            )
            if "_assistant_message" in result and result["_assistant_message"]:
                session_manager.add_message(
                    request.session_id,
                    "assistant",
                    result.pop("_assistant_message")
                )
        
        return MaskingResponse(**result)
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"分析失败: {str(e)}")


@app.get("/health")
async def health_check():
    """服务健康检查"""
    return {
        "status": "healthy",
        "prompt_version": agent.prompt_version or "default"
    }


@app.get("/prompts")
async def get_prompts():
    """获取所有可用的提示词版本"""
    return {
        "versions": list_prompt_versions(),
        "current": agent.prompt_version or "default"
    }


@app.get("/sessions")
async def list_sessions():
    """列出所有活跃的会话"""
    session_manager = get_session_manager()
    sessions = session_manager.list_sessions()
    return {
        "sessions": sessions,
        "count": len(sessions)
    }


@app.get("/sessions/{session_id}")
async def get_session_info(session_id: str):
    """获取指定会话的信息"""
    session_manager = get_session_manager()
    info = session_manager.get_session_info(session_id)
    
    if info is None:
        raise HTTPException(status_code=404, detail="会话不存在或已过期")
    
    return info


@app.delete("/sessions/{session_id}")
async def clear_session(session_id: str):
    """清除指定会话的历史"""
    session_manager = get_session_manager()
    session_manager.clear_session(session_id)
    return {"message": f"会话 {session_id} 已清除"}


if __name__ == "__main__":
    host = os.getenv("SERVICE_HOST", "0.0.0.0")
    port = int(os.getenv("SERVICE_PORT", 8004))
    
    uvicorn.run(app, host=host, port=port)
