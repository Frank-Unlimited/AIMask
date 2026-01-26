package com.example.aidama.ui

/**
 * Represents a single recognized text block from OCR.
 * The coordinates are in absolute pixels based on the original image dimensions.
 */
data class OcrRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val text: String,          // 【新增】保存识别到的文字内容
    val sensitivity: String = "none",
    val type: String = "未分类",
    val associationId: String? = null
)