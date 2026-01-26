package com.example.aidama.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BottomBar(
    editMode: EditMode,
    categorySummary: Map<String, Pair<Int, Boolean>>,
    onCategoryClick: (String) -> Unit,
    onBatchConfirm: () -> Unit,
    onOpenChat: () -> Unit // 【新增】点击输入框的回调，用于打开悬浮聊天窗
) {
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        when (editMode) {
            EditMode.AI -> AiChatTriggerBar(onOpenChat)
            EditMode.BATCH -> BatchProcessBar(onBatchConfirm)
            EditMode.TYPE_MATCH -> TypeMatchBar(categorySummary, onCategoryClick)
            else -> {}
        }
    }
}

/**
 * AI 模式底栏：
 * 这是一个"伪装"的输入栏，看起来像输入框，但实际上点击任何位置都会触发 onOpenChat。
 * 真正的输入交互在 ChatOverlay 中进行。
 */
@Composable
private fun AiChatTriggerBar(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(56.dp) // 增加一点高度方便点击
            .clickable(onClick = onClick), // 点击整个区域触发
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 模拟输入框外观
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 4.dp)
                .background(Color(0xFF2C2C2C), RoundedCornerShape(12.dp))
                .border(1.dp, Color.Transparent, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "下达 AI 打码指令...",
                color = Color.Gray,
                fontSize = 14.sp
            )

            // 模拟右侧图标
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Mic, null, tint = Color.Gray)
                Spacer(modifier = Modifier.width(16.dp))
                Icon(Icons.Default.Send, null, tint = Color(0xFF4A90E2))
            }
        }
    }
}

/**
 * 批量处理模式底栏
 */
@Composable
private fun BatchProcessBar(onConfirm: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("是否将当前打码方式运用到所有图片?", color = Color.White, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A90E2))
            ) {
                Text("是")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = { }, // 演示中"否"通常不做操作或重置，此处留空即可
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
            ) {
                Text("否")
            }
        }
    }
}

/**
 * 类型匹配模式底栏 (保持不变)
 */
@Composable
private fun TypeMatchBar(
    categorySummary: Map<String, Pair<Int, Boolean>>,
    onCategoryClick: (String) -> Unit
) {
    if (categorySummary.isEmpty()) return

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        categorySummary.forEach { (name, info) ->
            val count = info.first
            val isAllSelected = info.second

            item(key = name) {
                Surface(
                    modifier = Modifier.clickable { onCategoryClick(name) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isAllSelected) Color(0xFF3B4A6B) else Color(0xFF4D5E85),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isAllSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp).padding(end = 4.dp)
                            )
                        }

                        Text(
                            text = "$name ($count)",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}