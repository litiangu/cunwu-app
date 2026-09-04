package com.litiangu.wuji.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// 物品表。字段对齐《添加页面 + 物品信息表单功能》与《健康分加权模型设计草案》。
// categoryId 可空：未分类物品 = null（删除分类时置空，不级联删物品）。
// purchaseDate 存 epoch 毫秒；陪伴天数 = 现在 - purchaseDate。
// healthScore 健康分快照列：已弃用（修复07）——首页/分析页均走 domain 引擎实时计算，保存时固定写 null。
@Entity(
    tableName = "items",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("categoryId")]
)
@Serializable
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long? = null,
    val status: ItemStatus,
    val purchaseDate: Long,        // epoch millis
    val purchasePrice: Double,      // 购入价
    val extraCost: Double = 0.0,    // 附加费用（配件/维修）
    val resaleValue: Double = 0.0,  // 二手变现收益
    val imagePath: String? = null,
    val croppedImagePath: String? = null,
    val imageDisplayMode: Int = 0,    // 图片展示态：0=原图 1=抠图（Step16）
    val healthScore: Int? = null,   // 健康分快照列，已弃用（修复07）：恒为 null，等级改由引擎实时计算
    // 修复01：附加费用 / 收益金额子项 JSON 序列化（kotlinx.serialization），保存用户改过的标签/金额。
    // 默认 "[]" = 无子项，进入编辑页时 VM init 走默认项回退（买配件/维修费用/二手变现）。
    val extraItemsJson: String = "[]",
    val resaleItemsJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long
)
