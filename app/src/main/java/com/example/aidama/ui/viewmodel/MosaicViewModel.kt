package com.example.aidama.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aidama.data.model.AgentRecommendationDomain
import com.example.aidama.data.model.CategoryUiModel
import com.example.aidama.data.model.EditMode
import com.example.aidama.data.model.MaskedItemDomain
import com.example.aidama.data.model.OcrRect
import com.example.aidama.data.model.UserFeedbackDomain
import com.example.aidama.data.repository.AiAgentService
import com.example.aidama.data.repository.ChatRepository
import com.example.aidama.data.repository.OcrRepository
import com.example.aidama.utils.MosaicUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class MosaicUiState(
    val images: List<Uri> = emptyList(),
    val selectedIndex: Int = -1,
    val isOcrLoading: Boolean = false,
    val loadingMessage: String = "",
    val isEffectView: Boolean = false,
    val isExporting: Boolean = false,
    val zoomLevel: Float = 1f,
    val panOffset: Offset = Offset.Zero,
    val editMode: EditMode = EditMode.AI,
    val isAiProcessed: Boolean = false,
    val showChatOverlay: Boolean = false,
    val batchRuleMust: List<String> = emptyList(),
    val batchRuleDemand: List<String> = emptyList(),
    val batchRuleCustom: List<String> = emptyList(),
    val showBatchRuleDialog: Boolean = false,
    val showAnalysisNotice: Boolean = false,
    val showTypeChangeNotice: Boolean = false,
    val typeChangeNotice: String = ""
)

sealed class MosaicUiEvent {
    data class ShowToast(val message: String) : MosaicUiEvent()
}

