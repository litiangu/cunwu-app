package com.litiangu.wuji.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

// 回收站（软删除）表：物品被首页右滑删除后，完整快照落此表，30 天内可恢复。
// 字段镜像 ItemEntity（去掉自增 id，改用 originalId 保留原物品 id），加 deletedAt 记录删除时间。
// 恢复时由 toItem() 还原为 ItemEntity 写回物品表（沿用 originalId）。
// 注：本实现以列镜像存快照（非 JSON 字符串），避免引入额外序列化依赖；语义等价于《设置-最近删除》文档的「物品快照」。
@Entity(tableName = "trash")
data class TrashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalId: Long,
    val name: String,
    val categoryId: Long? = null,
    val status: ItemStatus,
    val purchaseDate: Long,
    val purchasePrice: Double,
    val extraCost: Double = 0.0,
    val resaleValue: Double = 0.0,
    val imagePath: String? = null,
    val croppedImagePath: String? = null,
    val healthScore: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long
)

// 物品实体 → 回收站快照（删除当下）
fun ItemEntity.toTrash(deletedAt: Long): TrashEntity = TrashEntity(
    originalId = id,
    name = name,
    categoryId = categoryId,
    status = status,
    purchaseDate = purchaseDate,
    purchasePrice = purchasePrice,
    extraCost = extraCost,
    resaleValue = resaleValue,
    imagePath = imagePath,
    croppedImagePath = croppedImagePath,
    healthScore = healthScore,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

// 回收站快照 → 物品实体（恢复时）
fun TrashEntity.toItem(): ItemEntity = ItemEntity(
    id = originalId,
    name = name,
    categoryId = categoryId,
    status = status,
    purchaseDate = purchaseDate,
    purchasePrice = purchasePrice,
    extraCost = extraCost,
    resaleValue = resaleValue,
    imagePath = imagePath,
    croppedImagePath = croppedImagePath,
    healthScore = healthScore,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
