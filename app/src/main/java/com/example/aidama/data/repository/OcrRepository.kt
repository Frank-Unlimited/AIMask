package com.example.aidama.data.repository

import android.net.Uri
import androidx.compose.ui.unit.IntSize
import com.example.aidama.data.model.OcrRect

/**
 * OCR 数据仓库抽象接口
 * 【架构设计意图】：
 * 隔离 OCR 识别的具体实现。
 * UI 层只需调用 loadOcrData，无需关心底层是：
 * 1. MockOcrRepositoryImpl (比赛演示：读取本地 JSON)
 * 2. OnlineOcrRepositoryImpl (云端部署：调用 Python 后端微服务)
 * 3. OnDeviceOcrRepositoryImpl (端侧部署：调用 PaddleOCR / ML Kit 本地模型)
 */
interface OcrRepository {
    suspend fun loadOcrData(uri: Uri): Pair<List<OcrRect>, IntSize>?
    fun getCached(uri: Uri): Pair<List<OcrRect>, IntSize>?
}