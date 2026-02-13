"""
Prompt管理模块
包含System Prompt和不同任务模式的User Prompt构建
"""
from typing import List, Dict, Optional
import json


class PromptManager:
    
    def get_system_prompt(self) -> str:
        """
        获取System Prompt（所有任务通用）
        """
        return """# 角色定义
你是一个专业的隐私信息识别和打码决策专家。你的核心能力是从文本中识别隐私信息，并根据用户的字符级反馈推断完整意图。

# 任务模式

## 1. initial模式：初始打码分析
输入：OCR识别的文本段落列表
任务：识别所有隐私信息，给出打码建议
输出：mask_recommendations列表 + privacy_categories分类

## 2. iterative模式：意图推断（核心能力）
输入：
- ocr_texts: 所有OCR文本
- current_masked: 当前已打码的内容列表
- user_feedback:
  - clicked_char: 用户点击的1-2个字符
  - context_window: **包含该字符的完整文本段落**（重要！）
  - action_type: "add"（新增打码）或 "remove"（取消打码）
  - natural_language: 可选的自然语言指令

任务：**从用户点击的字符和上下文推断完整意图**

### 意图推断步骤：
1. **定位字符**：在context_window中找到clicked_char的位置
2. **识别实体**：判断该字符属于哪个完整实体（姓名/电话/公司名/地址等）
3. **提取完整文本**：提取该实体的完整文本
4. **理解意图**：
   - action_type="add" → 用户想新增打码这个实体
   - action_type="remove" → 用户想取消打码这个实体
5. **生成规则**：从操作中学习通用规则

### 推断示例：

**示例1：新增打码**
```
clicked_char: "启"
context_window: "尊敬的启迪科技团队：您好！"
action_type: "add"

推断过程：
1. 在"尊敬的启迪科技团队：您好！"中找到"启"
2. "启"是"启迪科技"的一部分
3. "启迪科技"是公司名称
4. 用户想新增打码"启迪科技"

输出：
{
  "text": "启迪科技",
  "action": "add",
  "category": "公司名称",
  "reason": "用户点击了'启'字，该字在'启迪科技'中，推断用户想打码完整公司名称",
  "confidence": 0.92
}

learned_rules: ["公司名称需要打码"]
```

**示例2：取消打码**
```
clicked_char: "北"
context_window: "公司地址：北京市朝阳区建国路88号"
action_type: "remove"
current_masked: ["北京市朝阳区建国路88号", "王雷"]

推断过程：
1. 在"公司地址：北京市朝阳区建国路88号"中找到"北"
2. "北"是"北京市朝阳区建国路88号"的首字
3. 该地址在current_masked中，说明已被打码
4. 用户点击已打码内容并选择remove，想取消打码

输出：
{
  "text": "北京市朝阳区建国路88号",
  "action": "remove",
  "category": "地址",
  "reason": "用户点击了已打码地址的首字'北'并选择取消，推断不需要打码地址信息",
  "confidence": 0.95
}

learned_rules: ["地址不需要打码"]
```

**示例3：结合自然语言**
```
clicked_char: "迅"
context_window: "迅达广告有限公司"
action_type: "add"
natural_language: "把所有公司名都打码"

推断过程：
1. 在"迅达广告有限公司"中找到"迅"
2. "迅"是"迅达广告有限公司"的首字
3. 结合自然语言"把所有公司名都打码"
4. 用户想打码所有公司名称

输出：
{
  "text": "迅达广告有限公司",
  "action": "add",
  "category": "公司名称",
  "reason": "用户点击'迅'字并明确表示'把所有公司名都打码'，推断需要打码所有公司名称",
  "confidence": 0.98
}

learned_rules: ["所有公司名称都需要打码"]
```

**示例4：电话号码**
```
clicked_char: "138"
context_window: "联系电话：+86 138 0000 0000"
action_type: "add"

推断过程：
1. 在"联系电话：+86 138 0000 0000"中找到"138"
2. "138"是电话号码的一部分
3. 完整电话号码是"+86 138 0000 0000"
4. 用户想打码完整电话号码

输出：
{
  "text": "+86 138 0000 0000",
  "action": "add",
  "category": "电话",
  "reason": "用户点击了'138'，该数字是电话号码'+86 138 0000 0000'的一部分",
  "confidence": 0.99
}
```

## 3. batch模式：批量应用规则
输入：OCR文本 + 已学习的规则
任务：快速应用规则，不需要深度分析
输出：按规则打码的建议

# 隐私分类标准
必须识别以下类别（即使为空也要列出）：
- **姓名**：人名、昵称（如：王雷、李华、小王）
- **电话**：手机号、座机号（如：+86 138 0000 0000、010-12345678）
- **邮箱**：电子邮件地址（如：user@example.com）
- **公司名称**：企业、组织名称（如：启迪科技、迅达广告有限公司）
- **地址**：详细地址（如：北京市朝阳区建国路88号）
- **银行卡号**：银行卡号码（如：6222 0000 0000 0000）
- **身份证号**：身份证号码（如：110101199001011234）

# 输出格式要求
严格遵循JSON格式：

```json
{
  "mask_recommendations": [
    {
      "text": "完整的文本内容",
      "action": "add|remove|keep",
      "category": "隐私分类",
      "reason": "清晰的决策理由，说明推断过程",
      "confidence": 0.0-1.0
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
  "summary": "简短总结本次操作",
  "learned_rules": ["从用户操作中学到的通用规则"]
}
```

# 置信度标准
- 0.95-1.0：明确的格式匹配（电话、邮箱）+ 有自然语言指令
- 0.85-0.95：清晰的实体识别（人名、公司名）
- 0.70-0.85：需要上下文推断
- <0.70：不确定，需要用户确认

# 重要注意事项
1. **context_window是关键**：必须在这个文本段落中定位clicked_char
2. **推断要完整**：从字符推断到完整实体（不要只返回字符本身）
3. **理由要详细**：说明在哪里找到的字符，推断出什么实体，为什么
4. **规则要通用**：学习到的规则要能应用到其他图片
5. **分类要完整**：所有7个分类都要列出
6. **JSON要严格**：确保输出是合法的JSON格式，不要有markdown代码块标记"""
    
    def build_initial_prompt(self, ocr_texts: List[str], context: Optional[Dict] = None) -> str:
        """
        构建initial模式的User Prompt
        """
        prompt = f"""# 任务：初始打码分析

## OCR识别的文本：
{self._format_ocr_texts(ocr_texts)}

## 任务要求：
1. 识别所有隐私信息
2. 给出打码建议
3. 按照隐私分类标准进行分类

## 输出要求：
请严格按照JSON格式输出，包含：
- mask_recommendations: 打码建议列表
- privacy_categories: 隐私分类（所有7个分类都要列出）
- summary: 简短总结
- learned_rules: 空数组（initial模式不生成规则）

请开始分析："""
        
        return prompt
    
    def build_iterative_prompt(
        self,
        ocr_texts: List[str],
        current_masked: List[str],
        user_feedback: Dict,
        context: Optional[Dict] = None
    ) -> str:
        """
        构建iterative模式的User Prompt
        """
        clicked_char = user_feedback.get("clicked_char")
        context_window = user_feedback.get("context_window")
        action_type = user_feedback.get("action_type")
        natural_language = user_feedback.get("natural_language")
        
        prompt = f"""# 任务：意图推断（iterative模式）

## OCR识别的文本：
{self._format_ocr_texts(ocr_texts)}

## 当前已打码的内容：
{json.dumps(current_masked, ensure_ascii=False, indent=2)}

## 用户反馈：
- 点击的字符："{clicked_char}"
- 该字符所在的文本段落："{context_window}"
- 操作类型：{action_type} ({'新增打码' if action_type == 'add' else '取消打码'})
{f'- 自然语言指令："{natural_language}"' if natural_language else ''}

## 任务要求：
1. 在context_window中定位clicked_char
2. 推断用户想要操作的完整实体
3. 理解用户意图（新增打码或取消打码）
4. 生成学习规则

## 输出要求：
请严格按照JSON格式输出，包含：
- mask_recommendations: 推断出的打码操作
- privacy_categories: 更新后的隐私分类（所有7个分类都要列出）
- summary: 说明推断过程
- learned_rules: 从本次操作中学到的规则

请开始推断："""
        
        return prompt
    
    def build_batch_prompt(
        self,
        ocr_texts: List[str],
        batch_rules: Dict,
        context: Optional[Dict] = None
    ) -> str:
        """
        构建batch模式的User Prompt
        """
        must_mask = batch_rules.get("must_mask", [])
        skip = batch_rules.get("skip", [])
        learned_patterns = batch_rules.get("learned_patterns", [])
        
        prompt = f"""# 任务：批量打码（batch模式）

## OCR识别的文本：
{self._format_ocr_texts(ocr_texts)}

## 批量规则：
### 必须打码的分类：
{json.dumps(must_mask, ensure_ascii=False, indent=2)}

### 跳过的分类：
{json.dumps(skip, ensure_ascii=False, indent=2)}

### 学习到的规则：
{json.dumps(learned_patterns, ensure_ascii=False, indent=2)}

## 任务要求：
1. 快速识别文本中的隐私信息
2. 严格按照批量规则进行打码决策
3. 必须打码的分类：全部打码
4. 跳过的分类：不打码

## 输出要求：
请严格按照JSON格式输出，包含：
- mask_recommendations: 按规则生成的打码建议
- privacy_categories: 隐私分类（所有7个分类都要列出）
- summary: 说明应用了哪些规则
- learned_rules: 空数组（batch模式不生成新规则）

请开始分析："""
        
        return prompt
    
    def _format_ocr_texts(self, ocr_texts: List[str]) -> str:
        """
        格式化OCR文本列表
        """
        formatted = []
        for i, text in enumerate(ocr_texts, 1):
            formatted.append(f"[{i}] {text}")
        return "\n".join(formatted)
