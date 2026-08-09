package com.litiangu.wuji.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// 分类月度预算的读写（Step 21，L2）。upsert 用 REPLACE 覆盖同分类的限额。
@Dao
interface CategoryBudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CategoryBudgetEntity)

    @Query("SELECT * FROM category_budgets")
    suspend fun getAll(): List<CategoryBudgetEntity>

    @Query("SELECT * FROM category_budgets")
    fun observeAll(): Flow<List<CategoryBudgetEntity>>

    @Query("DELETE FROM category_budgets WHERE categoryId = :categoryId")
    suspend fun delete(categoryId: Long)
}
