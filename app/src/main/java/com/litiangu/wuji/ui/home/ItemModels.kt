package com.litiangu.wuji.ui.home

import com.litiangu.wuji.data.db.ItemStatus

data class ItemUiModel(
    val id: Long,
    val name: String,
    val category: String,
    val purchaseDate: Long,
    val purchasePrice: Double,
    val addPrice: Double,
    val soldPrice: Double,
    val soldDate: Long?,
    val note: String,
    val netAsset: Double,
    val healthScore: Double,
    val companionshipDays: Int,
    val status: ItemStatus,
    val categoryKey: String? = null
)

data class HomeOverview(
    val totalPurchase: Double,
    val totalAdd: Double,
    val totalSold: Double,
    val netAsset: Double,
    val totalCount: Int,
    val useRatePercent: Double,
    val useStateLabel: String,
    val healthLabel: String,
)

/**
 * 从物品列表算出首页资产概览。
 * - 总购入 / 总附加 / 总变现 / 净资产：全物品聚合（Σ(购入+附加) − Σ(变现)）
 * - 使用率：非售出物品占比（占位，可后续替换为「正在使用天数 / 总陪伴天数」语义）
 * - 消费健康等级：80/60 双阈映射三态（≥80 优 / 60-79 良 / <60 中差），空库显示 "—"
 */
fun List<ItemUiModel>.toOverview(): HomeOverview {
    val totalPurchase = sumOf { it.purchasePrice }
    val totalAdd = sumOf { it.addPrice }
    val totalSold = sumOf { it.soldPrice }
    val netAsset = totalPurchase + totalAdd - totalSold
    val useRatePercent = if (isEmpty()) 0.0 else (count { it.soldDate == null }.toDouble() / size) * 100.0
    val useStateLabel = if (isEmpty()) "暂无数据" else "正在使用"
    val avgHealth = if (isEmpty()) 0.0 else sumOf { it.healthScore } / size
    val healthLabel = when {
        isEmpty() -> "—"
        avgHealth >= 80.0 -> "优"
        avgHealth >= 60.0 -> "良"
        else -> "差"
    }
    return HomeOverview(
        totalPurchase = totalPurchase,
        totalAdd = totalAdd,
        totalSold = totalSold,
        netAsset = netAsset,
        totalCount = size,
        useRatePercent = useRatePercent,
        useStateLabel = useStateLabel,
        healthLabel = healthLabel,
    )
}