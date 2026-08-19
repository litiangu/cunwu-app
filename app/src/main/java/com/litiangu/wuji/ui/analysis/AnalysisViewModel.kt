package com.litiangu.wuji.ui.analysis

import androidx.lifecycle.ViewModel
import com.litiangu.wuji.data.AnalysisRepository
import com.litiangu.wuji.data.db.ItemEntity
import com.litiangu.wuji.data.db.ItemStatus
import com.litiangu.wuji.data.db.CategoryEntity
import com.litiangu.wuji.data.db.CategoryBudgetEntity
import com.litiangu.wuji.data.db.toItemInput
import com.litiangu.wuji.domain.computeHealthScore
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

// 分类分布的一个柱：分类名 + 价值合计 + 数量 + 分类 key（驱动分类色）
data class CategoryBarData(
    val label: String,
    val value: Double,
    val count: Int,
    val categoryKey: String = "custom"
)

// 月度趋势的一个点：月份标签 + 当月投入（购入+附加）+ 当月物品净变动数（副柱用）
data class TrendPoint(val label: String, val amount: Double, val itemDelta: Int)

// 分类预算一行：分类名 + 近 30 天支出 + 该类月度限额 + 是否超支（Step 21 L2）
data class CategoryBudgetRow(val label: String, val spend: Double, val limit: Double, val over: Boolean)

// 物品荣誉一项（文档 §2.1）
data class HonorItem(
    val itemId: Long,
    val name: String,
    val imagePath: String? = null,       // 展示态路径（抠图优先），null = 无图占位
    val primaryText: String,             // "390 Days" / "¥23.1/天" / "+¥2,300" / "2026.07"
    val primaryLabel: String,            // "陪伴最久" / "日均价值最高" / "收益最高" / 退化 "最新入手"
)

// 物品荣誉三联（文档 §2.1 三维度评选）
data class Honors(
    val longestCompanion: HonorItem? = null,
    val highestDailyValue: HonorItem? = null,
    val highestRecovery: HonorItem? = null,
)

// 分析页全部展示数据
data class AnalysisUiState(
    val hasItems: Boolean = false,
    val score: Int = 0,                    // 健康分四舍五入整数
    val gradeLabel: String = "",            // 优/良/中/差（首页同源粒度）
    val gradeComment: String = "",          // 少即是多/张弛有度/略有冗余/断舍离时刻（本页评语大字）
    val gradeColorHex: String = "#34C759",  // 圆环/评语文本色（与 HealthGrade.colorHex 同源）
    val d1: Double = 0.0,                   // 利用率
    val d2: Double = 0.0,                   // 价值健康度
    val d3: Double = 0.0,                   // 流转率
    val d4: Double = 0.0,                   // 变现回收
    val totalInput: Double = 0.0,           // 净持有口径：未售出 购入+附加
    val totalRecovery: Double = 0.0,        // 已变现：全部 二手收益
    val netHolding: Double = 0.0,           // 净持有 = 未售出投入 − 全部变现
    val categoryBars: List<CategoryBarData> = emptyList(),
    val monthlyTrend: List<TrendPoint> = emptyList(),
    // ===== Step 21 预算遵从信号（独立，不进健康分）=====
    val monthlyBudget: Double = 0.0,        // 全局月度预算（0 = 未配置）
    val recentSpend30: Double = 0.0,        // 近 30 天支出（购入+附加）
    val categoryBudgetRows: List<CategoryBudgetRow> = emptyList(), // 仅含「已设限额」的分类
    // ===== Step 32 新增 =====
    val itemTotalCount: Int = 0,            // 全物品数（含售出），驱动「N items」徽章
    val honors: Honors = Honors(),          // 物品荣誉三联
    val weakestHint: String = "",           // 行动出口：最弱维度一句话提示（文档 §2.4）
)

/**
 * 分析页 UI State 聚合（Step 32 提取为 file-level 内部函数，供 AnalysisRepository 与 ViewModel 复用）。
 * 数据源：Room（itemDao/categoryDao/categoryBudgetDao）+ DataStore（每日/月度预算锚点 + 隐私模式）。
 */
internal fun buildAnalysisUiState(
    items: List<ItemEntity>,
    categories: List<CategoryEntity>,
    anchor: Double,
    monthlyBudget: Double,
    budgets: List<CategoryBudgetEntity>,
): AnalysisUiState {
    val inputs = items.map { it.toItemInput() }
    val result = computeHealthScore(inputs, anchor)
    val nameById = categories.associateBy { it.id }

    val activeItems = items.filter { it.status != ItemStatus.SOLD }
    val totalInput = activeItems.sumOf { it.purchasePrice + it.extraCost }
    val totalRecovery = items.sumOf { it.resaleValue }

    val bars = items.groupBy { it.categoryId }
        .map { (catId, list) ->
            CategoryBarData(
                label = nameById[catId]?.name ?: "未分类",
                value = list.sumOf { it.purchasePrice + it.extraCost },
                count = list.size,
                categoryKey = nameById[catId]?.colorKey ?: "custom",
            )
        }
        .sortedByDescending { it.value }

    val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    val recentItems = items.filter { it.purchaseDate >= cutoff }
    val recentSpend30 = recentItems.sumOf { it.purchasePrice + it.extraCost }

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
        gradeColorHex = result.colorHex,
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
        categoryBudgetRows = categoryBudgetRows,
        itemTotalCount = items.size,
        honors = buildHonors(items),
        weakestHint = weakestHint(
            result.dimensions.d1Usage,
            result.dimensions.d2ValueHealth,
            result.dimensions.d3Turnover,
            result.dimensions.d4Recovery
        ),
    )
}

