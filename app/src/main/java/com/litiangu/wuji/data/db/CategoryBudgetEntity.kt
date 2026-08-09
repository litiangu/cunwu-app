package com.litiangu.wuji.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// 分类月度预算（Step 21，L2）。分类预算按分类记录「每月支出上限」。
// 与 categories 表通过 categoryId 关联（不建外键，避免删分类时连带删预算配置）。
// monthlyLimit = 0 视为「未配置该类限额」，分析页回落到全局月度预算比对。
@Entity(tableName = "category_budgets")
@Serializable
data class CategoryBudgetEntity(
    @PrimaryKey val categoryId: Long,
    val monthlyLimit: Double
)
