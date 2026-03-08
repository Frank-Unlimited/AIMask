package com.example.aidama.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aidama.R
import com.example.aidama.data.repository.ChatRepository
import com.example.aidama.data.repository.OcrRepository
import com.example.aidama.ui.components.*
import com.example.aidama.ui.viewmodel.MosaicUiEvent
import com.example.aidama.ui.viewmodel.MosaicViewModel
import com.example.aidama.ui.viewmodel.MosaicViewModelFactory

@Composable
fun MosaicScreen(
    vm: MosaicViewModel,
    context: android.content.Context = LocalContext.current
) {
    val state = vm.state
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val chatMessages by ChatRepository.messages.collectAsState()

    // 接收 ViewModel 发出的单次 Toast 事件
    LaunchedEffect(Unit) {
        vm.uiEvent.collect { event ->
            when (event) {
                is MosaicUiEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        vm.addImages(uris)
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF2C2C2C)) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                TopBar(onExport = { vm.startBatchExport() })

                Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { previewSize = it }) {
                    if (state.images.isEmpty()) {
                        EmptyStateView { imagePicker.launch("image/*") }
                    } else {
                        ImagePreview(
                            modifier = Modifier.fillMaxSize(),
                            imageUri = state.images.getOrNull(state.selectedIndex),
                            ocrRects = vm.currentOcrRects,
                            originalImageSize = vm.originalImageSize,
                            zoomLevel = state.zoomLevel,
                            panOffset = state.panOffset,
                            onPanChange = { vm.setPan(it, previewSize) },
                            selectedOcrIndices = vm.selectedOcrIndices,
                            isEffectView = state.isEffectView,
                            isAiProcessed = state.isAiProcessed,
                            isExporting = state.isExporting,
                            typeColorMap = vm.typeColorMap,
                            onOcrRectClick = { vm.handleOcrRectClick(it) }
                        )
                        Box(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 60.dp)) {
                            ImageThumbnails(state.images, state.selectedIndex, { vm.selectImage(it) }, { vm.removeImage(it) })
                        }
                    }
                    if (state.isOcrLoading) OcrLoadingOverlay(state.loadingMessage)
                }

                Controls({ imagePicker.launch("image/*") }, state.editMode, { vm.setEditMode(it) }, state.isEffectView, { vm.toggleEffectView(it) }, { vm.undo() }, { vm.redo() }, state.zoomLevel, { vm.setZoom(it, previewSize) }, { vm.handleAiAction() })

                BottomBar(state.editMode, vm.categorySummary, vm.typeColorMap, { vm.handleCategoryClick(it) }, { vm.handleBatchProcessing() }, { vm.setShowChat(true) })
            }

            Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)) {
                AnimatedVisibility(visible = state.showAnalysisNotice, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
                    Surface(color = Color(0xFF2C2C2C), shape = RoundedCornerShape(24.dp), shadowElevation = 6.dp) {
                        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoFixHigh, null, tint = Color(0xFF4A90E2), modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(stringResource(id = R.string.ai_analysis_notice), color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
                AnimatedVisibility(visible = state.showTypeChangeNotice, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
                    Surface(color = Color(0xFF3B4A6B), shape = RoundedCornerShape(24.dp), shadowElevation = 10.dp) {
                        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TipsAndUpdates, null, tint = Color.Yellow, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(state.typeChangeNotice, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            ChatOverlay(state.showChatOverlay, chatMessages, { vm.setShowChat(false) }, { vm.handleUserMessage(it) })

            if (state.showBatchRuleDialog) {
                AlertDialog(
                    onDismissRequest = { vm.dismissBatchDialog() }, icon = { Icon(Icons.AutoMirrored.Filled.Rule, null, tint = Color(0xFF4A90E2)) }, title = { Text(stringResource(id = R.string.generate_rules_title), fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("AI 已根据当前图片生成以下策略：", fontSize = 13.sp, color = Color.Gray); Spacer(Modifier.height(8.dp))
                            RuleItemView("1. 必打码", state.batchRuleMust, "高敏感隐私", Color(0xFFEF5350))
                            RuleItemView("2. 按需打码", state.batchRuleDemand, "保留选择空间", Color(0xFFFFA726))
                            RuleItemView("3. 自定义适配", state.batchRuleCustom, "业务创意描述", Color(0xFF42A5F5))
                            Spacer(Modifier.height(8.dp)); Text("是否按照这些内容打码？\n如果需修改请前往AI对话界面。", fontSize = 13.sp, color = Color.Gray)
                        }
                    },
                    confirmButton = { Button({ vm.confirmBatchApply() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A90E2))) { Text(stringResource(id = R.string.apply_rules)) } },
                    dismissButton = { TextButton({ vm.navigateToChatAndReport() }) { Text(stringResource(id = R.string.go_to_modify)) } },
                    containerColor = Color(0xFF2C2C2C), titleContentColor = Color.White, textContentColor = Color.LightGray
                )
            }
        }
    }
}

@Composable
private fun RuleItemView(title: String, items: List<String>, reason: String, color: Color) {
    if (items.isNotEmpty()) {
        Text(title, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp)
        Text("${items.joinToString("、")} ($reason)", fontSize = 13.sp, color = Color.White)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun EmptyStateView(onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(80.dp), Color.Gray.copy(alpha = 0.5f)); Spacer(Modifier.height(16.dp)); Text("请点击下方按钮上传图片", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
fun OcrLoadingOverlay(message: String) {
    val trans = rememberInfiniteTransition(label = "scan")
    val scan by trans.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse), label = "line")
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize()) { Box(Modifier.fillMaxWidth().height(4.dp).align(Alignment.TopCenter).offset(y = (scan * 500).dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF4A90E2), Color.Transparent)))) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = Color(0xFF4A90E2), strokeWidth = 4.dp); Spacer(Modifier.height(16.dp)); Text(message, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}