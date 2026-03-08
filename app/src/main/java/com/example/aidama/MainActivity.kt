package com.example.aidama

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aidama.data.model.AgentRecommendationDomain
import com.example.aidama.data.model.AgentResultDomain
import com.example.aidama.data.model.MaskedItemDomain
import com.example.aidama.data.model.UserFeedbackDomain
import com.example.aidama.data.repository.AiAgentService
import com.example.aidama.data.repository.MockOcrRepositoryImpl
import com.example.aidama.data.repository.OcrRepository
import com.example.aidama.ui.screen.MosaicScreen
import com.example.aidama.ui.theme.AIDamaTheme
import com.example.aidama.ui.viewmodel.MosaicViewModel
import com.example.aidama.ui.viewmodel.MosaicViewModelFactory
import kotlinx.coroutines.delay

// 比赛现场演示专用：Mock 的假智能体服务
// 等后端接口写好了，你只需写一个 OnlineAiAgentService 实现替换掉它就行，UI和ViewModel不用动！
class MockAiAgentService : AiAgentService {
    override suspend fun analyzeMasking(
        ocrTexts: List<String>,
        currentMasked: List<MaskedItemDomain>,
        userFeedback: UserFeedbackDomain,
        sessionId: String?
    ): AgentResultDomain {
        delay(1500) // 模拟网络延迟

        // 模拟对 "布局调整" 的分析
        if (userFeedback.clickedChar?.contains("布局") == true) {
            return AgentResultDomain(
                recommendations = listOf(
                    AgentRecommendationDomain("设计采用生动的色彩", "mask", "业务隐私", "识别到关联设计创意", 0.9f),
                    AgentRecommendationDomain("视觉元素", "mask", "业务隐私", "保护业务视觉内容", 0.9f)
                )
            )
        }

        // 兜底返回空建议
        return AgentResultDomain(emptyList())
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

            val ocrRepository: OcrRepository = MockOcrRepositoryImpl(applicationContext)
        val aiAgentService: AiAgentService = MockAiAgentService()

        setContent {
            AIDamaTheme {
                // 2. 注入依赖到屏幕
                val viewModel: MosaicViewModel = viewModel(
                    factory = MosaicViewModelFactory(applicationContext, ocrRepository, aiAgentService)
                )
                MosaicScreen(vm = viewModel) // 确保你的 MosaicScreen 接收这个 vm
            }
        }
    }
}