package com.litiangu.wuji.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// 物品的增删改查、按分类筛选、按价值（购入价+附加）排序。
@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Delete
    suspend fun delete(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    suspend fun getAll(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE categoryId = :categoryId ORDER BY createdAt DESC")
    fun observeByCategory(categoryId: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE categoryId = :categoryId ORDER BY createdAt DESC")
    suspend fun getByCategory(categoryId: Long): List<ItemEntity>

    @Query("SELECT * FROM items WHERE categoryId IS NULL ORDER BY createdAt DESC")
    suspend fun getUncategorized(): List<ItemEntity>

    // 按价值排序（购入价 + 附加费用），供首页筛选与排序弹窗使用
    @Query("SELECT * FROM items ORDER BY (purchasePrice + extraCost) DESC")
    suspend fun getAllByValueDesc(): List<ItemEntity>
}
