package com.example.aidama.ui
import androidx.compose.runtime.mutableStateListOf

data class ChatMessage(val isUser: Boolean, val text: String)

object ChatRepository {
    val messages = mutableStateListOf<ChatMessage>()
    init {
        if (messages.isEmpty()) {
            messages.add(ChatMessage(false, "你好，我是 AI 打码助手。你可以直接告诉我你想打码的内容。"))
        }
    }
    fun addMessage(isUser: Boolean, text: String) { messages.add(ChatMessage(isUser, text)) }
}