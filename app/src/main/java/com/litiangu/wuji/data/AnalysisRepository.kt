package com.litiangu.wuji.data

import android.content.Context
import com.litiangu.wuji.data.db.CategoryBudgetDao
import com.litiangu.wuji.data.db.CategoryDao
import com.litiangu.wuji.data.db.ItemDao
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

    // 数据源引用：init 时缓存，供 refresh() 强制重读（Step 35）
    @Volatile private var itemDao: ItemDao? = null
    @Volatile private var categoryDao: CategoryDao? = null
    @Volatile private var categoryBudgetDao: CategoryBudgetDao? = null
    @Volatile private var settings: SettingsDataStore? = null

    /**
     * 同步预热 + 后台订阅。WujiApplication 在 _isReady=true 之前调用一次。
     * 幂等：重复调用不会重启订阅。
     */
    suspend fun init(context: Context) {
        if (started) return
        val database: WujiDatabase = WujiDatabaseProvider.get(context)
        val appSettings = SettingsDataStore(context)
        val appItemDao = database.itemDao()
        val appCategoryDao = database.categoryDao()
        val appCategoryBudgetDao = database.categoryBudgetDao()

        itemDao = appItemDao
        categoryDao = appCategoryDao
        categoryBudgetDao = appCategoryBudgetDao
        settings = appSettings

        // 1) 同步取首帧，立刻算出真实 StateFlow
        val items = appItemDao.observeAll().first()
        val categories = appCategoryDao.observeAll().first()
        val anchor = appSettings.budgetAnchorFlow.first()
        val monthlyBudget = appSettings.monthlyBudgetAnchorFlow.first()
        val budgets = appCategoryBudgetDao.observeAll().first()
        _privacyMode.value = appSettings.privacyModeFlow.first()
        _uiState.value = buildAnalysisUiState(items, categories, anchor, monthlyBudget, budgets)

        // 2) 后台持续订阅
        // 预算配置合成（保持 4 combine 兼容：item/category/anchor/预算对）
        val budgetConfig = combine(
            appSettings.monthlyBudgetAnchorFlow,
            appCategoryBudgetDao.observeAll()
        ) { m, b -> m to b }

        scope.launch {
            combine(
                appItemDao.observeAll(),
                appCategoryDao.observeAll(),
                appSettings.budgetAnchorFlow,
                budgetConfig
            ) { items, categories, anchor, cfg ->
                val (monthly, budgets) = cfg
                buildAnalysisUiState(items, categories, anchor, monthly, budgets)
            }.collect { _uiState.value = it }
        }
        scope.launch {
            appSettings.privacyModeFlow.collect { _privacyMode.value = it }
        }

        started = true
    }

    /**
     * 刷新（Step 35）：强制重读五源（物品/分类/分类预算/每日锚点/月度预算）并重建 UiState。
     * 供分析页顶栏刷新按钮调用；Room Flow 后台订阅之外的确定性同步
     * （典型场景：备份还原后立即反映还原后数据）。
     * 未 init（字段为 null）时静默跳过。
     */
    suspend fun refresh() {
        val dao = itemDao ?: return
        val catDao = categoryDao ?: return
        val budgetDao = categoryBudgetDao ?: return
        val st = settings ?: return
        val items = dao.observeAll().first()
        val categories = catDao.observeAll().first()
        val anchor = st.budgetAnchorFlow.first()
        val monthlyBudget = st.monthlyBudgetAnchorFlow.first()
        val budgets = budgetDao.observeAll().first()
        _uiState.value = buildAnalysisUiState(items, categories, anchor, monthlyBudget, budgets)
    }
}
