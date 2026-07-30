package com.litiangu.wuji.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// 物品表。字段对齐《添加页面 + 物品信息表单功能》与《健康分加权模型设计草案》。
// categoryId 可空：未分类物品 = null（删除分类时置空，不级联删物品）。
// purchaseDate 存 epoch 毫秒；陪伴天数 = 现在 - purchaseDate。
// healthScore 为健康分快照（可空）：Step3 计算引擎写入，当前仅占位。
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
    val healthScore: Int? = null,   // 健康分快照（Step3 填充）
    val createdAt: Long,
    val updatedAt: Long
)
