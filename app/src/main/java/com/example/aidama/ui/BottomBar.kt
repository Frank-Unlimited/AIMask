package com.example.aidama.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BottomBar(
    editMode: EditMode,
    categorySummary: Map<String, Pair<Int, Boolean>>,
    typeColorMap: Map<String, Color>,
    onCategoryClick: (String) -> Unit,
    onBatchConfirm: () -> Unit,
    onOpenChat: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        when (editMode) {
            EditMode.AI -> AiChatTriggerBar(onOpenChat)
            EditMode.BATCH -> BatchProcessBar(onBatchConfirm)
            EditMode.TYPE_MATCH -> TypeMatchBar(categorySummary, typeColorMap, onCategoryClick)
        }
    }
}

@Composable
private fun TypeMatchBar(
    summary: Map<String, Pair<Int, Boolean>>,
    colorMap: Map<String, Color>,
    onClick: (String) -> Unit
) {
    if (summary.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        summary.forEach { (name, info) ->
            val color = colorMap[name] ?: Color.Gray
            item {
                Surface(
                    modifier = Modifier.clickable { onClick(name) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (info.second) color else color.copy(alpha = 0.2f),
                    border = if (info.second) null else BorderStroke(1.dp, color.copy(alpha = 0.5f))
                ) {
                    // 【修复点】：显式指定 verticalAlignment，避免参数位置混淆
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (info.second) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp).padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = "$name (${info.first})",
                            color = if (info.second) Color.White else color,
                            fontSize = 12.sp,
                            fontWeight = if (info.second) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiChatTriggerBar(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp).background(Color(0xFF2C2C2C), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("下达 AI 打码指令...", color = Color.Gray, fontSize = 14.sp)

            // 【修复点】：显式指定 verticalAlignment
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Mic, null, tint = Color.Gray)
                Spacer(Modifier.width(16.dp))
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color(0xFF4A90E2))
            }
        }
    }
}

@Composable
private fun BatchProcessBar(onConfirm: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("是否将当前打码方式运用到所有图片?", color = Color.White, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A90E2))
            ) { Text("是") }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = {},
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
            ) { Text("否") }
        }
    }
}