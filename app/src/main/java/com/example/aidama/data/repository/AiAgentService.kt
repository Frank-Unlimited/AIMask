package com.example.aidama.data.repository

import com.example.aidama.data.model.AgentResultDomain
import com.example.aidama.data.model.MaskedItemDomain
import com.example.aidama.data.model.UserFeedbackDomain

/**
 * AI 智能体抽象服务接口
 * 【架构设计意图】：
 * 隔离底层实现。目前使用 OnlineAiAgentServiceImpl 调用后端微服务，
 * 未来可无缝替换为 OnDeviceAiAgentServiceImpl (端侧本地化大模型部署)。
 */
interface AiAgentService {
    suspend fun analyzeMasking(
        ocrTexts: List<String>,
        currentMasked: List<MaskedItemDomain>,
        userFeedback: UserFeedbackDomain,
        sessionId: String? = null
    ): AgentResultDomain
}