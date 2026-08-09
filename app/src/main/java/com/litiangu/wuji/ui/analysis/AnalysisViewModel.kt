package com.litiangu.wuji.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litiangu.wuji.data.db.CategoryBudgetDao
import com.litiangu.wuji.data.db.CategoryBudgetEntity
import com.litiangu.wuji.data.db.CategoryDao
import com.litiangu.wuji.data.db.CategoryEntity
import com.litiangu.wuji.data.db.ItemDao
import com.litiangu.wuji.data.db.ItemEntity
import com.litiangu.wuji.data.db.ItemStatus
import com.litiangu.wuji.data.db.toItemInput
import com.litiangu.wuji.data.settings.SettingsDataStore
import com.litiangu.wuji.domain.computeHealthScore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

// 分类分布的一个柱：分类名 + 价值合计 + 数量
data class CategoryBarData(val label: String, val value: Double, val count: Int)

// 月度趋势的一个点：月份标签 + 当月投入（购入+附加）
data class MonthlyData(val label: String, val value: Double)

// 分类预算一行：分类名 + 近 30 天支出 + 该类月度限额 + 是否超支（Step 21 L2）
data class CategoryBudgetRow(val label: String, val spend: Double, val limit: Double, val over: Boolean)

// 分析页全部展示数据
data class AnalysisUiState(
    val hasItems: Boolean = false,
    val score: Int = 0,                    // 健康分四舍五入整数
    val gradeLabel: String = "",            // 优/良/中/差
    val gradeComment: String = "",          // 评语
    val d1: Double = 0.0,                   // 利用率
    val d2: Double = 0.0,                   // 价值健康度
    val d3: Double = 0.0,                   // 流转率
    val d4: Double = 0.0,                   // 变现回收
    val totalInput: Double = 0.0,           // 净持有口径：未售出 购入+附加
    val totalRecovery: Double = 0.0,        // 已变现：全部 二手收益
    val netHolding: Double = 0.0,           // 净持有 = 未售出投入 − 全部变现
    val categoryBars: List<CategoryBarData> = emptyList(),
    val monthlyTrend: List<MonthlyData> = emptyList(),
    // ===== Step 21 预算遵从信号（独立，不进健康分）=====
    val monthlyBudget: Double = 0.0,        // 全局月度预算（0 = 未配置）
    val recentSpend30: Double = 0.0,        // 近 30 天支出（购入+附加）
    val categoryBudgetRows: List<CategoryBudgetRow> = emptyList() // 仅含「已设限额」的分类
)

/**
 * 分析页：健康分（含四维拆解）+ 资产结构 + 分类分布 + 月度趋势 + 预算遵从（Step 21）。
 * 全部由 Room Flow + 预算锚点流组合计算，数据变化自动刷新。
 */
class AnalysisViewModel(
    private val itemDao: ItemDao,
    private val categoryDao: CategoryDao,
    private val categoryBudgetDao: CategoryBudgetDao,
    private val settings: SettingsDataStore
) : ViewModel() {

    // 把「月度锚点 + 分类预算」合成一个 Pair 流，保持 combine 元数 = 4（兼容性更稳）。
    private val budgetConfig = combine(
        settings.monthlyBudgetAnchorFlow,
        categoryBudgetDao.observeAll()
    ) { monthly, budgets -> monthly to budgets }

    val uiState: StateFlow<AnalysisUiState> = combine(
        itemDao.observeAll(),
        categoryDao.observeAll(),
        settings.budgetAnchorFlow,
        budgetConfig
    ) { items, categories, anchor, cfg ->
        val (monthly, budgets) = cfg
        buildState(items, categories, anchor, monthly, budgets)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalysisUiState())

    private fun buildState(
        items: List<ItemEntity>,
        categories: List<CategoryEntity>,
        anchor: Double,
        monthlyBudget: Double,
        budgets: List<CategoryBudgetEntity>
    ): AnalysisUiState {
        val inputs = items.map { it.toItemInput() }
        val result = computeHealthScore(inputs, anchor)
        val nameById = categories.associateBy { it.id }

        // 资产结构：净资产 = Σ(未售出 购入+附加) − Σ(全部 变现)，对齐文档 2.1 口径
        val activeItems = items.filter { it.status != ItemStatus.SOLD }
        val totalInput = activeItems.sumOf { it.purchasePrice + it.extraCost }
        val totalRecovery = items.sumOf { it.resaleValue }

        // 分类分布：按分类分组（含未分类），价值 = 购入+附加
        val bars = items.groupBy { it.categoryId }
            .map { (catId, list) ->
                CategoryBarData(
                    label = nameById[catId]?.name ?: "未分类",
                    value = list.sumOf { it.purchasePrice + it.extraCost },
                    count = list.size
                )
            }
            .sortedByDescending { it.value }

        // ===== Step 21：近 30 天支出 + 分类限额比对 =====
        // 近 30 天窗口（epoch 毫秒）。purchaseDate 为空（极老数据）的物品按 0 处理，自然被 cutoff 排除。
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val recentItems = items.filter { it.purchaseDate >= cutoff }
        val recentSpend30 = recentItems.sumOf { it.purchasePrice + it.extraCost }

        // 仅对「已设限额」的分类生成一行（未设限额的回落到全局月度预算，不单列）。
        val limitById = budgets.associate { it.categoryId to it.monthlyLimit }
        val categoryBudgetRows = categories
            .filter { it.id in limitById }
            .map { cat ->
                val spend = recentItems
                    .filter { it.categoryId == cat.id }
                    .sumOf { it.purchasePrice + it.extraCost }
                val limit = limitById[cat.id] ?: 0.0
                CategoryBudgetRow(cat.name, spend, limit, spend > limit)
            }
            .sortedByDescending { it.spend }

        return AnalysisUiState(
            hasItems = items.isNotEmpty(),
            score = result.totalRounded,
            gradeLabel = result.grade.label,
            gradeComment = result.comment,
            d1 = result.dimensions.d1Usage,
            d2 = result.dimensions.d2ValueHealth,
            d3 = result.dimensions.d3Turnover,
            d4 = result.dimensions.d4Recovery,
            totalInput = totalInput,
            totalRecovery = totalRecovery,
            netHolding = totalInput - totalRecovery,
            categoryBars = bars,
            monthlyTrend = buildMonthlyTrend(items),
            monthlyBudget = monthlyBudget,
            recentSpend30 = recentSpend30,
            categoryBudgetRows = categoryBudgetRows
        )
    }

    // 最近 12 个月投入趋势（按 purchaseDate 聚合 购入+附加），不足月份补 0
    private fun buildMonthlyTrend(items: List<ItemEntity>): List<MonthlyData> {
        val now = System.currentTimeMillis()
        val months = (0 until 12).map { offset ->
            val c = Calendar.getInstance().apply { timeInMillis = now }
            c.add(Calendar.MONTH, -offset)
            c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
        }.reversed()
        val sums = months.associateWith { 0.0 }.toMutableMap()
        items.forEach { item ->
            val c = Calendar.getInstance().apply { timeInMillis = item.purchaseDate }
            val key = c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
            if (key in sums) {
                sums[key] = sums.getValue(key) + item.purchasePrice + item.extraCost
            }
        }
        return months.map { (y, m) ->
            MonthlyData(
                label = "${y % 100}-${m.toString().padStart(2, '0')}",
                value = sums[y to m] ?: 0.0
            )
        }
    }
}
