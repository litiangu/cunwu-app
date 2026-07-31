package com.litiangu.wuji.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 数据库定义：包含两张表 items / categories，版本 1。
// @TypeConverters 让全局能用 Converters（枚举 <-> 字符串）。
@Database(
    entities = [ItemEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WujiDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        const val DATABASE_NAME = "wuji.db"
    }
}

// 7 个预置分类（颜色键对齐 ui.theme.Color.kt 的 CategoryPalette）。
// 顺序即筛选栏展示顺序；用户自定义分类追加在后。
private val SEED_CATEGORIES = listOf(
    CategoryEntity(name = "数码", colorKey = "digital", isPreset = true, sortOrder = 0),
    CategoryEntity(name = "电器", colorKey = "appliance", isPreset = true, sortOrder = 1),
    CategoryEntity(name = "家具", colorKey = "furniture", isPreset = true, sortOrder = 2),
    CategoryEntity(name = "工具", colorKey = "tool", isPreset = true, sortOrder = 3),
    CategoryEntity(name = "运动", colorKey = "sport", isPreset = true, sortOrder = 4),
    CategoryEntity(name = "交通", colorKey = "traffic", isPreset = true, sortOrder = 5),
    CategoryEntity(name = "自定义", colorKey = "custom", isPreset = true, sortOrder = 6)
)

// 首次建库后写入预置分类（仅在确实为空时）。
suspend fun WujiDatabase.seedIfEmpty() {
    if (categoryDao().count() == 0) {
        categoryDao().insertAll(SEED_CATEGORIES)
    }
}

// 单例获取数据库。首个创建出来的实例会在后台播种预置分类。
object WujiDatabaseProvider {
    @Volatile
    private var INSTANCE: WujiDatabase? = null

    fun get(context: Context): WujiDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                WujiDatabase::class.java,
                WujiDatabase.DATABASE_NAME
            )
                .fallbackToDestructiveMigration(true) // Step2 暂无迁移；版本升级时在此加 Migration（dropAllTables=true 开发期可接受，首次改表会清空数据）
                .build()
                .also { INSTANCE = it }
                .also {
                    // 后台播种（不阻塞调用方）；Step4 首次读取前可 await seedIfEmpty()
                    CoroutineScope(Dispatchers.IO).launch { it.seedIfEmpty() }
                }
        }
    }
}
