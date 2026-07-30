package com.litiangu.wuji.data.db

import com.litiangu.wuji.domain.ItemInput

// 一天的毫秒数（用于陪伴天数换算）
private const val DAY_MILLIS = 86_400_000L

/**
 * 把数据库实体映射为健康分引擎的纯输入。
 * 引擎（domain）不直接依赖 Room，这里在 data 层完成转换（单向依赖）。
 *
 * @param todayEpochMillis 当前时间（epoch 毫秒），默认取系统时间；测试可注入固定值。
 */
fun ItemEntity.toItemInput(todayEpochMillis: Long = System.currentTimeMillis()): ItemInput {
    val rawDays = ((todayEpochMillis - purchaseDate) / DAY_MILLIS).toInt()
    return ItemInput(
        isInUse = status == ItemStatus.IN_USE,
        isSold = status == ItemStatus.SOLD,
        purchasePrice = purchasePrice,
        extraCost = extraCost,
        resaleValue = resaleValue,
        // 防 0 天（当天购入）导致 D2 除零；至少计 1 天
        companionshipDays = maxOf(1, rawDays)
    )
}
