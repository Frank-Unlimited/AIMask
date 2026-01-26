package com.example.aidama.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatOverlay(
    isVisible: Boolean,
    messages: List<ChatMessage>,
    onClose: () -> Unit,
    onSendScriptedMessage: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // 自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onClose)
        ) {
            // 【核心修复 1】将 Padding 移到 Surface 层
            // 并使用 wrapContentHeight 确保对话框高度随内容收缩
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding() // 整个对话框随键盘上移
                    .navigationBarsPadding() // 避开系统导航条
                    .pointerInput(Unit) { detectTapGestures { /* 拦截点击，防止穿透到背景 */ } },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Color(0xFF1E1E1E)
            ) {
                // 【核心修复 2】Column 不再使用 fillMaxSize，而是由内容撑起，高度上限由 heightIn 限制
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp) // 限制最大高度，防止顶到状态栏
                ) {
                    // 1. 顶部手柄
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.Gray.copy(alpha = 0.4f))
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(24.dp)
                        )
                    }

                    Divider(color = Color.Gray.copy(alpha = 0.1f))

                    // 2. 消息列表
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f, fill = false) // 允许列表根据内容收缩
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(messages) { msg ->
                            ChatBubble(message = msg)
                        }
                    }

                    // 3. 底部输入区
                    ChatInputArea(
                        text = inputText,
                        onTextChanged = { inputText = it },
                        isRecording = isRecording,
                        onVoiceStart = { isRecording = true },
                        onVoiceEnd = {
                            isRecording = false
                            inputText = "只打码手机号和身份证号"
                        },
                        onSend = {
                            if (inputText.isNotBlank()) {
                                onSendScriptedMessage(inputText)
                                inputText = ""
                                keyboardController?.hide()
                            }
                        }
                    )
                }
            }

            // 录音提示提示 (在外部 Box 层，不受 Surface 位移影响)
            if (isRecording) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Mic, null, tint = Color(0xFF4A90E2), modifier = Modifier.size(64.dp))
                        Text("正在聆听指令...", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputArea(
    text: String,
    onTextChanged: (String) -> Unit,
    isRecording: Boolean,
    onVoiceStart: () -> Unit,
    onVoiceEnd: () -> Unit,
    onSend: () -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2C2C2C))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {},
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onPress = { onVoiceStart(); tryAwaitRelease(); onVoiceEnd() },
                    onTap = { Toast.makeText(context, "请长按录音", Toast.LENGTH_SHORT).show() }
                )
            }
        ) {
            Icon(Icons.Default.Mic, null, tint = if (isRecording) Color(0xFF4A90E2) else Color.Gray)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(20.dp))
                .clickable { focusRequester.requestFocus() }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color(0xFF4A90E2)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text("输入消息...", color = Color.Gray, fontSize = 15.sp)
                    }
                    innerTextField()
                }
            )
        }

        IconButton(onClick = onSend) {
            Icon(Icons.Default.Send, null, tint = if (text.isNotEmpty()) Color(0xFF4A90E2) else Color.Gray)
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF4A90E2)), Alignment.Center) {
                Icon(Icons.Default.SmartToy, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isUser) Color(0xFF4A90E2) else Color(0xFF333333))
                .padding(12.dp)
        ) {
            Text(message.text, color = Color.White, fontSize = 14.sp)
        }
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color.Gray), Alignment.Center) {
                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}