class MosaicViewModel(
    private val applicationContext: Context,
    private val ocrRepository: OcrRepository,
    private val aiAgentService: AiAgentService // 🚀 注入大模型智能体抽象服务
) : ViewModel() {

    var state by mutableStateOf(MosaicUiState())
        private set

    private val _uiEvent = Channel<MosaicUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    var currentOcrRects by mutableStateOf<List<OcrRect>>(emptyList())
    var originalImageSize by mutableStateOf<IntSize?>(null)
    var selectedOcrIndices by mutableStateOf<Set<Int>>(emptySet())
    var typeColorMap by mutableStateOf<Map<String, Color>>(emptyMap())

    private val multiImageMaskMap = mutableStateMapOf<Uri, Set<Int>>()
    private val undoStack = mutableListOf<Set<Int>>()
    private val redoStack = mutableListOf<Set<Int>>()
    private var pendingRuleReport: String = ""

    // 动态计算底部分类统计（现在完全由 Agent 返回的分类驱动）
    val categorySummary: List<CategoryUiModel> get() {
        val result = mutableListOf<CategoryUiModel>()
        if (currentOcrRects.isEmpty()) return result

        val typeGroups = currentOcrRects.indices
            .groupBy { currentOcrRects[it].type }
            .filterKeys { it != "none" && it != "未分类" }

        typeGroups.forEach { (type, indices) ->
            result.add(CategoryUiModel(type, indices.size, indices.all { selectedOcrIndices.contains(it) }))
        }
        return result
    }

    fun addImages(uris: List<Uri>) {
        val start = state.images.size
        state = state.copy(images = state.images + uris)
        viewModelScope.launch {
            uris.forEachIndexed { i, uri ->
                ocrRepository.loadOcrData(uri)?.let { (rects, _) ->
                    postAiAnalysisToChat(start + i, rects)
                }
            }
            selectImage(start + uris.size - 1)
        }
    }

    fun selectImage(index: Int) {
        if (index !in state.images.indices) return
        val uri = state.images[index]
        state = state.copy(
            selectedIndex = index, zoomLevel = 1f, panOffset = Offset.Zero,
            isEffectView = false, editMode = EditMode.AI
        )
        undoStack.clear(); redoStack.clear()

        selectedOcrIndices = multiImageMaskMap[uri] ?: emptySet()
        ocrRepository.getCached(uri)?.let { (rects, size) ->
            currentOcrRects = rects; originalImageSize = size
            refreshTypeColors()
        }
    }

    // ==========================================
    // 🚀 核心：接入 AI Agent 处理点击意图
    // ==========================================
    fun handleOcrRectClick(index: Int) {
        val clickedRect = currentOcrRects[index]
        val isCurrentlyMasked = selectedOcrIndices.contains(index)

        // 1. 乐观 UI 更新：让用户立刻看到点击的框变色，不被网络请求阻塞
        val newSelection = if (isCurrentlyMasked) selectedOcrIndices - index else selectedOcrIndices + index
        updateSelection(newSelection)
        toggleEffectView(true)

        // 2. 异步请求大模型 Agent 分析上下文意图
        viewModelScope.launch {
            setLoading(true, "AI 正在理解意图...")

            val allTexts = currentOcrRects.map { it.text }
            val currentMaskedItems = newSelection.map { idx ->
                MaskedItemDomain(currentOcrRects[idx].type, currentOcrRects[idx].text)
            }

            // 构造用户行为反馈
            val feedback = UserFeedbackDomain(
                clickedChar = clickedRect.text,
                contextWindow = clickedRect.text, // 简易处理：直接将整个框内容作为上下文
                actionType = if (isCurrentlyMasked) "remove" else "add"
            )

            // 发送给在线/端侧大模型微服务
            val result = aiAgentService.analyzeMasking(allTexts, currentMaskedItems, feedback)

            // 3. 将大模型的决策应用到屏幕上
            if (result.error == null && result.recommendations.isNotEmpty()) {
                applyAgentRecommendations(result.recommendations)
            } else if (result.error != null) {
                _uiEvent.send(MosaicUiEvent.ShowToast("Agent服务异常: ${result.error}"))
            }

            setLoading(false)
        }
    }

    // ==========================================
    // 🚀 核心：接入 AI Agent 处理自然语言指令
    // ==========================================
    fun handleUserMessage(naturalLanguageText: String) {
        // 先把用户说的话显示在聊天框
        ChatRepository.addMessage(isUser = true, text = naturalLanguageText)

        viewModelScope.launch {
            if (naturalLanguageText.contains("好了")) {
                state = state.copy(showChatOverlay = false)
                handleBatchProcessing()
                return@launch
            }

            setLoading(true, "AI 正在思考...")

            val allTexts = currentOcrRects.map { it.text }
            val currentMaskedItems = selectedOcrIndices.map { idx ->
                MaskedItemDomain(currentOcrRects[idx].type, currentOcrRects[idx].text)
            }

            // 构造聊天意图反馈
            val feedback = UserFeedbackDomain(
                actionType = "chat",
                naturalLanguage = naturalLanguageText
            )

            // 请求 Agent
            val result = aiAgentService.analyzeMasking(allTexts, currentMaskedItems, feedback)

            if (result.error == null && result.recommendations.isNotEmpty()) {
                applyAgentRecommendations(result.recommendations)
            } else if (result.error != null) {
                ChatRepository.addMessage(false, "抱歉，分析出错：${result.error}")
            } else {
                ChatRepository.addMessage(false, "我好像没找到需要处理的内容，请换种说法试试。")
            }

            setLoading(false)
        }
    }

    /**
     * 解析并应用 Agent 返回的策略（打码、取消打码、修改分类标签）
     */
    private fun applyAgentRecommendations(recommendations: List<AgentRecommendationDomain>) {
        val updatedSelection = selectedOcrIndices.toMutableSet()
        val updatedRects = currentOcrRects.toMutableList() // 允许修改 Category 标签
        val replyBuilder = StringBuilder()

        recommendations.forEach { rec ->
            // 模糊匹配：找到画布中包含这段文本的所有 OCR 框
            val matchingIndices = updatedRects.indices.filter { updatedRects[it].text.contains(rec.text) }

            if (matchingIndices.isNotEmpty()) {
                if (rec.action == "mask") {
                    updatedSelection.addAll(matchingIndices)
                    // 如果 Agent 识别出了新的业务标签，更新原有数据
                    if (rec.category.isNotBlank() && rec.category != "未分类") {
                        matchingIndices.forEach { idx ->
                            updatedRects[idx] = updatedRects[idx].copy(type = rec.category)
                        }
                    }
                } else if (rec.action == "unmask") {
                    updatedSelection.removeAll(matchingIndices.toSet())
                }

                // 将后端的 reason 拼接到 AI 回复中
                val actionText = if(rec.action == "mask") "打码" else "取消打码"
                replyBuilder.append("已${actionText}：${rec.text}。\n理由：${rec.reason}\n\n")
            }
        }

        // 应用状态更新
        currentOcrRects = updatedRects
        updateSelection(updatedSelection)
        refreshTypeColors()

        // 让 AI 助手在聊天框把理由说出来，并弹出提示气泡
        if (replyBuilder.isNotEmpty()) {
            ChatRepository.addMessage(isUser = false, text = replyBuilder.toString().trimEnd())
            state = state.copy(showAnalysisNotice = true)
            viewModelScope.launch { delay(3000); state = state.copy(showAnalysisNotice = false) }
        }
    }

    fun handleAiAction() {
        setLoading(true, "AI 正在进行全局高敏排查...")
        viewModelScope.launch {
            delay(800)
            // 兜底策略：把 OCR 识别出来的高敏感数据直接打码
            val high = currentOcrRects.indices.filter { currentOcrRects[it].sensitivity == "high" }.toSet()
            updateSelection(selectedOcrIndices + high)
            state = state.copy(isAiProcessed = true, isEffectView = true)
            refreshTypeColors()
            setLoading(false)
        }
    }

    fun handleBatchProcessing() {
        if (state.images.isEmpty()) return
        val m = mutableListOf<String>(); val d = mutableListOf<String>(); val c = mutableListOf<String>()
        val grouped = currentOcrRects.filter { it.type != "none" && it.type != "未分类" }.groupBy { it.type }

        grouped.forEach { (type, rects) ->
            val count = currentOcrRects.indices.filter { currentOcrRects[it].type == type }.count { selectedOcrIndices.contains(it) }
            when {
                count == rects.size -> m.add(type)
                count > 0 -> d.add(type)
                else -> c.add(type)
            }
        }
        state = state.copy(batchRuleMust = m, batchRuleDemand = d, batchRuleCustom = c, showBatchRuleDialog = true)

        val report = StringBuilder("AI 为当前图片制定的打码规则：\n\n")
        if (m.isNotEmpty()) report.append("🔴 **必打码**：${m.joinToString("、")}\n")
        if (d.isNotEmpty()) report.append("🟠 **按需打码**：${d.joinToString("、")}\n")
        if (c.isNotEmpty()) report.append("🔵 **不打码**：${c.joinToString("、")}\n")
        pendingRuleReport = report.toString()
    }

    fun confirmBatchApply() {
        state = state.copy(showBatchRuleDialog = false)
        setLoading(true, "应用规则到所有图片...")
        viewModelScope.launch {
            delay(1000)
            val mustMaskTypes = state.batchRuleMust.toSet()
            state.images.forEach { u ->
                if (u == state.images.getOrNull(state.selectedIndex)) return@forEach
                ocrRepository.getCached(u)?.let { (r, _) ->
                    multiImageMaskMap[u] = r.indices.filter { i -> mustMaskTypes.contains(r[i].type) }.toSet()
                }
            }
            setLoading(false)
            _uiEvent.send(MosaicUiEvent.ShowToast("多图批量应用完成"))
        }
    }

    fun startBatchExport() {
        if (state.images.isEmpty()) return
        viewModelScope.launch {
            state = state.copy(isEffectView = true, isExporting = true)
            setLoading(true, "图片渲染导出中...")
            var count = 0
            for (uri in state.images) {
                val data = ocrRepository.getCached(uri) ?: ocrRepository.loadOcrData(uri)
                if (data != null) {
                    val masks = multiImageMaskMap[uri] ?: emptySet()
                    val success = MosaicUtils.saveImageToGallery(applicationContext, uri, data.first, masks)
                    if (success) count++
                }
            }
            setLoading(false)
            state = state.copy(isExporting = false)
            _uiEvent.send(MosaicUiEvent.ShowToast("成功导出 $count 张图片到相册"))
        }
    }

    fun handleCategoryClick(type: String) {
        val indices = currentOcrRects.indices.filter { currentOcrRects[it].type == type }.toSet()
        val isAllSelected = indices.all { selectedOcrIndices.contains(it) }
        updateSelection(if (isAllSelected) selectedOcrIndices - indices else selectedOcrIndices + indices)
        toggleEffectView(true)
    }

    private fun postAiAnalysisToChat(imageIndex: Int, rects: List<OcrRect>) {
        val num = imageIndex + 1
        ChatRepository.addMessage(true, "上传并分析了图片$num")
        val sb = StringBuilder("识别到图片$num 内容，AI 建议关注：\n\n")
        val grouped = rects.filter { it.type != "未分类" && it.type != "none" }.groupBy { it.type }
        grouped.forEach { (t, r) ->
            sb.append("• $t: ${r.map { it.text }.distinct().take(3).joinToString("、")}\n")
        }
        ChatRepository.addMessage(false, sb.toString())
        state = state.copy(showAnalysisNotice = true)
        viewModelScope.launch { delay(3500); state = state.copy(showAnalysisNotice = false) }
    }

    fun navigateToChatAndReport() {
        state = state.copy(showBatchRuleDialog = false, editMode = EditMode.AI, showChatOverlay = true)
        viewModelScope.launch { delay(300); if (pendingRuleReport.isNotEmpty()) ChatRepository.addMessage(false, pendingRuleReport) }
    }

    fun removeImage(index: Int) {
        val uri = state.images[index]
        val l = state.images.toMutableList().apply { removeAt(index) }
        multiImageMaskMap.remove(uri)
        state = state.copy(images = l)
        if (l.isEmpty()) state = state.copy(selectedIndex = -1) else selectImage(index.coerceAtMost(l.size - 1))
    }

    private fun updateSelection(new: Set<Int>) {
        undoStack.add(selectedOcrIndices); if (undoStack.size > 30) undoStack.removeAt(0)
        redoStack.clear(); selectedOcrIndices = new
        state.images.getOrNull(state.selectedIndex)?.let { multiImageMaskMap[it] = new }
    }

    private fun refreshTypeColors() {
        val types = currentOcrRects.map { it.type }.filter { it != "none" && it != "未分类" }.distinct().sorted()
        typeColorMap = types.mapIndexed { i, t -> t to MosaicUtils.getColorForIndex(i) }.toMap()
    }

    fun undo() { if (undoStack.isNotEmpty()) { redoStack.add(selectedOcrIndices); updateSelection(undoStack.removeAt(undoStack.lastIndex)); undoStack.removeAt(undoStack.lastIndex) } }
    fun redo() { if (redoStack.isNotEmpty()) { undoStack.add(selectedOcrIndices); updateSelection(redoStack.removeAt(redoStack.lastIndex)); undoStack.removeAt(undoStack.lastIndex) } }
    fun toggleEffectView(isEffect: Boolean) { state = state.copy(isEffectView = isEffect) }
    fun setEditMode(mode: EditMode) { state = state.copy(editMode = mode) }
    fun setZoom(level: Float, size: IntSize) { val z = level.coerceIn(0.5f, 3f); state = state.copy(zoomLevel = z, panOffset = MosaicUtils.coercePanOffset(state.panOffset, z, size)) }
    fun setPan(offset: Offset, size: IntSize) { state = state.copy(panOffset = MosaicUtils.coercePanOffset(offset, state.zoomLevel, size)) }
    fun setShowChat(show: Boolean) { state = state.copy(showChatOverlay = show) }
    private fun setLoading(isLoading: Boolean, message: String = "") { state = state.copy(isOcrLoading = isLoading, loadingMessage = message) }
    fun dismissBatchDialog() { state = state.copy(showBatchRuleDialog = false) }
}

/**
 * 带有依赖注入的 ViewModel 工厂
 * 注入了 ApplicationContext、OCR 本地仓库、以及 AI Agent 远端服务
 */
class MosaicViewModelFactory(
    private val applicationContext: Context,
    private val ocrRepository: OcrRepository,
    private val aiAgentService: AiAgentService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MosaicViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MosaicViewModel(applicationContext, ocrRepository, aiAgentService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}