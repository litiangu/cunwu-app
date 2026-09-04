package com.litiangu.wuji.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.graphics.BitmapFactory
import com.litiangu.wuji.data.db.ItemDao
import com.litiangu.wuji.data.db.CategoryDao
import com.litiangu.wuji.data.db.ItemStatus
import com.litiangu.wuji.data.db.TrashDao
import com.litiangu.wuji.data.db.toItemInput
import com.litiangu.wuji.data.db.toTrash
import com.litiangu.wuji.data.toUiModel
import com.litiangu.wuji.domain.computeHealthScore
import com.litiangu.wuji.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview

/**
 * 首页视图模型：持有物品流、分类流，以及 Step 10 的筛选/搜索/视图状态。
 *
 * 状态分两类：
 * - 持久化：视图密度 viewMode（DataStore）。
 * - 会话内存：分类 chip（selectedColorKey）、搜索（searchQuery/Keyword）、
 *   筛选与排序（sortKey/sortOrder/statusFilter）。其中筛选与排序条件仅会话内存，
 *   重启恢复默认（购买日期降序 / 无状态过滤），不落库，避免用户困惑。
 */
class HomeViewModel(
    private val itemDao: ItemDao,
    private val categoryDao: CategoryDao,
    private val trashDao: TrashDao,
    private val context: Context
) : ViewModel() {

    private val settings = SettingsDataStore(context)

    // 分类筛选（chip，按 colorKey；null = 全部）
    private val _selectedColorKey = MutableStateFlow<String?>(null)
    val selectedColorKey = _selectedColorKey.asStateFlow()
    fun selectColorKey(key: String?) { _selectedColorKey.value = key }

    // 搜索：_searchQuery 即时回显输入框；searchKeyword 经 300ms 防抖 + trim 后驱动过滤
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    fun setSearchQuery(q: String) { _searchQuery.value = q }
    @OptIn(FlowPreview::class)
    val searchKeyword: StateFlow<String> = _searchQuery
        .debounce(300)
        .map { it.trim() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // 视图三态（持久化 DataStore）：Step 40 起对外是枚举 HomeViewMode，磁盘仍存老字符串。
    // 由顶栏下拉菜单显式选择触发，参考 3.5 视图切换按钮状态页面。
    val viewMode: StateFlow<HomeViewMode> = settings.viewModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeViewMode.DEFAULT)
    fun setViewMode(mode: HomeViewMode) {
        viewModelScope.launch { settings.setViewMode(mode) }
    }

    // 筛选与排序生效态（仅会话内存，默认 购买日期降序 / 无状态过滤）
    private val _sortKey = MutableStateFlow("date")      // "date" | "price"
    private val _sortOrder = MutableStateFlow("desc")   // "asc" | "desc"
    private val _statusFilter = MutableStateFlow<Set<ItemStatus>>(emptySet())
    val sortKey = _sortKey.asStateFlow()
    val sortOrder = _sortOrder.asStateFlow()
    val statusFilter = _statusFilter.asStateFlow()

    // 弹窗「确定」：草稿态提交为生效态
    fun applyFilter(key: String, order: String, status: Set<ItemStatus>) {
        _sortKey.value = key
        _sortOrder.value = order
        _statusFilter.value = status
    }

    // 生效态是否偏离默认（用于 ≡ 按钮橙点）
    fun isFilterActive(): Boolean =
        _statusFilter.value.isNotEmpty() || _sortKey.value != "date" || _sortOrder.value != "desc"

    val categories = categoryDao.observeAll()

    // 数据首读完成标记：items 初始值为 emptyList()，Room 首查返回后置 true。
    // 首页据此在「数据就绪前」渲染中性背景，避免空状态页 / 零值资产卡闪现（见 HomeScreen）。
    private val _itemsLoaded = MutableStateFlow(false)
    val itemsLoaded: StateFlow<Boolean> = _itemsLoaded.asStateFlow()

    // imageAspectRatio（Step 40）：瀑布流（Step 42）要在布局前就知道图片比例，
    // 否则首帧统一按 1.0f 画、测完再跳变，视觉抖动。这里在流里用 inJustDecodeBounds
    // 只读文件头（不整图解码）算好，Composable 内只读字段、零 IO。
    // flowOn(IO)：解码与 toUiModel 随之上 IO 线程，主线程只做最后的 stateIn 收集。
    val items: StateFlow<List<ItemUiModel>> = combine(
        itemDao.observeAll(),
        categoryDao.observeAll(),
    ) { entities, cats ->
        val keyById = cats.associate { it.id to it.colorKey }
        entities.map {
            val model = it.toUiModel().copy(categoryKey = it.categoryId?.let { id -> keyById[id] })
            model.copy(imageAspectRatio = readAspectRatio(model.displayImagePath()))
        }
    }
        .onEach { _itemsLoaded.value = true }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // [修复07] 消费健康等级：与分析页同源，实时调 v5 健康分引擎（computeHealthScore）。
    // 原实现平均 ItemUiModel.healthScore，而该值来自 DB 快照列 healthScore——此列从未被
    // 写入（保存物品时固定 null），恒 0 分导致首页恒显「差」，与分析页实时 100 分互相矛盾。
    // 锚点读同一 DataStore 流（budgetAnchorFlow），自定义每日预算后两页保持一致；空库显示 "—"。
    val healthLabel: StateFlow<String> = combine(
        itemDao.observeAll(),
        settings.budgetAnchorFlow,
    ) { entities, anchor ->
        if (entities.isEmpty()) "—"
        else computeHealthScore(entities.map { it.toItemInput() }, anchor).grade.label
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "—")

    val privacyMode = settings.privacyModeFlow
    val darkMode = settings.darkModeFlow

    // Step 29：首页备份阈值提示所需的三流（复用于首页 banner）。
    // 直接暴露原始 Flow，照搬 privacyMode 套路（不在 VM 内塞默认 StateFlow），
    // 由 HomeScreen 收集时用默认值闸门，避免首帧横幅闪现。
    val lastBackupAt = settings.lastBackupAtFlow          // 上次成功备份时间戳（0 = 从未）
    val backupReminderOn = settings.backupReminderOnFlow  // 备份提醒开关（默认开）
    val backupReminderThresholdDays = settings.backupReminderThresholdDaysFlow  // 阈值天数（默认 14）

    fun togglePrivacyMode() {
        viewModelScope.launch {
            val current = settings.privacyModeFlow.first()
            settings.setPrivacyMode(!current)
        }
    }

    // 软删除：读原实体 → 快照写入 trash 表 → 从物品表移除（首页/概览/分析自动更新）
    fun softDelete(item: ItemUiModel) {
        viewModelScope.launch {
            val entity = itemDao.getById(item.id) ?: return@launch
            trashDao.insert(entity.toTrash(System.currentTimeMillis()))
            itemDao.deleteById(item.id)
        }
    }
}

/**
 * 只读图片文件头取宽高比（宽 ÷ 高）。
 *
 * `inJustDecodeBounds = true` 时 BitmapFactory 只解析尺寸、不分配像素内存，开销极小，
 * 所以可以在流里对每个物品都算一遍。无图 / 文件缺失 / 解析失败一律回退 1.0f（正方形），
 * 保证瀑布流永远拿得到一个可用比例，不会因为脏数据算出 0 或 NaN 把布局撑坏。
 */
private fun readAspectRatio(path: String?): Float {
    if (path.isNullOrBlank()) return 1.0f
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, opts)
    val w = opts.outWidth
    val h = opts.outHeight
    return if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 1.0f
}
