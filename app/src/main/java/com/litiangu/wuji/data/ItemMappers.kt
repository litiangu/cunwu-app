package com.litiangu.wuji.data

import com.litiangu.wuji.data.db.ItemEntity
import com.litiangu.wuji.ui.home.ItemUiModel

fun ItemEntity.toUiModel(): ItemUiModel = ItemUiModel(
    id = id,
    name = name,
    category = "",
    purchaseDate = purchaseDate,
    purchasePrice = purchasePrice,
    addPrice = extraCost,
    soldPrice = resaleValue,
    soldDate = null,
    note = "",
    netAsset = purchasePrice + extraCost - resaleValue,
    healthScore = (healthScore ?: 0).toDouble(),
    companionshipDays = frozenCompanionshipDays(purchaseDate),
)

private fun frozenCompanionshipDays(purchaseDate: Long): Int {
    val days = ((System.currentTimeMillis() - purchaseDate) / 86_400_000L).toInt()
    return days.coerceAtLeast(0)
}

fun List<ItemEntity>.toUiModels(): List<ItemUiModel> = map { it.toUiModel() }
