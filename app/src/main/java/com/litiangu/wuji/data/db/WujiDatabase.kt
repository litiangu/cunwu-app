package com.litiangu.wuji.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 数据库定义：items / categories / trash 三表。
// 版本 2：新增 trash（软删除回收站）表，Migration(1,2) 仅建表不清库。
// @TypeConverters 让全局能用 Converters（枚举 <-> 字符串）。
@Database(
    entities = [ItemEntity::class, CategoryEntity::class, TrashEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WujiDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun categoryDao(): CategoryDao
    abstract fun trashDao(): TrashDao

    companion object {
        const val DATABASE_NAME = "wuji.db"

        // 版本 1 → 2：仅新建 trash 表，保留既有 items / categories 数据。
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `trash` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `originalId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `categoryId` INTEGER,
                        `status` TEXT NOT NULL,
                        `purchaseDate` INTEGER NOT NULL,
                        `purchasePrice` REAL NOT NULL,
                        `extraCost` REAL NOT NULL,
                        `resaleValue` REAL NOT NULL,
                        `imagePath` TEXT,
                        `croppedImagePath` TEXT,
                        `healthScore` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
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
                .fallbackToDestructiveMigration(true) // 无对应 Migration 时兜底清库（开发期可接受）
                .addMigrations(WujiDatabase.MIGRATION_1_2) // 1→2 仅建 trash 表，保留旧数据
                .build()
                .also { INSTANCE = it }
                .also {
                    // 后台播种（不阻塞调用方）；Step4 首次读取前可 await seedIfEmpty()
                    CoroutineScope(Dispatchers.IO).launch { it.seedIfEmpty() }
                }
        }
    }
}
