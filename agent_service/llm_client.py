"""
LLM客户端封装
支持多种LLM提供商：Google Gemini (默认), OpenAI, 通义千问
"""
import os
import json
from typing import Optional


class LLMClient:
    def __init__(self, config):
        self.config = config
        self.provider = config.LLM_PROVIDER

        if self.provider == "gemini":
            from google import genai
            from google.genai import types
            self.genai = genai
            self.types = types
            # 创建 Gemini 客户端
            self.client = genai.Client(api_key=config.GOOGLE_API_KEY)
        elif self.provider == "openai":
            from openai import OpenAI
            self.client = OpenAI(api_key=config.OPENAI_API_KEY)
        elif self.provider == "qwen":
            from openai import OpenAI
            # 通义千问兼容OpenAI接口
            self.client = OpenAI(
                api_key=config.QWEN_API_KEY,
                base_url="https://dashscope.aliyuncs.com/compatible-mode/v1"
            )
        else:
            raise ValueError(f"Unsupported LLM provider: {self.provider}")

    def call(
        self,
        system_prompt: str,
        user_prompt: str,
        temperature: float = 0.3,
        max_tokens: int = 2000
    ) -> str:
        """
        调用LLM
        """
        try:
            if self.provider == "gemini":
                return self._call_gemini(system_prompt, user_prompt, temperature, max_tokens)
            else:
                return self._call_openai_compatible(system_prompt, user_prompt, temperature, max_tokens)

        except Exception as e:
            print(f"LLM call error: {e}")
            raise

    def _call_gemini(
        self,
        system_prompt: str,
        user_prompt: str,
        temperature: float,
        max_tokens: int
    ) -> str:
        """
        调用 Google Gemini API (使用 Google ADK)
        """
        # 构建生成配置
        generate_config = self.types.GenerateContentConfig(
            temperature=temperature,
            max_output_tokens=max_tokens,
            response_mime_type="application/json",  # 强制 JSON 输出
            system_instruction=system_prompt
        )

        # 调用 Gemini API
        response = self.client.models.generate_content(
            model=self.config.LLM_MODEL,
            contents=user_prompt,
            config=generate_config
        )

        # 提取响应文本
        return response.text

    def _call_openai_compatible(
        self,
        system_prompt: str,
        user_prompt: str,
        temperature: float,
        max_tokens: int
    ) -> str:
        """
        调用 OpenAI 兼容接口 (OpenAI, 通义千问)
        """
        response = self.client.chat.completions.create(
            model=self.config.LLM_MODEL,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ],
            temperature=temperature,
            max_tokens=max_tokens,
            response_format={"type": "json_object"} if self.provider == "openai" else None
        )

        content = response.choices[0].message.content
        return content
