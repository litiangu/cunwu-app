package com.litiangu.wuji.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litiangu.wuji.data.db.ItemDao
import com.litiangu.wuji.data.db.TrashDao
import com.litiangu.wuji.data.db.TrashEntity
import com.litiangu.wuji.data.db.toItem
import com.litiangu.wuji.ui.item.deleteImagePath
import kotlinx.coroutines.launch

/**
 * 最近删除（回收站）页视图模型。
 *
 * 仅提供三类动作（读取已下沉到 TrashRepository 内存缓存，本 VM 不持有流）：
 * - 恢复：物品快照写回物品表（沿用 originalId）→ 再移除回收站快照。两条顺序执行保证原子。
 * - 永久删除单个：移除回收站快照 + 顺手删私有图文件（原图 + 抠图 PNG），避免孤儿文件。
 * - 全部清空：先取出全部快照删图文件，再清空回收站表。
 *
 * 语义与 NextStep §3.9 对齐：本步只做「列表 + 恢复 + 永久删除 + 全部清空」，
 * 30 天自动清理（purgeOlderThan）是惰性后台行为，不在本步 UI 范畴。
 */
class TrashViewModel(
    private val itemDao: ItemDao,
    private val trashDao: TrashDao,
) : ViewModel() {

    // 恢复：先回物品表（沿用 originalId），再删回收站快照。
    fun restore(entity: TrashEntity) {
        viewModelScope.launch {
            itemDao.insert(entity.toItem())
            trashDao.deleteByOriginalId(entity.originalId)
        }
    }

    // 永久删除单个：删回收站快照 + 删私有图文件（原图 + 抠图 PNG）。
    fun deletePermanently(entity: TrashEntity) {
        viewModelScope.launch {
            trashDao.deleteByOriginalId(entity.originalId)
            deleteImagePath(entity.imagePath)
            deleteImagePath(entity.croppedImagePath)
        }
    }

    // 全部清空：先取全部快照删图文件，再清空回收站表。
    fun clearAll() {
        viewModelScope.launch {
            val all = trashDao.getAll()
            all.forEach {
                deleteImagePath(it.imagePath)
                deleteImagePath(it.croppedImagePath)
            }
            trashDao.clear()
        }
    }
}
