package com.litiangu.wuji.data

import android.content.Context
import com.litiangu.wuji.data.db.WujiDatabase
import com.litiangu.wuji.data.db.WujiDatabaseProvider
import com.litiangu.wuji.data.settings.SettingsDataStore
import com.litiangu.wuji.ui.analysis.AnalysisUiState
import com.litiangu.wuji.ui.analysis.buildAnalysisUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 分析页预热内存缓存（Step 32，与 UserProfile/BudgetAnchor/Backup/Trash 同款模式）。
 *
 * 在 WujiApplication.onCreate 预热期间同步读一次四个数据源（itemDao/categoryDao/
 * categoryBudgetDao/budgetAnchorFlow/monthlyBudgetAnchorFlow/privacyModeFlow）的首帧，
 * 立刻算出 AnalysisUiState 写入 StateFlow，让分析页首帧即真实值，
 * 消除「进入分析页短暂跳闪」。后台协程持续订阅四流，数据变化自动同步。
 *
 * 隐私模式单独流，因 UI 需要区分（金额脱敏 vs 状态值）。
 */
object AnalysisRepository {
    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private val _privacyMode = MutableStateFlow(false)
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false

    /**
     * 同步预热 + 后台订阅。WujiApplication 在 _isReady=true 之前调用一次。
     * 幂等：重复调用不会重启订阅。
     */
    suspend fun init(context: Context) {
        if (started) return
        val database: WujiDatabase = WujiDatabaseProvider.get(context)
        val settings = SettingsDataStore(context)
        val itemDao = database.itemDao()
        val categoryDao = database.categoryDao()
        val categoryBudgetDao = database.categoryBudgetDao()

        // 1) 同步取首帧，立刻算出真实 StateFlow
        val items = itemDao.observeAll().first()
        val categories = categoryDao.observeAll().first()
        val anchor = settings.budgetAnchorFlow.first()
        val monthlyBudget = settings.monthlyBudgetAnchorFlow.first()
        val budgets = categoryBudgetDao.observeAll().first()
        _privacyMode.value = settings.privacyModeFlow.first()
        _uiState.value = buildAnalysisUiState(items, categories, anchor, monthlyBudget, budgets)

        // 2) 后台持续订阅
        // 预算配置合成（保持 4 combine 兼容：item/category/anchor/预算对）
        val budgetConfig = combine(
            settings.monthlyBudgetAnchorFlow,
            categoryBudgetDao.observeAll()
        ) { m, b -> m to b }

        scope.launch {
            combine(
                itemDao.observeAll(),
                categoryDao.observeAll(),
                settings.budgetAnchorFlow,
                budgetConfig
            ) { items, categories, anchor, cfg ->
                val (monthly, budgets) = cfg
                buildAnalysisUiState(items, categories, anchor, monthly, budgets)
            }.collect { _uiState.value = it }
        }
        scope.launch {
            settings.privacyModeFlow.collect { _privacyMode.value = it }
        }

        started = true
    }
}
