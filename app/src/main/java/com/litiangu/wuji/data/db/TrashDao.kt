package com.litiangu.wuji.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// 回收站（软删除）的增查删。
// 列表按删除时间倒序；30 天清理由 purgeOlderThan(cutoff) 执行；恢复/清空见《设置-最近删除》。
@Dao
interface TrashDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: TrashEntity): Long

    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    suspend fun getAll(): List<TrashEntity>

    @Query("SELECT * FROM trash WHERE originalId = :id")
    suspend fun getByOriginalId(id: Long): TrashEntity?

    @Query("DELETE FROM trash WHERE originalId = :id")
    suspend fun deleteByOriginalId(id: Long)

    @Query("DELETE FROM trash")
    suspend fun clear()

    // 物理清除删除时间早于 cutoff（30 天）的记录（惰性清理，不提示）
    @Query("DELETE FROM trash WHERE deletedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)
}
