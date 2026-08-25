package com.litiangu.wuji.domain

import kotlin.math.roundToInt

/**
 * 健康分计算引擎（v5 模型，「记录哲学」二维重设计，Step 38）。
 *
 * 产品哲学：slogan「存下此刻，物有所值」——注重对物品的记录与陪伴。
 * 物品使用越久、日均持有成本越低、性价比越高；流转/售出只是需求变化、报废、损坏后的
 * 自然生命周期事件，不是健康行为，不参与评分（生命周期事实由分析页资产卡回收率、
 * 最近变动流水作信息展示）。
 *
 * - 公式：score = 0.55·D1 + 0.45·D2
 *   D1 利用率 = 使用中 / (使用中 + 闲置) × 100，已售出物品移出统计池（售出 = 自然离场，
 *   不再拖累在用率）；持有 0 件（全部离场）→ D1 = 100（无闲置即满分）。
 *   D2 价值健康度 = 100 × min(1, 每日预算锚点 / 聚合日均持有成本)，全生命周期口径
 *   （含已售出：长陪伴摊薄日均、正向贡献，正是「用得久 = 有性价比」的表达）。
 * - 历史：v0.3 四维 → v0.4 调权（0.50/0.40/0.05/0.05）→ v5 二维（本版，移除流转率/变现回收）。
 * - 本文件保持纯 Kotlin（不依赖 Android / Room），便于 JVM 单测。
 */

// 单物品精简输入。由 data 层的 ItemEntity.toItemInput() 映射而来，
// domain 不直接依赖数据层，保证引擎可被纯 JUnit 测试覆盖。
data class ItemInput(
    val isInUse: Boolean,        // 状态 = 使用中
    val isSold: Boolean,         // 状态 = 已售出
    val purchasePrice: Double,   // 购入价
    val extraCost: Double,       // 附加费用（配件/维修）
    val resaleValue: Double,     // 二手变现收益
    val companionshipDays: Int   // 陪伴天数 = 今天 - 购买日期（映射器算好传入）
)

// 两个维度子分（均为 0~100）
data class DimensionScores(
    val d1Usage: Double,       // 利用率
    val d2ValueHealth: Double  // 价值健康度
)

// 等级 + 同源派生的评语与色值。
// 色值对齐 Color.kt 评级四色（Step1c 已修撞色：蓝 #0088FF→#0095FF、黄 #FFB300→#FFCC00、红 #BA1A1A→#FF4438），
// 与 03 计划文档里的旧 hex 不同，以应用实际调色板为准。
enum class HealthGrade(
    val label: String,       // 首页等级：优/良/中/差
    val comment: String,     // 分析评语
    val colorHex: String
) {
    EXCELLENT("优", "物尽其用", "#34C759"),
    GOOD("良", "张弛有度", "#0095FF"),
    MEDIUM("中", "略有闲置", "#FFCC00"),
    POOR("差", "该唤醒了", "#FF4438")
}

// 健康分计算结果
data class HealthScoreResult(
    val total: Double,          // 0~100（带小数，便于精确判断）
    val totalRounded: Int,      // 展示用四舍五入整数
    val dimensions: DimensionScores,
    val grade: HealthGrade,
    val comment: String,
    val colorHex: String
)

// 权重（合计 1.0）。v5 二维：利用率 55% / 价值健康度 45%，
// 保持「使用略重于成本」的倾向（v0.4 为 0.50/0.40 的归一化近似）。
private const val W_D1 = 0.55   // 利用率
private const val W_D2 = 0.45   // 价值健康度

/**
 * 维度权重公开常量（Step 37 引入）：供分析页拆解条「贡献分 = 子分 × 权重」展示用。
 * 单点真相：UI 从这里读权重，算法调权时 UI 自动跟随（Step 36 曾因 UI 侧硬编码
 * 过期文档权重导致标注错误，教训见 37_分析页审计修复.md §1）。
 * 顺序：利用率 / 价值健康度，与 DimensionScores 字段一一对应。
 */
val DIMENSION_WEIGHTS = listOf(W_D1, W_D2)

// 预算锚点：默认 ¥3000/月 → 每日 ≈ ¥100（D2 比对用）
const val DEFAULT_DAILY_BUDGET_ANCHOR = 100.0

// 月度预算锚点（L1，Step 21 新增）：默认 0 表示「未配置」，分析页预算卡仅在其 > 0 时渲染，
// 保护零配置中性行为（不污染健康分、不弹无意义的超支预警）。用户配置后由 DataStore 持久化。
const val DEFAULT_MONTHLY_BUDGET_ANCHOR = 0.0

// 等级阈值（分析评语四档；首页等级另用 80/60 双阈，属展示层，不在引擎内）
private const val T_EXCELLENT = 85.0
private const val T_GOOD = 70.0
private const val T_MEDIUM = 50.0

fun gradeFromScore(score: Double): HealthGrade = when {
    score >= T_EXCELLENT -> HealthGrade.EXCELLENT
    score >= T_GOOD -> HealthGrade.GOOD
    score >= T_MEDIUM -> HealthGrade.MEDIUM
    else -> HealthGrade.POOR
}

/**
 * 计算全库健康分。
 * @param items 物品集合（映射后的输入）
 * @param dailyBudgetAnchor 每日预算锚点（元），默认 ¥100
 * @return 健康分结果（总分、二维子分、等级、评语、色值）
 */
fun computeHealthScore(
    items: List<ItemInput>,
    dailyBudgetAnchor: Double = DEFAULT_DAILY_BUDGET_ANCHOR
): HealthScoreResult {
    val total = items.size
    if (total == 0) {
        // 空集合：无数据即视为「无健康可言」，给最低档，不崩。
        return HealthScoreResult(
            total = 0.0,
            totalRounded = 0,
            dimensions = DimensionScores(0.0, 0.0),
            grade = HealthGrade.POOR,
            comment = HealthGrade.POOR.comment,
            colorHex = HealthGrade.POOR.colorHex
        )
    }

    val inUse = items.count { it.isInUse }

    // D1 利用率（v5 口径修正）= 使用中 / (使用中 + 闲置) × 100，已售出移出统计池
    // （售出 = 自然离场，不再永久拖累在用率）；持有 0 件（全部离场）→ 无闲置即满分。
    val owned = items.count { !it.isSold }
    val d1 = if (owned <= 0) 100.0 else inUse.toDouble() / owned * 100.0

    // D2 价值健康度 = 100 × min(1, 每日预算锚点 / 聚合日均持有成本)
    // 聚合日均持有成本 = Σ(购入+附加−收益) / Σ(陪伴天数)，全生命周期口径（含已售出）
    val netSum = items.sumOf { it.purchasePrice + it.extraCost - it.resaleValue }
    val daySum = items.sumOf { it.companionshipDays.toDouble() }
    val d2 = if (daySum <= 0.0 || netSum <= 0.0) {
        // 无持有成本（净投入≤0，如倒卖净赚）或零陪伴天数 → 视为最健康满分
        100.0
    } else {
        val aggDaily = netSum / daySum
        100.0 * minOf(1.0, dailyBudgetAnchor / aggDaily)
    }

    val score = W_D1 * d1 + W_D2 * d2
    val grade = gradeFromScore(score)
    return HealthScoreResult(
        total = score,
        totalRounded = score.roundToInt(),
        dimensions = DimensionScores(d1, d2),
        grade = grade,
        comment = grade.comment,
        colorHex = grade.colorHex
    )
}
