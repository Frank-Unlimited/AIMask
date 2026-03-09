package com.example.aidama.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aidama.data.model.ChatMessage

@Composable
fun ChatOverlay(isVisible: Boolean, messages: List<ChatMessage>, onClose: () -> Unit, onSendScriptedMessage: (String) -> Unit) {
    var inputText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    AnimatedVisibility(isVisible, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { onClose() }) {
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().navigationBarsPadding().pointerInput(Unit) { detectTapGestures {} }, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = Color(0xFF1E1E1E)) {
                Column(Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                    Box(Modifier.fillMaxWidth().height(40.dp).clickable { onClose() }, Alignment.Center) {
                        Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.Gray.copy(alpha = 0.4f)))
                    }
                    Divider(color = Color.White.copy(alpha = 0.05f))
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        messages.forEach { msg -> item { ChatBubble(msg) } }
                    }
                    ChatInputArea(inputText, { inputText = it }, isRecording, { isRecording = true }, { isRecording = false; inputText = "输入邮件内容：尊敬的启迪科技团队：大家好！韩昊辰和张思成已经完成了初步设计……日期：2025年12月9日" }) { if (inputText.isNotBlank()) { onSendScriptedMessage(inputText); inputText = "" } }
                }
            }
        }
    }
}

@Composable
private fun ChatInputArea(text: String, onTextChange: (String) -> Unit, recording: Boolean, onRecStart: () -> Unit, onRecEnd: () -> Unit, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF2C2C2C)).padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {}, Modifier.pointerInput(Unit) { detectTapGestures(onPress = { onRecStart(); tryAwaitRelease(); onRecEnd() }) }) { Icon(Icons.Default.Mic, null, tint = if (recording) Color(0xFF4A90E2) else Color.Gray) }
        Box(Modifier.weight(1f).height(42.dp).background(Color(0xFF1A1A1A), RoundedCornerShape(21.dp)).padding(horizontal = 16.dp), Alignment.CenterStart) {
            BasicTextField(
                value = text, onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color(0xFF4A90E2)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                decorationBox = { innerTextField -> if (text.isEmpty()) Text("输入指令...", color = Color.Gray, fontSize = 15.sp); innerTextField() }
            )
        }
        IconButton(onClick = onSend, enabled = text.isNotBlank()) { Icon(Icons.Default.Send, null, tint = if (text.isNotBlank()) Color(0xFF4A90E2) else Color.Gray) }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start) {
        if (!msg.isUser) { Surface(Modifier.size(32.dp), CircleShape, Color(0xFF4A90E2)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.SmartToy, null, tint = Color.White, modifier = Modifier.size(18.dp)) } }; Spacer(Modifier.width(8.dp)) }
        Surface(Modifier.widthIn(max = 280.dp), shape = RoundedCornerShape(12.dp), color = if (msg.isUser) Color(0xFF4A90E2) else Color(0xFF333333)) { Text(msg.text, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(12.dp)) }
        if (msg.isUser) { Spacer(Modifier.width(8.dp)); Surface(Modifier.size(32.dp), CircleShape, Color.Gray) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(18.dp)) } } }
    }
}