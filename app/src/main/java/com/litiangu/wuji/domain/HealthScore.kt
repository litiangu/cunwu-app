package com.litiangu.wuji.domain

/**
 * 健康分计算引擎（H2 模型）。
 *
 * 设计要点（来自《健康分加权模型设计草案》v0.3 定稿）：
 * - 健康分是「全库健康度」，四个维度全部是集合级聚合，不是单物品分。
 *   （03 计划文档写的 computeScore(item) 是简化，实际按草案的集合聚合实现。）
 * - 公式：score = 0.40·D1 + 0.30·D2 + 0.15·D3 + 0.15·D4
 *   D3 流转率封顶 50%，D4 变现回收封顶 60%。
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

// 四个维度子分（均为 0~100）
data class DimensionScores(
    val d1Usage: Double,       // 利用率
    val d2ValueHealth: Double, // 价值健康度
    val d3Turnover: Double,    // 流转率
    val d4Recovery: Double     // 变现回收
)

// 等级 + 同源派生的评语与色值。
// 色值对齐 Color.kt 评级四色（Step1c 已修撞色：蓝 #0088FF→#0095FF、黄 #FFB300→#FFCC00、红 #BA1A1A→#FF4438），
// 与 03 计划文档里的旧 hex 不同，以应用实际调色板为准。
enum class HealthGrade(
    val label: String,       // 首页等级：优/良/中/差
    val comment: String,     // 分析评语
    val colorHex: String
) {
    EXCELLENT("优", "少即是多", "#34C759"),
    GOOD("良", "张弛有度", "#0095FF"),
    MEDIUM("中", "略有冗余", "#FFCC00"),
    POOR("差", "断舍离时刻", "#FF4438")
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

// 权重（合计 1.0，已定稿）
private const val W_D1 = 0.40   // 利用率（高）
private const val W_D2 = 0.30   // 价值健康度（中）
private const val W_D3 = 0.15   // 流转率（低）
private const val W_D4 = 0.15   // 变现回收（低）

// 封顶
private const val D3_CAP = 0.50 // 流转率封顶 50%
private const val D4_CAP = 0.60 // 变现回收封顶 60%

// 预算锚点：默认 ¥3000/月 → 每日 ≈ ¥100（D2 比对用）
const val DEFAULT_DAILY_BUDGET_ANCHOR = 100.0

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
 * @return 健康分结果（总分、四维子分、等级、评语、色值）
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
            dimensions = DimensionScores(0.0, 0.0, 0.0, 0.0),
            grade = HealthGrade.POOR,
            comment = HealthGrade.POOR.comment,
            colorHex = HealthGrade.POOR.colorHex
        )
    }

    val inUse = items.count { it.isInUse }
    val sold = items.count { it.isSold }

    // D1 利用率 = 使用中 / 总物品数 × 100（分母含已售出）
    val d1 = inUse.toDouble() / total * 100.0

    // D2 价值健康度 = 100 × min(1, 每日预算锚点 / 聚合日均持有成本)
    // 聚合日均持有成本 = Σ(购入+附加−收益) / Σ(陪伴天数)
    val netSum = items.sumOf { it.purchasePrice + it.extraCost - it.resaleValue }
    val daySum = items.sumOf { it.companionshipDays.toDouble() }
    val d2 = if (daySum <= 0.0 || netSum <= 0.0) {
        // 无持有成本（净投入≤0，如倒卖净赚）或零陪伴天数 → 视为最健康满分
        100.0
    } else {
        val aggDaily = netSum / daySum
        100.0 * minOf(1.0, dailyBudgetAnchor / aggDaily)
    }

    // D3 流转率 = 100 × min(sold/total, 50%)
    val d3 = 100.0 * minOf(sold.toDouble() / total, D3_CAP)

    // D4 变现回收 = 100 × min(max(0, Σ收益) / Σ购入, 60%)
    val purchaseSum = items.sumOf { it.purchasePrice }
    val resaleSum = items.sumOf { it.resaleValue }
    val d4 = if (purchaseSum <= 0.0) {
        // 无购入成本则无可回收基数 → 0（不构成「回收优异」）
        0.0
    } else {
        val recovery = maxOf(0.0, resaleSum) / purchaseSum
        100.0 * minOf(recovery, D4_CAP)
    }

    val score = W_D1 * d1 + W_D2 * d2 + W_D3 * d3 + W_D4 * d4
    val grade = gradeFromScore(score)
    return HealthScoreResult(
        total = score,
        totalRounded = score.roundToInt(),
        dimensions = DimensionScores(d1, d2, d3, d4),
        grade = grade,
        comment = grade.comment,
        colorHex = grade.colorHex
    )
}
