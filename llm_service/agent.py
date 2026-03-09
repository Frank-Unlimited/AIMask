"""隐私信息识别和打码决策Agent"""
import json
from typing import List, Dict, Any, Optional
from openai import OpenAI
import os

from prompts.prompt_loader import get_prompt


class PrivacyMaskingAgent:
    """隐私打码决策专家Agent"""
    
    def __init__(self, api_key: str, api_base: str, model: str, prompt_version: str = None):
        self.client = OpenAI(api_key=api_key, base_url=api_base)
        self.model = model
        self.prompt_version = prompt_version
        self.system_prompt = get_prompt(prompt_version)
    
    def analyze(
        self,
        ocr_texts: List[str],
        current_masked: List[Dict[str, str]],
        user_feedback: Dict[str, Any],
        history: List[Dict[str, str]] = None
    ) -> Dict[str, Any]:
        """
        分析用户反馈并给出打码建议
        
        Args:
            ocr_texts: OCR文本列表
            current_masked: 当前已打码内容
            user_feedback: 用户反馈
            history: 历史对话消息（可选）
        
        Returns:
            打码建议结果
        """
        user_message = self._build_user_message(ocr_texts, current_masked, user_feedback)
        
        # 构建消息列表
        messages = [{"role": "system", "content": self.system_prompt}]
        
        # 添加历史消息
        if history:
            messages.extend(history)
        
        # 添加当前消息
        messages.append({"role": "user", "content": user_message})
        
        try:
            response = self.client.chat.completions.create(
                model=self.model,
                messages=messages,
                temperature=0.3,
                response_format={"type": "json_object"}
            )
            
            assistant_message = response.choices[0].message.content
            result = json.loads(assistant_message)
            
            # 返回结果和assistant消息（用于保存到历史）
            result["_assistant_message"] = assistant_message
            result["_user_message"] = user_message
            
            return result
            
        except Exception as e:
            return {
                "mask_recommendations": [],
                "error": str(e),
                "_assistant_message": None,
                "_user_message": user_message
            }
    
    def _build_user_message(
        self,
        ocr_texts: List[str],
        current_masked: List[Dict[str, str]],
        user_feedback: Dict[str, Any]
    ) -> str:
        """构建用户消息"""
        
        message_parts = [
            "# OCR文本内容：",
            json.dumps(ocr_texts, ensure_ascii=False, indent=2),
            "\n# 当前已打码内容：",
            json.dumps(current_masked, ensure_ascii=False, indent=2),
            "\n# 用户反馈：",
            json.dumps(user_feedback, ensure_ascii=False, indent=2),
            "\n请根据以上信息给出打码建议。"
        ]
        
        return "\n".join(message_parts)
