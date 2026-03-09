package com.example.aidama.data.model

// 1. 发送给 Agent 的当前已打码状态
data class MaskedItemDomain(
    val category: String,
    val text: String
)

// 2. 用户的交互意图
data class UserFeedbackDomain(
    val clickedChar: String? = null,    // 用户点击的具体字符 (例如: "李")
    val contextWindow: String? = null,  // 完整的词语或句子上下文 (例如: "李经理")
    val actionType: String? = null,     // 操作类型: "add" (新增打码), "remove" (取消打码), "chat" (聊天框输入)
    val naturalLanguage: String? = null // 用户在聊天框输入的自然语言指令
)

// 3. Agent 返回的单条建议
data class AgentRecommendationDomain(
    val text: String,       // 建议操作的文本内容
    val action: String,     // 操作指令: "mask" (打码), "unmask" (取消打码)
    val category: String,   // 分类标签 (如: "业务隐私", "关联方")
    val reason: String,     // Agent 给出的解释理由 (用于展示在聊天框)
    val confidence: Float   // 置信度
)

// 4. Agent 返回的整体结果
data class AgentResultDomain(
    val recommendations: List<AgentRecommendationDomain>,
    val error: String? = null
)