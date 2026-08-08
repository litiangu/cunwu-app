package com.litiangu.wuji.ui.home

import com.litiangu.wuji.data.db.ItemStatus

// 隐私模式开启时，所有金额（购入价/净资产/日均/资产总额）统一显示为脱敏占位符。
const val PRIVACY_MASK = "****"

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
    val categoryKey: String? = null,
    val imagePath: String? = null,        // 原图路径
    val croppedImagePath: String? = null, // 抠图透明 PNG 路径（Step16）
    val imageDisplayMode: Int = 0,       // 0=原图 1=抠图（Step16）
) {
    /**
     * 按展示态选择实际显示的图片路径。
     * 展示态为抠图且存在抠图结果时优先返回透明 PNG，否则返回原图。
     */
    fun displayImagePath(): String? =
        if (imageDisplayMode == 1 && croppedImagePath != null) croppedImagePath else imagePath
}

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