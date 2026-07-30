package com.litiangu.wuji.data.db

import androidx.room.TypeConverter

// Room 只能直接保存基础类型。这里教 Room 如何在 ItemStatus 枚举
// 与字符串之间互相转换，status 字段才能写进数据库。
class Converters {
    @TypeConverter
    fun fromStatus(status: ItemStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): ItemStatus = ItemStatus.valueOf(value)
}
