"""
统一Agent核心逻辑
处理三种任务模式：initial, iterative, batch
"""
import json
from typing import List, Dict, Optional
from llm_client import LLMClient
from prompts import PromptManager


class UnifiedAgent:
    def __init__(self, config):
        self.config = config
        self.llm_client = LLMClient(config)
        self.prompt_manager = PromptManager()
    
    def analyze(
        self,
        task_mode: str,
        ocr_texts: List[str],
        current_masked: Optional[List[str]] = None,
        user_feedback: Optional[Dict] = None,
        batch_rules: Optional[Dict] = None,
        context: Optional[Dict] = None
    ) -> Dict:
        """
        统一分析入口
        """
        # 构建System Prompt
        system_prompt = self.prompt_manager.get_system_prompt()
        
        # 构建User Prompt（根据task_mode不同）
        user_prompt = self._build_user_prompt(
            task_mode=task_mode,
            ocr_texts=ocr_texts,
            current_masked=current_masked,
            user_feedback=user_feedback,
            batch_rules=batch_rules,
            context=context
        )
        
        # 调用LLM
        response = self.llm_client.call(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            temperature=0.3  # 低温度保证稳定性
        )
        
        # 解析响应
        result = self._parse_response(response)
        
        return result
    
    def _build_user_prompt(
        self,
        task_mode: str,
        ocr_texts: List[str],
        current_masked: Optional[List[str]],
        user_feedback: Optional[Dict],
        batch_rules: Optional[Dict],
        context: Optional[Dict]
    ) -> str:
        """
        构建User Prompt
        """
        if task_mode == "initial":
            return self.prompt_manager.build_initial_prompt(ocr_texts, context)
        
        elif task_mode == "iterative":
            return self.prompt_manager.build_iterative_prompt(
                ocr_texts=ocr_texts,
                current_masked=current_masked,
                user_feedback=user_feedback,
                context=context
            )
        
        elif task_mode == "batch":
            return self.prompt_manager.build_batch_prompt(
                ocr_texts=ocr_texts,
                batch_rules=batch_rules,
                context=context
            )
        
        else:
            raise ValueError(f"Unknown task_mode: {task_mode}")
    
    def _parse_response(self, response: str) -> Dict:
        """
        解析LLM响应
        """
        try:
            # 尝试直接解析JSON
            result = json.loads(response)
            
            # 验证必需字段
            required_fields = ["mask_recommendations", "privacy_categories", "summary"]
            for field in required_fields:
                if field not in result:
                    raise ValueError(f"Missing required field: {field}")
            
            # 确保learned_rules存在
            if "learned_rules" not in result:
                result["learned_rules"] = []
            
            # 设置status
            result["status"] = "success"
            
            return result
            
        except json.JSONDecodeError as e:
            # JSON解析失败，尝试提取JSON部分
            print(f"JSON decode error: {e}")
            print(f"Response: {response}")
            
            # 尝试从markdown代码块中提取
            if "```json" in response:
                start = response.find("```json") + 7
                end = response.find("```", start)
                json_str = response[start:end].strip()
                try:
                    result = json.loads(json_str)
                    result["status"] = "success"
                    if "learned_rules" not in result:
                        result["learned_rules"] = []
                    return result
                except:
                    pass
            
            # 如果还是失败，返回错误
            return {
                "status": "error",
                "mask_recommendations": [],
                "privacy_categories": {
                    "姓名": [], "电话": [], "邮箱": [],
                    "公司名称": [], "地址": [], "银行卡号": [], "身份证号": []
                },
                "summary": f"LLM响应解析失败: {str(e)}",
                "learned_rules": [],
                "error": str(e),
                "raw_response": response
            }
    
    def generate_batch_rules(self, interaction_history: List[Dict]) -> Dict:
        """
        从交互历史生成批量规则
        """
        category_actions = {}
        
        for interaction in interaction_history:
            category = interaction.get("category")
            action = interaction.get("action_type")
            
            if not category or not action:
                continue
            
            if category not in category_actions:
                category_actions[category] = []
            category_actions[category].append(action)
        
        must_mask = []
        skip = []
        learned_patterns = []
        
        for category, actions in category_actions.items():
            add_count = actions.count("add")
            remove_count = actions.count("remove")
            
            if add_count > remove_count:
                must_mask.append(category)
                learned_patterns.append(f"{category}需要打码")
            else:
                skip.append(category)
                learned_patterns.append(f"{category}不需要打码")
        
        return {
            "must_mask": must_mask,
            "skip": skip,
            "learned_patterns": learned_patterns
        }
