package com.litiangu.wuji.ui.home

import com.litiangu.wuji.data.db.ItemStatus
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.round

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
    val companionshipDays: Int,
    val status: ItemStatus,
    val categoryKey: String? = null,
    val imagePath: String? = null,        // 原图路径
    val croppedImagePath: String? = null, // 抠图透明 PNG 路径（Step16）
    val imageDisplayMode: Int = 0,       // 0=原图 1=抠图（Step16）
    // Step 40：图片宽高比（宽 ÷ 高），由 HomeViewModel 在流里读文件头算好。
    // 瀑布流（Step 42）布局前就要知道高度，Composable 内再解码会首帧跳变。
    // 无图 / 解析失败统一 1.0f（正方形），模式 A / C 不使用。
    val imageAspectRatio: Float = 1.0f,
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
    /** 当前净资产 − 上月末净资产；空库为 null。已按分位四舍五入。 */
    val monthlyDelta: Double? = null,
    /** 上月末净资产，按当前物品数据回算；用于判断是否首月。 */
    val lastMonthNetAsset: Double = 0.0,
)

/**
 * 从物品列表算出首页资产概览。
 * - 总购入 / 总附加 / 总变现 / 净资产：全物品聚合（Σ(购入+附加) − Σ(变现)）
 * - 使用率：非售出物品占比（占位，可后续替换为「正在使用天数 / 总陪伴天数」语义）
 * - 消费健康等级：[修复07] 起由外部传入（HomeViewModel 实时调 v5 健康分引擎，与分析页
 *   同源四档 优/良/中/差，空库 "—"）。原实现平均 ItemUiModel.healthScore 快照，
 *   而该快照列从未被写入（恒 0），导致首页恒显「差」、与分析页实时分数矛盾。
 * - 月度变化：当前净资产 − 上月末净资产。上月末按当前数据回算，
 *   purchaseDate 不晚于上月末的计入，soldDate 不晚于上月末的扣除变现金额。
 *   回算而不是存快照，避免新增一张历史表；代价是改价/删除会同时改写历史值，
 *   但这也保证改旧物价格不会凭空产生月度差额。
 *
 * @param now 计算月份边界的基准时间，默认当前时间，便于单元测试注入固定时刻。
 */
fun List<ItemUiModel>.toOverview(
    healthLabel: String,
    now: Long = System.currentTimeMillis(),
): HomeOverview {
    val totalPurchase = sumOf { it.purchasePrice }
    val totalAdd = sumOf { it.addPrice }
    val totalSold = sumOf { it.soldPrice }
    val netAsset = totalPurchase + totalAdd - totalSold
    val useRatePercent = if (isEmpty()) 0.0 else (count { it.soldDate == null }.toDouble() / size) * 100.0
    val useStateLabel = if (isEmpty()) "暂无数据" else "正在使用"

    // 月份按设备本地时区切分：本月 1 日 00:00 的前 1 毫秒即上月末。
    val zone = ZoneId.systemDefault()
    val monthStart = Instant.ofEpochMilli(now)
        .atZone(zone)
        .toLocalDate()
        .withDayOfMonth(1)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
    val lastMonthEnd = monthStart - 1
    val lastMonthNetAsset = filter { it.purchaseDate <= lastMonthEnd }
        .sumOf { item ->
            val soldBeforeLastMonthEnd =
                item.soldDate != null && item.soldDate <= lastMonthEnd
            item.purchasePrice + item.addPrice -
                if (soldBeforeLastMonthEnd) item.soldPrice else 0.0
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
        monthlyDelta = if (isEmpty()) null else roundToCents(netAsset - lastMonthNetAsset),
        lastMonthNetAsset = lastMonthNetAsset,
    )
}

/** 差额按分位四舍五入，避免 0.0000001 被显示成 +0.00。 */
private fun roundToCents(value: Double): Double = round(value * 100.0) / 100.0

/** 月度变化是否小到可以视为持平。 */
fun isMonthlyFlat(delta: Double?): Boolean = delta != null && abs(delta) < 0.005