package com.litiangu.wuji.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.litiangu.wuji.data.db.ItemDao
import com.litiangu.wuji.data.db.CategoryDao
import com.litiangu.wuji.data.db.ItemStatus
import com.litiangu.wuji.data.toUiModel
import com.litiangu.wuji.data.settings.SettingsDataStore
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

    // 视图密度三态（持久化 DataStore）：list-default / card-2x2 / list-compact。
    // 由顶栏下拉菜单显式选择触发，参考 3.5 视图切换按钮状态页面。
    val viewMode: StateFlow<String> = settings.viewModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "list-default")
    fun setViewMode(mode: String) {
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

    val items: StateFlow<List<ItemUiModel>> = combine(
        itemDao.observeAll(),
        categoryDao.observeAll(),
    ) { entities, cats ->
        val keyById = cats.associate { it.id to it.colorKey }
        entities.map { it.toUiModel().copy(categoryKey = it.categoryId?.let { id -> keyById[id] }) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val privacyMode = settings.privacyModeFlow
    val darkMode = settings.darkModeFlow

    fun togglePrivacyMode() {
        viewModelScope.launch {
            val current = settings.privacyModeFlow.first()
            settings.setPrivacyMode(!current)
        }
    }
}
