package com.litiangu.wuji.data.db

// 物品状态枚举。数据库以字符串形式存储（转换见 Converters.kt）。
// 对齐《添加页面 + 物品信息表单功能》：使用中 / 未使用 / 已售出
enum class ItemStatus(val label: String) {
    IN_USE("使用中"),
    UNUSED("未使用"),
    SOLD("已售出")
}
