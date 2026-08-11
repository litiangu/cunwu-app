package com.litiangu.wuji.data.db

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 最近删除（回收站）内存缓存（消除进入页时的加载圈与默认闪跳，对齐 Step 25 的
 * UserProfileRepository / 预算锚点的 BudgetAnchorRepository / 数据备份的 BackupRepository）。
 *
 * 问题：回收站列表来自 Room 流，原 `TrashScreen` 用 `produceState` 等首查 + 加载圈门控，
 * 进入页面先转圈再出列表，违背「点击即现」的交互预期。
 *
 * 解决：应用预热期把当前值同步读进内存 StateFlow，UI 首帧即真实值，无需 loading；
 * 恢复/永久删除/全部清空仍走 Room（经由 TrashViewModel 动作函数），StateFlow 通过订阅自动同步。
 *
 * 仅做读取预热；恢复/删除/清空等带副作用的动作留在 `TrashViewModel`（其依赖 ui.item.deleteImagePath，
 * 不应下沉到 data 层，避免 data → ui 反向依赖）。
 */
object TrashRepository {
    private lateinit var db: WujiDatabase

    private val _items = MutableStateFlow<List<TrashEntity>>(emptyList())
    val items: StateFlow<List<TrashEntity>> = _items

    private val _categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _categories

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 在 Application 预热协程中调用（isReady 置 true 之前）。
     * 同步读取一次当前值写入 StateFlow，再启动后台订阅持续同步。
     */
    suspend fun init(database: WujiDatabase) {
        if (::db.isInitialized) return
        db = database

        // 先同步把当前持久值读到内存，保证 UI 首帧正确。
        _items.value = db.trashDao().observeAll().first()
        _categories.value = db.categoryDao().observeAll().first()

        // 后续变化自动同步到 StateFlow。
        scope.launch { db.trashDao().observeAll().collect { _items.value = it } }
        scope.launch { db.categoryDao().observeAll().collect { _categories.value = it } }
    }
}
