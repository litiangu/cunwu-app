package com.litiangu.wuji.data.settings

import android.content.Context
import com.litiangu.wuji.data.db.CategoryBudgetEntity
import com.litiangu.wuji.data.db.CategoryEntity
import com.litiangu.wuji.data.db.WujiDatabase
import com.litiangu.wuji.domain.DEFAULT_DAILY_BUDGET_ANCHOR
import com.litiangu.wuji.domain.DEFAULT_MONTHLY_BUDGET_ANCHOR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 预算锚点内存缓存（消除预算锚点页进入时的加载圈与默认闪跳，对齐 Step 25 的 UserProfileRepository）。
 *
 * 问题：预算锚点页依赖 DataStore(每日/每月锚点) + Room(分类/分类预算) 共四条流，
 * 首帧未到齐前先转圈，违背「点击即现」的交互预期。
 *
 * 解决：应用预热期把当前值同步读进内存 StateFlow，UI 首帧即真实值，无需 loading；
 * 写入仍走 DataStore / Room，StateFlow 通过订阅自动同步。
 */
object BudgetAnchorRepository {
    private lateinit var settings: SettingsDataStore
    private lateinit var db: WujiDatabase

    private val _daily = MutableStateFlow(DEFAULT_DAILY_BUDGET_ANCHOR)
    val daily: StateFlow<Double> = _daily

    private val _monthly = MutableStateFlow(DEFAULT_MONTHLY_BUDGET_ANCHOR)
    val monthly: StateFlow<Double> = _monthly

    private val _categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _categories

    private val _budgets = MutableStateFlow<List<CategoryBudgetEntity>>(emptyList())
    val budgets: StateFlow<List<CategoryBudgetEntity>> = _budgets

    /**
     * 在 Application 预热协程中调用（isReady 置 true 之前）。
     * 同步读取一次当前值写入 StateFlow，再启动后台订阅持续同步。
     */
    suspend fun init(context: Context, database: WujiDatabase) {
        if (::settings.isInitialized) return
        val appContext = context.applicationContext
        settings = SettingsDataStore(appContext)
        db = database

        // 先同步把当前持久值读到内存，保证 UI 首帧正确。
        _daily.value = settings.budgetAnchorFlow.first()
        _monthly.value = settings.monthlyBudgetAnchorFlow.first()
        _categories.value = db.categoryDao().observeAll().first()
        _budgets.value = db.categoryBudgetDao().observeAll().first()

        // 后续变化自动同步到 StateFlow。
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch { settings.budgetAnchorFlow.collect { _daily.value = it } }
        scope.launch { settings.monthlyBudgetAnchorFlow.collect { _monthly.value = it } }
        scope.launch { db.categoryDao().observeAll().collect { _categories.value = it } }
        scope.launch { db.categoryBudgetDao().observeAll().collect { _budgets.value = it } }
    }

    suspend fun setDaily(value: Double) = settings.setBudgetAnchor(value)

    suspend fun setMonthly(value: Double) = settings.setMonthlyBudgetAnchor(value)
}
