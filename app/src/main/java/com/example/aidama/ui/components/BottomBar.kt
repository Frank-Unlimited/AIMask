package com.example.aidama.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aidama.R
import com.example.aidama.data.model.CategoryUiModel
import com.example.aidama.data.model.EditMode
import com.example.aidama.ui.theme.AppPanelDark
import com.example.aidama.ui.theme.AppPrimary
import com.example.aidama.ui.theme.AppSurfaceDark
import com.example.aidama.ui.theme.AppTextGray

@Composable
fun BottomBar(
    editMode: EditMode,
    categorySummary: List<CategoryUiModel>,
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
    summary: List<CategoryUiModel>,
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
        items(items = summary, key = { it.name }) { item ->
            val color = colorMap[item.name] ?: Color.Gray
            Surface(
                modifier = Modifier.clickable { onClick(item.name) },
                shape = RoundedCornerShape(20.dp),
                color = if (item.selected) color else color.copy(alpha = 0.2f),
                border = if (item.selected) null else BorderStroke(1.dp, color.copy(alpha = 0.5f))
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (item.selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp).padding(end = 4.dp))
                    Text("${item.name} (${item.count})", color = if (item.selected) Color.White else color, fontSize = 12.sp, fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun AiChatTriggerBar(onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp).background(AppSurfaceDark, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            Text(stringResource(id = R.string.ai_hint), color = AppTextGray, fontSize = 14.sp)
            Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, null, tint = AppTextGray); Spacer(Modifier.width(16.dp)); Icon(Icons.AutoMirrored.Filled.Send, null, tint = AppPrimary)
            }
        }
    }
}

@Composable
private fun BatchProcessBar(onConfirm: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(AppPanelDark).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(id = R.string.batch_confirm_question), color = Color.White, fontSize = 14.sp); Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = onConfirm, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)) { Text(stringResource(id = R.string.yes)) }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = {}, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))) { Text(stringResource(id = R.string.no)) }
        }
    }
}