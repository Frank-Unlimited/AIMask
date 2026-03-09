"""提示词加载器"""
import os
from pathlib import Path
from typing import List, Optional


class PromptLoader:
    """提示词加载和管理"""
    
    def __init__(self, prompts_dir: str = None):
        """
        初始化提示词加载器
        
        Args:
            prompts_dir: 提示词文件目录，默认为当前文件所在目录
        """
        if prompts_dir is None:
            prompts_dir = Path(__file__).parent
        self.prompts_dir = Path(prompts_dir)
    
    def load_prompt(self, version: str) -> str:
        """
        加载指定版本的提示词
        
        Args:
            version: 提示词版本（如 v1, v2）
        
        Returns:
            提示词文本内容
        
        Raises:
            FileNotFoundError: 如果提示词文件不存在
        """
        prompt_file = self.prompts_dir / f"prompt_{version}.txt"
        
        if not prompt_file.exists():
            raise FileNotFoundError(
                f"提示词文件不存在: {prompt_file}\n"
                f"可用版本: {', '.join(self.list_versions())}"
            )
        
        with open(prompt_file, 'r', encoding='utf-8') as f:
            return f.read()
    
    def list_versions(self) -> List[str]:
        """
        列出所有可用的提示词版本
        
        Returns:
            版本列表（如 ['v1', 'v2']）
        """
        versions = []
        for file in self.prompts_dir.glob("prompt_*.txt"):
            # 从文件名提取版本号：prompt_v1.txt -> v1
            version = file.stem.replace("prompt_", "")
            versions.append(version)
        return sorted(versions)
    
    def get_default_version(self) -> str:
        """
        获取默认版本（最新版本）
        
        Returns:
            默认版本号
        """
        versions = self.list_versions()
        if not versions:
            raise FileNotFoundError("没有找到任何提示词文件")
        # 返回最后一个版本（假设版本号是递增的）
        return versions[-1]


# 全局加载器实例
_loader = PromptLoader()


def get_prompt(version: Optional[str] = None) -> str:
    """
    获取指定版本的提示词（便捷函数）
    
    Args:
        version: 提示词版本，如果为None则使用默认版本
    
    Returns:
        提示词文本
    """
    if version is None:
        version = _loader.get_default_version()
    return _loader.load_prompt(version)


def list_prompt_versions() -> List[str]:
    """
    列出所有可用的提示词版本（便捷函数）
    
    Returns:
        版本列表
    """
    return _loader.list_versions()
