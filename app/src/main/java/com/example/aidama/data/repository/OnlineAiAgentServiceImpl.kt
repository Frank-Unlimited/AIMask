package com.example.aidama.data.repository

import com.example.aidama.api.AidamaApiService
import com.example.aidama.api.ApiAnalyzeRequest
import com.example.aidama.api.ApiMaskedItem
import com.example.aidama.api.ApiUserFeedback
import com.example.aidama.data.model.AgentRecommendationDomain
import com.example.aidama.data.model.AgentResultDomain
import com.example.aidama.data.model.MaskedItemDomain
import com.example.aidama.data.model.UserFeedbackDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 继承自抽象服务的子类：在线网络实现
 */
class OnlineAiAgentServiceImpl(
    private val apiService: AidamaApiService // 注入 Retrofit 接口
) : AiAgentService {

    override suspend fun analyzeMasking(
        ocrTexts: List<String>,
        currentMasked: List<MaskedItemDomain>,
        userFeedback: UserFeedbackDomain,
        sessionId: String?
    ): AgentResultDomain = withContext(Dispatchers.IO) {
        try {
            // 1. 将 Domain 模型转换为网络 DTO
            val request = ApiAnalyzeRequest(
                ocr_texts = ocrTexts,
                current_masked = currentMasked.map { ApiMaskedItem(it.category, it.text) },
                user_feedback = ApiUserFeedback(
                    clicked_char = userFeedback.clickedChar,
                    context_window = userFeedback.contextWindow,
                    action_type = userFeedback.actionType,
                    natural_language = userFeedback.naturalLanguage
                ),
                session_id = sessionId
            )

            // 2. 发起真实的 HTTP 请求 (微服务通信)
            val response = apiService.analyzeMasking(request)

            // 3. 处理错误
            if (response.error != null) {
                return@withContext AgentResultDomain(emptyList(), response.error)
            }

            // 4. 将网络 DTO 转换回 Domain 模型
            val domainRecommendations = response.mask_recommendations?.map { dto ->
                AgentRecommendationDomain(
                    text = dto.text,
                    action = dto.action,
                    category = dto.category,
                    reason = dto.reason,
                    confidence = dto.confidence
                )
            } ?: emptyList()

            return@withContext AgentResultDomain(domainRecommendations)

        } catch (e: Exception) {
            e.printStackTrace()
            // 网络异常处理
            return@withContext AgentResultDomain(emptyList(), "网络连接失败: ${e.message}")
        }
    }
}