// 近 6 个自然月投入趋势（按 purchaseDate 聚合 购入+附加），不足月份补 0。
// 副柱 itemDelta = 当月新增 − 当月售出（售出按 updatedAt 近似落桶；收益不抵扣支出侧，文档 §2.6）
internal fun buildMonthlyTrend(items: List<ItemEntity>): List<TrendPoint> {
    val now = System.currentTimeMillis()
    val months = (0 until 6).map { offset ->
        val c = Calendar.getInstance().apply { timeInMillis = now }
        c.add(Calendar.MONTH, -offset)
        c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
    }.reversed()
    val sums = months.associateWith { 0.0 }.toMutableMap()
    val deltas = months.associateWith { 0 }.toMutableMap()
    items.forEach { item ->
        val c = Calendar.getInstance().apply { timeInMillis = item.purchaseDate }
        val key = c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
        if (key in sums) {
            sums[key] = sums.getValue(key) + item.purchasePrice + item.extraCost
            deltas[key] = deltas.getValue(key) + 1
            val s = Calendar.getInstance().apply { timeInMillis = item.updatedAt }
            val soldKey = s.get(Calendar.YEAR) to (s.get(Calendar.MONTH) + 1)
            if (item.status == ItemStatus.SOLD && soldKey in deltas) {
                deltas[soldKey] = deltas.getValue(soldKey) - 1
            }
        }
    }
    return months.map { (y, m) ->
        TrendPoint(
            label = "${m}月",
            amount = sums[y to m] ?: 0.0,
            itemDelta = deltas[y to m] ?: 0
        )
    }
}

// 物品荣誉三联（文档 §2.1）
internal fun buildHonors(items: List<ItemEntity>): Honors {
    if (items.isEmpty()) return Honors()

    fun daysOf(item: ItemEntity): Int = item.toItemInput().companionshipDays

    val longest: HonorItem? = (
        items.sortedByDescending { daysOf(it) }
            .firstOrNull { displayImagePathOf(it) != null }
            ?: items.maxByOrNull { daysOf(it) }
        )?.let { item ->
            HonorItem(
                itemId = item.id, name = item.name,
                imagePath = displayImagePathOf(item),
                primaryText = "${daysOf(item)} Days",
                primaryLabel = "陪伴最久",
            )
        }

    val highestDaily = items.maxByOrNull { it.purchasePrice / maxOf(1, daysOf(it)) }
        ?.let {
            val daily = it.purchasePrice / maxOf(1, daysOf(it))
            HonorItem(
                itemId = it.id, name = it.name,
                imagePath = displayImagePathOf(it),
                primaryText = "¥%.1f/天".format(daily),
                primaryLabel = "日均价值最高",
            )
        }

    val soldWithGain = items
        .filter { it.status == ItemStatus.SOLD && it.resaleValue > 0 }
        .maxByOrNull { it.resaleValue }
    val highestRecovery = soldWithGain?.let {
        HonorItem(
            itemId = it.id, name = it.name,
            imagePath = displayImagePathOf(it),
            primaryText = "+¥%,.0f".format(it.resaleValue),
            primaryLabel = "收益最高",
        )
    } ?: items.maxByOrNull { it.purchaseDate }?.let {
        val c = Calendar.getInstance().apply { timeInMillis = it.purchaseDate }
        HonorItem(
            itemId = it.id, name = it.name,
            imagePath = displayImagePathOf(it),
            primaryText = "${c.get(Calendar.YEAR)}.${(c.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}",
            primaryLabel = "最新入手",
        )
    }

    return Honors(longest, highestDaily, highestRecovery)
}

internal fun displayImagePathOf(item: ItemEntity): String? =
    if (item.imageDisplayMode == 1 && item.croppedImagePath != null) item.croppedImagePath else item.imagePath

internal fun weakestHint(d1: Double, d2: Double, d3: Double, d4: Double): String {
    if (d1 <= 0 && d2 <= 0 && d3 <= 0 && d4 <= 0) return ""
    val min = minOf(d1, d2, d3, d4)
    return when (min) {
        d1 -> "利用率偏低，建议处理闲置物品"
        d2 -> "持有成本偏高，建议评估高成本闲置物"
        d3 -> "流转率偏低，考虑出售回血"
        else -> "回收率偏低，考虑二手变现"
    }
}

/**
 * 分析页 ViewModel（Step 32 改为 thin wrapper）。
 * 数据来自 AnalysisRepository（与 UserProfile/BudgetAnchor/Backup/Trash 同款预热内存缓存模式），
 * 首帧即真实值，消除「进入分析页跳闪」。
 */
class AnalysisViewModel() : ViewModel() {

    // VM 仅做透传：避免重复 stateIn 造成的订阅重启与首帧延迟
    val uiState: StateFlow<AnalysisUiState> = AnalysisRepository.uiState
    val privacyMode: StateFlow<Boolean> = AnalysisRepository.privacyMode
}
