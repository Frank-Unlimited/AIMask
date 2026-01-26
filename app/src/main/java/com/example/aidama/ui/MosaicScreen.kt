package com.example.aidama.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException

// ----------------------------------------------------------------------------------
// 工具函数
// ----------------------------------------------------------------------------------

fun getFileNameKey(context: Context, uri: Uri): String {
    // 优先尝试从 ContentResolver 获取文件名，用于区分 primary1 和 primary2
    var name = "primary1" // 默认为 primary1
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val fullName = cursor.getString(nameIndex) ?: ""
                    // 简单的包含判断
                    if (fullName.contains("34") || fullName.contains("primary2", ignoreCase = true)) {
                        name = "primary2"
                    } else if (fullName.contains("33") || fullName.contains("primary1", ignoreCase = true)) {
                        name = "primary1"
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
    return name
}

fun loadJsonFromAssets(context: Context, fileName: String): String? {
    return try {
        context.assets.open(fileName).bufferedReader().use { it.readText() }
    } catch (ex: IOException) { null }
}

fun parseOcrJson(jsonString: String): Pair<List<OcrRect>, IntSize> {
    val jsonObject = JSONObject(jsonString)
    val imageMetadata = jsonObject.getJSONObject("image_metadata").getJSONObject("dimensions")
    val originalSize = IntSize(width = imageMetadata.getInt("width"), height = imageMetadata.getInt("height"))

    val rects = mutableListOf<OcrRect>()
    val textRegions = jsonObject.getJSONArray("text_regions")
    for (i in 0 until textRegions.length()) {
        val region = textRegions.getJSONObject(i)
        val textStr = region.optString("text", "").trim()
        if (textStr.isEmpty()) continue

        val level = region.optString("level", "none")
        val typeName = region.optString("type", "未分类")
        val assocId = if (region.isNull("association_id")) null else region.optString("association_id", null)

        val vertices = region.getJSONObject("bounding_box").getJSONArray("vertices")

        rects.add(
            OcrRect(
                x = vertices.getJSONObject(0).getInt("x"),
                y = vertices.getJSONObject(0).getInt("y"),
                width = vertices.getJSONObject(1).getInt("x") - vertices.getJSONObject(0).getInt("x"),
                height = vertices.getJSONObject(2).getInt("y") - vertices.getJSONObject(0).getInt("y"),
                text = textStr,
                sensitivity = level,
                type = typeName,
                associationId = assocId
            )
        )
    }
    return Pair(rects, originalSize)
}

fun coercePanOffset(offset: Offset, zoom: Float, size: IntSize): Offset {
    if (zoom <= 1f) return Offset.Zero
    val maxPanX = (size.width * (zoom - 1)) / 2f
    val maxPanY = (size.height * (zoom - 1)) / 2f
    return Offset(x = offset.x.coerceIn(-maxPanX, maxPanX), y = offset.y.coerceIn(-maxPanY, maxPanY))
}

// ----------------------------------------------------------------------------------
// MosaicScreen 主界面
// ----------------------------------------------------------------------------------

@Composable
fun MosaicScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // --- 状态变量：图片与OCR ---
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedImageIndex by remember { mutableIntStateOf(-1) }

    // 【关键】多图打码状态映射表 Map<ImageUri, Set<OcrIndex>>
    val multiImageMaskMap = remember { mutableStateMapOf<Uri, Set<Int>>() }

    var ocrRects by remember { mutableStateOf<List<OcrRect>>(emptyList()) }
    var originalImageSize by remember { mutableStateOf<IntSize?>(null) }
    var isOcrLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("字符识别中...") }
    var isAiProcessed by remember { mutableStateOf(false) }

    // --- 状态变量：视图控制 ---
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var selectedOcrIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    var editMode by remember { mutableStateOf(EditMode.AI) }
    var isEffectView by remember { mutableStateOf(false) }
    var isPickingImage by rememberSaveable { mutableStateOf(false) }

    // --- 状态变量：关联发现 ---
    var showAssocDialog by remember { mutableStateOf(false) }
    var assocTargets by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var assocNames by remember { mutableStateOf("") }

    // --- 状态变量：聊天与脚本 ---
    var showChatOverlay by remember { mutableStateOf(false) } // 控制聊天窗口显示
    // 【修改】移除了本地的 chatMessages，改用 ChatRepository.messages

    // 撤销重做堆栈 (针对单张图)
    val undoStack = remember { mutableStateListOf<Set<Int>>() }
    val redoStack = remember { mutableStateListOf<Set<Int>>() }

    // ----------------------------------------------------------------------------------
    // 逻辑函数
    // ----------------------------------------------------------------------------------

    fun pushUndo() {
        undoStack.add(selectedOcrIndices)
        if (undoStack.size > 30) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun performUndo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(selectedOcrIndices)
            selectedOcrIndices = undoStack.removeAt(undoStack.lastIndex)
            images.getOrNull(selectedImageIndex)?.let { uri -> multiImageMaskMap[uri] = selectedOcrIndices }
        }
    }

    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(selectedOcrIndices)
            selectedOcrIndices = redoStack.removeAt(redoStack.lastIndex)
            images.getOrNull(selectedImageIndex)?.let { uri -> multiImageMaskMap[uri] = selectedOcrIndices }
        }
    }

    fun updateSelection(newSelection: Set<Int>) {
        pushUndo()
        selectedOcrIndices = newSelection
        images.getOrNull(selectedImageIndex)?.let { uri -> multiImageMaskMap[uri] = newSelection }
    }

    // 动态分类统计
    val categorySummary: Map<String, Pair<Int, Boolean>> by remember(ocrRects, selectedOcrIndices) {
        derivedStateOf {
            ocrRects.filter { it.type != "none" && it.type != "未分类" }
                .groupBy { it.type }
                .mapValues { entry ->
                    val typeName = entry.key
                    val rects = entry.value
                    val allIndices = ocrRects.indices.filter { ocrRects[it].type == typeName }
                    val isFullyChecked = allIndices.isNotEmpty() && allIndices.all { selectedOcrIndices.contains(it) }
                    Pair(rects.size, isFullyChecked)
                }
        }
    }

    // --- 批量处理逻辑 ---
    fun handleBatchProcessing() {
        if (images.isEmpty()) return
        loadingMessage = "AI 正在批量处理..."
        isOcrLoading = true
        scope.launch {
            delay(1500) // 模拟处理时间
            images.forEach { uri ->
                val fileNameKey = getFileNameKey(context, uri)
                val aiJsonName = "${fileNameKey}_ai.json"
                val aiJsonString = loadJsonFromAssets(context, aiJsonName)
                if (aiJsonString != null) {
                    val (rects, _) = parseOcrJson(aiJsonString)
                    val highIndices = rects.indices.filter { rects[it].sensitivity == "high" }.toSet()
                    multiImageMaskMap[uri] = highIndices
                }
            }
            isOcrLoading = false
            Toast.makeText(context, "批量处理完成", Toast.LENGTH_SHORT).show()
            // 刷新当前视图
            images.getOrNull(selectedImageIndex)?.let { currentUri ->
                selectedOcrIndices = multiImageMaskMap[currentUri] ?: emptySet()
            }
            isEffectView = true
        }
    }

    // --- AI 聊天脚本处理逻辑 ---
    fun handleUserMessage(text: String) {
        // 1. 添加用户消息到全局仓库
        ChatRepository.addMessage(isUser = true, text = text)

        scope.launch {
            delay(600) // 模拟网络延迟

            // 脚本匹配：如果包含关键字，则执行打码
            if (text.contains("手机号") && (text.contains("身份证") || text.contains("ID"))) {
                // AI 回复添加到全局仓库
                ChatRepository.addMessage(false, "好的，正在为您扫描图片中的手机号和身份证信息...")

                // 停留一会，让用户看清回复
                delay(1200)

                // 2. 收起聊天框
                showChatOverlay = false

                // 3. 显示全屏 Loading，模拟执行过程
                loadingMessage = "AI 正在执行打码..."
                isOcrLoading = true
                delay(1800) // 执行时间

                // 4. 执行实际的打码筛选
                // 这里的逻辑：匹配 OCR 结果中的类型或文本特征
                val targetIndices = ocrRects.indices.filter { index ->
                    val type = ocrRects[index].type
                    val txt = ocrRects[index].text
                    // 模糊匹配逻辑
                    type.contains("手机") || type.contains("PHONE") ||
                            type.contains("身份证") || type.contains("ID") || type.contains("CN_ID") ||
                            txt.length == 11 || txt.length == 18 // 简单长度规则作为兜底
                }.toSet()

                // 更新选区并切换到效果图
                updateSelection(selectedOcrIndices + targetIndices)
                isEffectView = true
                isOcrLoading = false

                Toast.makeText(context, "AI 执行完毕", Toast.LENGTH_SHORT).show()
            } else {
                // 普通回复添加到全局仓库
                ChatRepository.addMessage(false, "收到。但我目前主要擅长处理隐私信息，试试说“打码手机号”？")
            }
        }
    }

    // --- 单图 AI 处理 ---
    fun handleAiAction() {
        val uri = images.getOrNull(selectedImageIndex) ?: return
        loadingMessage = "AI 智能打码中..."
        isOcrLoading = true
        scope.launch {
            delay(1000)
            val fileNameKey = getFileNameKey(context, uri)
            val aiJsonString = loadJsonFromAssets(context, "${fileNameKey}_ai.json")
            if (aiJsonString != null) {
                val (aiRects, size) = parseOcrJson(aiJsonString)
                ocrRects = aiRects
                originalImageSize = size
                val newIndices = aiRects.indices.filter { aiRects[it].sensitivity == "high" }.toSet()
                updateSelection(selectedOcrIndices + newIndices)
                isAiProcessed = true
                isEffectView = true
            }
            isOcrLoading = false
        }
    }

    // --- 点击词条 ---
    fun onRectClick(index: Int) {
        val clickedRect = ocrRects[index]
        val isNowSelecting = !selectedOcrIndices.contains(index)
        val newSet = selectedOcrIndices.toMutableSet()
        if (isNowSelecting) newSet.add(index) else newSet.remove(index)

        updateSelection(newSet)
        isEffectView = true

        if (isNowSelecting && clickedRect.associationId != null) {
            val relatedIndices = ocrRects.indices.filter { i ->
                i != index && ocrRects[i].associationId == clickedRect.associationId && !selectedOcrIndices.contains(i)
            }.toSet()
            if (relatedIndices.isNotEmpty()) {
                assocNames = relatedIndices.map { ocrRects[it].text }.distinct().joinToString("、")
                assocTargets = relatedIndices
                showAssocDialog = true
            }
        }
    }

    // 生命周期管理
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !isPickingImage) {
                // 注意：这里我们不清理 ChatRepository，以保持聊天记录
                images = emptyList(); selectedImageIndex = -1
                multiImageMaskMap.clear()
                undoStack.clear(); redoStack.clear()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer); onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 图片切换逻辑
    LaunchedEffect(selectedImageIndex, images) {
        val uri = images.getOrNull(selectedImageIndex) ?: return@LaunchedEffect
        undoStack.clear(); redoStack.clear()
        isAiProcessed = false

        val savedIndices = multiImageMaskMap[uri]
        if (savedIndices != null && savedIndices.isNotEmpty()) {
            selectedOcrIndices = savedIndices
            isEffectView = true
        } else {
            selectedOcrIndices = emptySet()
            isEffectView = false
        }

        loadingMessage = "字符识别中..."
        isOcrLoading = true
        delay(500)

        val fileNameKey = getFileNameKey(context, uri)
        // 如果已打码，倾向于加载 _ai.json 以确保坐标匹配
        val jsonToLoad = if (multiImageMaskMap.containsKey(uri)) "${fileNameKey}_ai.json" else "${fileNameKey}.json"

        var jsonString = loadJsonFromAssets(context, jsonToLoad)
        if (jsonString == null) jsonString = loadJsonFromAssets(context, "${fileNameKey}.json")

        if (jsonString != null) {
            val (r, s) = parseOcrJson(jsonString)
            ocrRects = r
            originalImageSize = s
            if (jsonToLoad.contains("_ai")) isAiProcessed = true
        }
        isOcrLoading = false
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            images = images + uris
            if (selectedImageIndex == -1) selectedImageIndex = 0
        }
    }

    // ----------------------------------------------------------------------------------
    // UI 结构
    // ----------------------------------------------------------------------------------
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 主内容层
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                TopBar(onExport = { })

                // 预览区域
                Box(modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { previewSize = it }) {
                    ImagePreview(
                        modifier = Modifier.fillMaxSize(),
                        imageUri = images.getOrNull(selectedImageIndex),
                        ocrRects = ocrRects,
                        originalImageSize = originalImageSize,
                        zoomLevel = zoomLevel,
                        panOffset = panOffset,
                        onPanChange = { panOffset = coercePanOffset(it, zoomLevel, previewSize) },
                        selectedOcrIndices = selectedOcrIndices,
                        isEffectView = isEffectView,
                        isAiProcessed = isAiProcessed,
                        onOcrRectClick = { onRectClick(it) }
                    )

                    if (isOcrLoading) OcrLoadingOverlay(loadingMessage)

                    // 缩放控制滑块
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        if (zoomLevel > 1f) {
                            val maxPanX = (previewSize.width * (zoomLevel - 1)) / 2f
                            Slider(
                                value = -panOffset.x,
                                onValueChange = { panOffset = coercePanOffset(Offset(-it, panOffset.y), zoomLevel, previewSize) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                valueRange = -maxPanX..maxPanX
                            )
                        }
                    }
                    Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                        if (zoomLevel > 1f) {
                            val maxPanY = (previewSize.height * (zoomLevel - 1)) / 2f
                            val sliderLength = with(LocalDensity.current) { (previewSize.height * 0.6f).toDp() }
                            Slider(
                                value = panOffset.y,
                                onValueChange = { panOffset = coercePanOffset(Offset(panOffset.x, it), zoomLevel, previewSize) },
                                valueRange = -maxPanY..maxPanY,
                                modifier = Modifier.layout { m, c ->
                                    val p = m.measure(c)
                                    layout(p.height, p.width) { p.place(-(p.width - p.height) / 2, -(p.height - p.width) / 2) }
                                }.graphicsLayer(rotationZ = 270f).width(sliderLength)
                            )
                        }
                    }
                }

                ImageThumbnails(
                    images = images, selectedImageIndex = selectedImageIndex,
                    onImageSelect = { if (selectedImageIndex != it) { selectedImageIndex = it; zoomLevel = 1f; panOffset = Offset.Zero } },
                    onImageRemove = {
                        val list = images.toMutableList(); list.removeAt(it)
                        multiImageMaskMap.remove(images[it])
                        images = list
                        if (images.isEmpty()) selectedImageIndex = -1 else selectedImageIndex = selectedImageIndex.coerceAtMost(images.size - 1)
                    }
                )

                Controls(
                    onAddImage = { isPickingImage = true; imagePickerLauncher.launch("image/*") },
                    editMode = editMode, onEditModeChange = { editMode = it },
                    isEffectView = isEffectView, onViewChange = { isEffectView = it },
                    onUndo = { performUndo() },
                    onRedo = { performRedo() },
                    zoomLevel = zoomLevel,
                    onZoomChange = { zoomLevel = it.coerceIn(0.5f, 3f) },
                    onAiOneClick = { handleAiAction() }
                )

                // 底部栏 (触发器)
                BottomBar(
                    editMode = editMode,
                    categorySummary = categorySummary,
                    onCategoryClick = { typeName ->
                        val info = categorySummary[typeName]
                        val allIndices = ocrRects.indices.filter { ocrRects[it].type == typeName }.toSet()
                        val newSet = if (info?.second == true) selectedOcrIndices - allIndices else selectedOcrIndices + allIndices
                        updateSelection(newSet)
                        if (!info?.second!!) isEffectView = true
                    },
                    onBatchConfirm = { handleBatchProcessing() },
                    onOpenChat = { showChatOverlay = true } // 点击打开悬浮窗
                )
            }

            // --- 浮层：AI 聊天窗口 ---
            // 使用全局 ChatRepository.messages
            ChatOverlay(
                isVisible = showChatOverlay,
                messages = ChatRepository.messages,
                onClose = { showChatOverlay = false },
                onSendScriptedMessage = { text -> handleUserMessage(text) }
            )

            // --- 浮层：关联发现对话框 ---
            if (showAssocDialog) {
                AlertDialog(
                    onDismissRequest = { showAssocDialog = false },
                    icon = { Icon(Icons.Default.AutoFixHigh, null, tint = Color(0xFF4A90E2)) },
                    title = { Text("AI 智能关联", fontWeight = FontWeight.Bold) },
                    text = { Text("检测到文中还包含：$assocNames，是否同步打码？") },
                    confirmButton = {
                        Button(onClick = {
                            updateSelection(selectedOcrIndices + assocTargets)
                            showAssocDialog = false
                        }) { Text("确认打码") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAssocDialog = false }) { Text("忽略", color = Color.Gray) }
                    },
                    containerColor = Color(0xFF2C2C2C), titleContentColor = Color.White, textContentColor = Color.LightGray
                )
            }
        }
    }
}

@Composable
fun OcrLoadingOverlay(message: String) {
    val infiniteTransition = rememberInfiniteTransition()
    val scanProgress by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse))
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.TopCenter).offset(y = (scanProgress * 500).dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF4A90E2), Color.Transparent))))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF4A90E2), strokeWidth = 4.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}