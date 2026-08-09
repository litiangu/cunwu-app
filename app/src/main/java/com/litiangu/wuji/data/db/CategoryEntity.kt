package com.litiangu.wuji.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// 分类表。colorKey 指向 ui.theme.Color.kt 的 CategoryPalette 颜色键，
// 保证色点单一事实源（禁止在 UI 硬编码颜色）。
// 对齐《首页页面 + 分类筛选栏功能》：7 个预置分类，固定顺序 sortOrder。
@Entity(tableName = "categories")
@Serializable
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorKey: String,        // digital/appliance/furniture/tool/sport/traffic/custom
    val isPreset: Boolean = false,
    val sortOrder: Int = 0
)
