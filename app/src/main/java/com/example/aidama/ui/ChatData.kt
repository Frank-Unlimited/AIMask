package com.example.aidama.ui

import androidx.compose.runtime.mutableStateListOf

// 1. 数据类定义在这里
data class ChatMessage(
    val isUser: Boolean,
    val text: String
)

// 2. 这里的 object 相当于一个全局仓库，APP 运行期间它一直存在
object ChatRepository {
    // 使用 mutableStateListOf 让 Compose 能监听到变化并自动刷新 UI
    val messages = mutableStateListOf<ChatMessage>()

    // 初始化代码块：如果列表为空，塞入第一条欢迎语
    init {
        if (messages.isEmpty()) {
            messages.add(ChatMessage(false, "你好，我是 AI 打码助手。你可以直接告诉我你想打码的内容，比如“遮住所有人脸”。"))
        }
    }

    // 辅助方法：添加消息
    fun addMessage(isUser: Boolean, text: String) {
        messages.add(ChatMessage(isUser, text))
    }

    // 如果需要清空记录（可选）
    fun clear() {
        messages.clear()
        // 重新添加欢迎语
        messages.add(ChatMessage(false, "你好，我是 AI 打码助手。你可以直接告诉我你想打码的内容，比如“遮住所有人脸”。"))
    }
}