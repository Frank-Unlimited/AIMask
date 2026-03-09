package com.example.aidama.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.unit.IntSize
import com.example.aidama.api.AidamaApiService
import com.example.aidama.data.model.OcrRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 在线 OCR 服务实现 (对接真实的 Python 后端)
 */
class OnlineOcrRepositoryImpl(
    private val context: Context,
    private val apiService: AidamaApiService // 注入网络接口
) : OcrRepository {

    private val cache = mutableMapOf<Uri, Pair<List<OcrRect>, IntSize>>()

    override suspend fun loadOcrData(uri: Uri): Pair<List<OcrRect>, IntSize>? = withContext(Dispatchers.IO) {
        cache[uri]?.let { return@withContext it }

        try {
            // 1. 读取本地图片，获取原始宽高 (UI 画布需要)
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: return@withContext null
            inputStream.close()

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            val imageSize = IntSize(options.outWidth, options.outHeight)

            // 2. 构造 Multipart 网络请求
            val requestBody = bytes.toRequestBody("image/*".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("file", "image.jpg", requestBody)

            // 3. 调用真实后端 API
            val response = apiService.analyzeImageOcr(imagePart)

            // 4. 将后端的 DTO 映射为前端的 Domain 模型
            val domainRects = response.words_result.map { word ->
                // 后端返回的是 4 个坐标点，我们需要计算出外接矩形框 (AABB)
                val xs = word.box_points.map { it.x }
                val ys = word.box_points.map { it.y }
                val minX = xs.minOrNull() ?: 0f
                val maxX = xs.maxOrNull() ?: 0f
                val minY = ys.minOrNull() ?: 0f
                val maxY = ys.maxOrNull() ?: 0f

                OcrRect(
                    x = minX.toInt(),
                    y = minY.toInt(),
                    width = (maxX - minX).toInt(),
                    height = (maxY - minY).toInt(),
                    text = word.text,
                    // 这个特定后端接口目前没返回分类，我们可以交由下游 Agent 处理，或者赋默认值
                    sensitivity = "none",
                    type = "未分类"
                )
            }

            val resultPair = Pair(domainRects, imageSize)
            cache[uri] = resultPair
            return@withContext resultPair

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    override fun getCached(uri: Uri): Pair<List<OcrRect>, IntSize>? = cache[uri]
}