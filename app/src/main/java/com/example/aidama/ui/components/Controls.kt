package com.example.aidama.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aidama.data.model.EditMode

@Composable
fun Controls(onAddImage: () -> Unit, editMode: EditMode, onEditModeChange: (EditMode) -> Unit, isEffectView: Boolean, onViewChange: (Boolean) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, zoomLevel: Float, onZoomChange: (Float) -> Unit, onAiOneClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAddImage) {
                Box {
                    Icon(Icons.Default.Image, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    Box(modifier = Modifier.align(Alignment.BottomEnd).offset(2.dp, 2.dp).size(16.dp).background(Color.White, CircleShape).border(0.5.dp, Color(0xFF4A90E2), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = Color(0xFF4A90E2), modifier = Modifier.size(12.dp)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onUndo) { Text("撤销", color = Color.Gray) }
                Row(modifier = Modifier.background(Color(0x80424242), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onZoomChange(zoomLevel - 0.1f) }) { Text("-", color = Color.White) }
                    Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color.Gray))
                    IconButton(onClick = { onZoomChange(zoomLevel + 0.1f) }) { Text("+", color = Color.White) }
                }
                TextButton(onClick = onRedo) { Text("重做", color = Color.Gray) }
            }
            IconButton(onClick = onAiOneClick) { Icon(Icons.Default.AutoFixHigh, null, tint = Color(0xFF4A90E2), modifier = Modifier.size(28.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF2C2C2C)).padding(4.dp)) {
                Button(onClick = { onViewChange(true) }, colors = ButtonDefaults.buttonColors(containerColor = if (isEffectView) Color(0xFF4A90E2) else Color.Transparent), shape = RoundedCornerShape(6.dp)) { Text("效果图", color = if (isEffectView) Color.White else Color.Gray) }
                Button(onClick = { onViewChange(false) }, colors = ButtonDefaults.buttonColors(containerColor = if (!isEffectView) Color(0xFF4A90E2) else Color.Transparent), shape = RoundedCornerShape(6.dp)) { Text("原图", color = if (!isEffectView) Color.White else Color.Gray) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalArrangement = Arrangement.SpaceAround) {
            TabItem("AI 打码", editMode == EditMode.AI) { onEditModeChange(EditMode.AI) }
            TabItem("类型匹配", editMode == EditMode.TYPE_MATCH) { onEditModeChange(EditMode.TYPE_MATCH) }
            TabItem("批量处理", editMode == EditMode.BATCH) { onEditModeChange(EditMode.BATCH) }
        }
    }
}

@Composable
fun TabItem(text: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Color(0xFF4A90E2) else Color.Gray
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = color, fontSize = 14.sp)
            if (selected) { Spacer(Modifier.height(4.dp)); Box(Modifier.width(20.dp).height(2.dp).background(color, RoundedCornerShape(1.dp))) }
        }
    }
}