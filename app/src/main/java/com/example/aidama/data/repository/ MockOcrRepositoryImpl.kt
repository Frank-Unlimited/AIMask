package com.example.aidama.data.repository

import android.content.Context
import android.net.Uri
import androidx.compose.ui.unit.IntSize
import com.example.aidama.data.model.OcrRect
import com.example.aidama.utils.MosaicUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 比赛演示专用的本地 Mock 实现 (读取 Assets 下的 JSON)
 */
class MockOcrRepositoryImpl(
    private val context: Context
) : OcrRepository {

    private val cache = mutableMapOf<Uri, Pair<List<OcrRect>, IntSize>>()

    override suspend fun loadOcrData(uri: Uri): Pair<List<OcrRect>, IntSize>? = withContext(Dispatchers.IO) {
        cache[uri]?.let { return@withContext it }

        val key = MosaicUtils.getFileNameKey(context, uri)
        val json = MosaicUtils.loadJsonFromAssets(context, "${key}_ai.json") ?: return@withContext null
        val parsed = MosaicUtils.parseOcrJson(json)

        cache[uri] = parsed
        return@withContext parsed
    }

    override fun getCached(uri: Uri): Pair<List<OcrRect>, IntSize>? = cache[uri]
}