package com.example.aidama.ui
data class OcrRect(
    val x: Int, val y: Int, val width: Int, val height: Int,
    val text: String, val sensitivity: String = "none",
    val type: String = "未分类", val associationId: String? = null
)