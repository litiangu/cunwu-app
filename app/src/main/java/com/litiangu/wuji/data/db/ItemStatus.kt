package com.litiangu.wuji.data.db

import kotlinx.serialization.Serializable

// 物品状态枚举。数据库以字符串形式存储（转换见 Converters.kt）。
// 对齐《添加页面 + 物品信息表单功能》：使用中 / 未使用 / 已售出
// @Serializable：kotlinx.serialization 按枚举 name（IN_USE 等）序列化，与 DB 存储的 .name 一致。
@Serializable
enum class ItemStatus(val label: String) {
    IN_USE("使用中"),
    UNUSED("未使用"),
    SOLD("已售出")
}
