"""
配置管理
"""
import os
from dotenv import load_dotenv

load_dotenv()


class Config:
    # LLM配置
    LLM_PROVIDER = os.getenv("LLM_PROVIDER", "gemini")  # gemini/openai/qwen
    LLM_MODEL = os.getenv("LLM_MODEL", "gemini-2.0-flash")

    # API Keys
    GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY")  # Gemini API Key
    OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
    QWEN_API_KEY = os.getenv("QWEN_API_KEY")

    # 服务配置
    PORT = int(os.getenv("AGENT_PORT", 5001))

    # LLM参数
    TEMPERATURE = float(os.getenv("TEMPERATURE", 0.3))
    MAX_TOKENS = int(os.getenv("MAX_TOKENS", 2000))
