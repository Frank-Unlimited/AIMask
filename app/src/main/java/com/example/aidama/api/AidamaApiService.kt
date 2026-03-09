package com.example.aidama.api

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

// ==========================================
// 1. OCR 识别服务 DTO (严格匹配后端 JSON)
// ==========================================
data class ApiOcrPoint(val x: Float, val y: Float)

data class ApiOcrWordResult(
    val text: String,
    val confidence: Float,
    val box_points: List<ApiOcrPoint> // 后端返回的是 4 个点的多边形
)

data class ApiOcrResponse(
    val full_text: String,
    val words_result: List<ApiOcrWordResult>
)

// ==========================================
// 2. AI Agent 决策服务 DTO (之前的)
// ==========================================
data class ApiMaskedItem(val category: String, val text: String)
data class ApiUserFeedback(val clicked_char: String?, val context_window: String?, val action_type: String?, val natural_language: String?)
data class ApiAnalyzeRequest(val ocr_texts: List<String>, val current_masked: List<ApiMaskedItem>, val user_feedback: ApiUserFeedback, val session_id: String?)
data class ApiRecommendation(val text: String, val action: String, val category: String, val reason: String, val confidence: Float)
data class ApiAnalyzeResponse(val mask_recommendations: List<ApiRecommendation>?, val error: String?)

// ==========================================
// 3. 统一的网络接口契约
// ==========================================
interface AidamaApiService {

    /**
     * 微服务 1：OCR 图像文本识别接口
     * 采用 Multipart 上传图片文件
     */
    @Multipart
    @POST("/api/v1/ocr")
    suspend fun analyzeImageOcr(
        @Part image: MultipartBody.Part
    ): ApiOcrResponse

    /**
     * 微服务 2：AI Agent 意图决策接口
     */
    @POST("/analyze")
    suspend fun analyzeMasking(
        @Body request: ApiAnalyzeRequest
    ): ApiAnalyzeResponse
}