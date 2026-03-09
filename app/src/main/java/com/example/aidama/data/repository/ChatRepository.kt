package com.example.aidama.data.repository

import com.example.aidama.data.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// 单例模式的数据仓库，模拟数据库/网络获取的聊天记录
object ChatRepository {
    private val _messages = MutableStateFlow(
        listOf(ChatMessage(false, "你好，我是慧码 AI 助手。你可以直接告诉我你想打码的内容。"))
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun addMessage(isUser: Boolean, text: String) {
        val currentList = _messages.value.toMutableList()
        currentList.add(ChatMessage(isUser, text))
        _messages.value = currentList
    }
}