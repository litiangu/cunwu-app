package com.litiangu.wuji.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.data.db.CategoryEntity
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.litiangu.wuji.ui.designsystem.CircleIconButton
import com.litiangu.wuji.ui.designsystem.TimelineRow
import com.litiangu.wuji.ui.designsystem.bottomBarInsetDp
import com.litiangu.wuji.ui.home.CategoryFilterBar
import com.litiangu.wuji.ui.onboarding.AppSplash
import com.litiangu.wuji.ui.theme.BrandOrange
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSize
import com.litiangu.wuji.ui.theme.WujiSpace

/**
 * 首页常量：一天毫秒数（Step 29 备份阈值天数换算用）。
 */
private const val DAY_MS = 86_400_000L

// Step 42：月份分桶 / 组头文案 / 当日判定助手。仅组合期（主线程）调用，SimpleDateFormat 套路同 TimelineRow。
private val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.CHINA)
private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
private val monthNameFormat = SimpleDateFormat("M月", Locale.CHINA)
private val yearFormat = SimpleDateFormat("yyyy", Locale.CHINA)

private fun monthKeyOf(epochMillis: Long): String = monthKeyFormat.format(Date(epochMillis))

private fun dayKeyOf(epochMillis: Long): String = dayKeyFormat.format(Date(epochMillis))

private fun monthNameOf(epochMillis: Long): String = monthNameFormat.format(Date(epochMillis))

private fun yearOf(epochMillis: Long): String = yearFormat.format(Date(epochMillis))

/** 模式 A 组内行条目：isFirstOfDay = 同日首行（左栏显示日期锚与节点，后续行留空、线延续） */
private data class TimelineEntry(val item: ItemUiModel, val isFirstOfDay: Boolean)

/**
 * 首页备份提示行自动消失时长（Step 29）：超期提示显示后停留 30s 自动消失，本会话不再打扰。
 */
private const val BACKUP_REMINDER_AUTO_HIDE_MS = 30_000L

/**
 * 首页：与 `stitch_app_settings_page_redesign/3.0应用主页-显示添加物品后的页面/screen.png` 1:1 对齐。
 *
 * 顶栏：「你的物品」+ 三圆钮。
 * - 列表切换：点击在按钮处弹出 DropdownMenu 三选项（时间轴 / 瀑布流 / 陈列馆），
 *   参考 3.5 视图切换按钮状态页面，显式选择触发 HomeViewModel.setViewMode，持久化。
 * - 搜索：点击展开内联输入框，300ms 防抖按名称模糊匹配（不区分大小写）。
 *   激活时左侧导航位空，× 取消按钮置于右侧 actions。
 * - 设置：跳设置页。
 * 分类 chips + 末尾 ≡ 接筛选排序弹窗（含激活橙点）。
 * 底栏 pill（首页/+ /分析），分析 tab 由 WujiApp 接 AnalysisScreen。
 * 点击物品：弹出 ItemDetailOverlay。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onNew: () -> Unit = {},
    onSetting: () -> Unit = {},
    onEdit: (Long) -> Unit = {},
    onBackup: () -> Unit = {},
    // [Step 55] 把「列表是否已在顶部」上报给根层，供返回栈判断下一步动作。
    // 三视图各用不同的滚动容器（LazyColumn / LazyStaggeredGrid / LazyVerticalGrid），
    // 根层拿不到 state 对象，只能由本页算出布尔值往外报。
    onScrollStateChanged: (Boolean) -> Unit = {},
    // [Step 55] 回顶信号。根层收到系统返回时自增，本页用 LaunchedEffect 侦听后
    // 调各自的 animateScrollToItem(0)。比根层直接持有 state 更干净：
    // 切换 viewMode 时 state 对象会变，信号机制不受牵连。
    scrollTopSignal: Int = 0,
) {
    val items by viewModel.items.collectAsStateWithLifecycle(emptyList())
    val itemsLoaded by viewModel.itemsLoaded.collectAsStateWithLifecycle(false)
    val categories by viewModel.categories.collectAsStateWithLifecycle(emptyList())
    val selectedColorKey by viewModel.selectedColorKey.collectAsStateWithLifecycle(null)
    // StateFlow 首帧收集用无参版（有参版会先出一帧 initialValue 再跳到真实值）
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle("")
    val searchKeyword by viewModel.searchKeyword.collectAsStateWithLifecycle("")
    val sortKey by viewModel.sortKey.collectAsStateWithLifecycle("date")
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle("desc")
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle(emptySet())
    // 隐私模式：开关在设置页，存储于同一 DataStore；此处收集后透传给各金额渲染处做脱敏。
    val privacyMasked by viewModel.privacyMode.collectAsStateWithLifecycle(false)

    // Step 29：备份阈值提示。三流默认初值取与 DataStore 一致语义；
    // reminderOn 默认 false，配合下方 itemsLoaded 闸门，杜绝首帧横幅「显示→消失」闪现。
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle(0L)
    val backupReminderOn by viewModel.backupReminderOn.collectAsStateWithLifecycle(false)
    val backupReminderThresholdDays by viewModel.backupReminderThresholdDays.collectAsStateWithLifecycle(14)

    // 是否「该提示」：开关开 且（从未备份 或 距上次备份天数 > 阈值）。
    // 每次进入首页（HomeScreen 合成）若满足即显示，停留 30s 自动消失，本会话内不再打扰；
    // 用户主动点 X / 点横幅也会立即隐藏。不做「每日一次」持久抑制——只要仍超期，下次打开应用重新提示。
    val daysSince = if (lastBackupAt > 0L) {
        (System.currentTimeMillis() - lastBackupAt) / DAY_MS
    } else 0L
    val reminderDue = remember(
        backupReminderOn, lastBackupAt, backupReminderThresholdDays, itemsLoaded, daysSince
    ) {
        if (!itemsLoaded || !backupReminderOn) false
        else (lastBackupAt == 0L || daysSince > backupReminderThresholdDays)
    }
    var bannerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(reminderDue) {
        if (reminderDue) {
            bannerVisible = true
            delay(BACKUP_REMINDER_AUTO_HIDE_MS) // 30s 后自动消失，本会话不再提示
            bannerVisible = false
        } else {
            bannerVisible = false
        }
    }
    val backupReminderText = remember(reminderDue, lastBackupAt, daysSince) {
        if (!reminderDue) null
        else if (lastBackupAt == 0L) "尚未备份数据，建议立即备份"
        else "已 ${daysSince} 天未备份，建议立即备份"
    }

    val healthLabel by viewModel.healthLabel.collectAsStateWithLifecycle("—")
    val overview = remember(items, healthLabel) { items.toOverview(healthLabel) }

    // 组合过滤 + 排序：分类 chip AND 搜索 AND 状态 filter AND 排序（三条件叠加）
    val displayed = remember(items, selectedColorKey, searchKeyword, sortKey, sortOrder, statusFilter) {
        items
            .filter { selectedColorKey == null || it.categoryKey == selectedColorKey }
            .filter { searchKeyword.isEmpty() || it.name.contains(searchKeyword, ignoreCase = true) }
            .filter { statusFilter.isEmpty() || statusFilter.contains(it.status) }
            .let { list ->
                when (sortKey) {
                    "date" -> if (sortOrder == "asc") list.sortedBy { it.purchaseDate }
                    else list.sortedByDescending { it.purchaseDate }
                    "price" -> if (sortOrder == "asc") list.sortedBy { it.purchasePrice }
                    else list.sortedByDescending { it.purchasePrice }
                    else -> list
                }
            }
    }

    // Step 42：模式 A 按购入月份分组 + 当日首行标记。displayed 已含排序结果，这里只做二次切分；
    // 不进 VM、不动排序/筛选/搜索（规格 §5.5 明令），回滚只需删这一处 remember 与渲染处 forEach。
    // groupBy 返回 LinkedHashMap，组序 = 排序结果中各组首次出现的顺序（默认日期降序 → 最新月在最前）。
    // 已知边界：若按价格排序，同日物品可能不相邻，日期锚会按「相邻比较」重复出现，属可接受行为。
    val monthGroups = remember(displayed) {
        displayed
            .groupBy { monthKeyOf(it.purchaseDate) }
            .map { (month, itemsInMonth) ->
                var lastDayKey: String? = null
                month to itemsInMonth.map { item ->
                    val dayKey = dayKeyOf(item.purchaseDate)
                    val entry = TimelineEntry(item, isFirstOfDay = dayKey != lastDayKey)
                    lastDayKey = dayKey
                    entry
                }
            }
    }

    var detailItem by remember { mutableStateOf<ItemUiModel?>(null) }
    var searchActive by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var viewMenuOpen by remember { mutableStateOf(false) }
    // 首页右滑互斥：仅一张卡展开；pendingDelete 挂起待二次确认的删除项
    var openItemId by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<ItemUiModel?>(null) }

    // 左滑展开互斥：点击「编辑/删除按钮区之外」的任意位置（列表空白区、其它卡片、顶栏、底部栏、筛选 chip）
    // 都自动收起当前展开的卡片。详见下方各 onClick 接入点。
    val closeSwipe: () -> Unit = { if (openItemId != null) openItemId = null }

    val filterActive = statusFilter.isNotEmpty() || sortKey != "date" || sortOrder != "desc"

    // ============================================================
    // [Step 55] 三视图滚动状态：用于「返回回顶」与「是否在顶部」上报
    //
    // 三个视图用三种不同的滚动容器，各需一种 State 类型，不能共用：
    //   DEFAULT → LazyListState            （LazyColumn）
    //   CARD    → LazyStaggeredGridState    （LazyVerticalStaggeredGrid）
    //   COMPACT → LazyGridState             （LazyVerticalGrid）
    //
    // 「是否在顶部」的判定规则：首项索引为 0 且首项偏移为 0。
    // 只看索引不够 —— 卡片被部分滚出时索引仍是 0 但其实已经不在顶。
    // ============================================================
    val defaultListState = rememberLazyListState()
    val cardStaggerState = rememberLazyStaggeredGridState()
    val compactGridState = rememberLazyGridState()

    // 当前视图对应的「是否在顶部」
    val isAtTop = when (viewMode) {
        HomeViewMode.DEFAULT ->
            defaultListState.firstVisibleItemIndex == 0 &&
                defaultListState.firstVisibleItemScrollOffset == 0
        HomeViewMode.CARD ->
            cardStaggerState.firstVisibleItemIndex == 0 &&
                cardStaggerState.firstVisibleItemScrollOffset == 0
        HomeViewMode.COMPACT ->
            compactGridState.firstVisibleItemIndex == 0 &&
                compactGridState.firstVisibleItemScrollOffset == 0
    }

    // 上报给根层。仅在值变化时写出，避免每帧重组。
    LaunchedEffect(isAtTop) { onScrollStateChanged(isAtTop) }

    // 根层请求回顶（系统返回时触发），按当前视图调对应的 animateScrollToItem。
    LaunchedEffect(scrollTopSignal) {
        if (scrollTopSignal > 0) {
            when (viewMode) {
                HomeViewMode.DEFAULT -> defaultListState.animateScrollToItem(0)
                HomeViewMode.CARD -> cardStaggerState.animateScrollToItem(0)
                HomeViewMode.COMPACT -> compactGridState.animateScrollToItem(0)
            }
        }
    }

    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(searchActive) {
        if (searchActive) searchFocus.requestFocus()
    }

    // 顶栏「列表切换」按钮在窗口中的位置，用于菜单 overlay 锚定
    var viewBtnBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    Box(modifier) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            placeholder = { Text("搜索物品名称") },
                            singleLine = true,
                            // [修复05] 圆角对齐下方卡片（模式 A/B 卡同为 20dp 令牌），默认 4dp 太尖
                            shape = RoundedCornerShape(ItemCardRadius.listRow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = WujiSpace.sm)
                                .focusRequester(searchFocus),
                        )
                    } else {
                        Text(
                            text = "你的物品",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                navigationIcon = {},
                actions = {
                    if (searchActive) {
                        // 搜索激活：左侧无 nav，× 取消按钮置于右侧 actions；与搜索框保留间距
                        Spacer(Modifier.width(WujiSpace.xs))
                        CircleIconButton(
                            icon = Icons.Filled.Close,
                            contentDescription = "取消搜索",
                            onClick = {
                                openItemId = null
                                searchActive = false
                                viewModel.setSearchQuery("")
                            },
                        )
                        Spacer(Modifier.width(WujiSpace.md))
                    } else {
                        // 视图切换：点击触发 HomeScreen overlay 层的菜单（参考 3.5）。
                        // 这里不放 Popup/DropdownMenu，避免容器矩形穿透圆角。
                        // 菜单位置由按钮 boundsInWindow 决定，渲染在 HomeScreen 父 Box 的最末层。
                        CircleIconButton(
                            // [修复04] 图标跟随当前视图模式，与菜单行共用 viewModeIcon 映射
                            icon = viewModeIcon(viewMode),
                            contentDescription = "列表切换",
                            onClick = { openItemId = null; viewMenuOpen = true },
                            modifier = Modifier.onGloballyPositioned { coords ->
                                viewBtnBounds = coords.boundsInWindow()
                            },
                        )
                        Spacer(Modifier.width(WujiSpace.sm))
                        CircleIconButton(
                            icon = Icons.Filled.Search,
                            contentDescription = "搜索",
                            onClick = { openItemId = null; searchActive = true },
                        )
                        Spacer(Modifier.width(WujiSpace.sm))
                        CircleIconButton(
                            icon = Icons.Filled.Settings,
                            contentDescription = "设置",
                            onClick = { openItemId = null; onSetting() },
                        )
                        Spacer(Modifier.width(WujiSpace.md))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        // 数据首读前：复用品牌启动页视觉，避免「空白页→有物品页」闪现，且与进程预热期启动页连续。
        if (!itemsLoaded) {
            AppSplash()
            return@Scaffold
        }
        val listModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            // 列表空白区点击：卡片展开时，点空白（非卡片、非按钮）自动收起。
            // 卡片自身 content clickable 会消费点击，故不会与卡片点击冲突；
            // 此处仅承接滚动容器内未被子项消费的空白点击。
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = closeSwipe,
            )

        if (viewMode == HomeViewMode.DEFAULT) {
            LazyColumn(
                state = defaultListState,
                modifier = listModifier,
                contentPadding = PaddingValues(
                    start = WujiSpace.lg,
                    end = WujiSpace.lg,
                    top = WujiSpace.md,
                    bottom = listBottomAvoidance(),
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item { HomeOverview(overview = overview, privacyMasked = privacyMasked) }
                item {
                    if (bannerVisible && backupReminderText != null) {
                        Spacer(Modifier.height(WujiSpace.md))
                        BackupReminderBanner(
                            text = backupReminderText,
                            onClick = {
                                bannerVisible = false
                                onBackup()
                            },
                            onDismiss = { bannerVisible = false },
                        )
                        Spacer(Modifier.height(WujiSpace.md))
                    }
                }
                item {
                    CategoryFilterBar(
                        categories = categories,
                        selected = selectedColorKey,
                        onSelect = viewModel::selectColorKey,
                        onFilter = { showFilter = true },
                        filterActive = filterActive,
                    )
                }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    )
                }
                // 分割线 → 首个组头目标 16dp（组头自带 top 8dp，此处补 8dp，见 ItemSectionHeader 节律）
                item { Spacer(Modifier.height(ItemListSpacing.row)) }
                if (displayed.isEmpty()) {
                    item {
                        HomeEmptyState(
                            globalEmpty = items.isEmpty(),
                            keyword = searchKeyword,
                            isCategoryFiltered = selectedColorKey != null || statusFilter.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = WujiSpace.xl),
                        )
                    }
                } else {
                    // 每组 = 组头（大月份 + 年份 + 件数，图 2 样式）+ 组块（竖线 + 日期锚 + 行列表）。
                    // 组间 16 / 组上 12 / 行间 8：组头自带 top8+bottom12，组内 spacedBy(8)（Step 42）。
                    monthGroups.forEach { (monthKey, entries) ->
                        item(key = "header-$monthKey", contentType = "SectionHeader") {
                            ItemSectionHeader(
                                monthName = monthNameOf(entries.first().item.purchaseDate),
                                year = yearOf(entries.first().item.purchaseDate),
                                count = entries.size,
                            )
                        }
                        item(key = "group-$monthKey", contentType = "TimelineGroup") {
                            // 组块：竖线在此统一画全高（跨 8dp 行缝连续），起点 = 首行节点中心（行高一半 40dp）。
                            // 线中心 x = 日期区 34 + 通道 18/2 = 43dp，与行内节点同轴（令牌同源）。
                            val lineColor = MaterialTheme.colorScheme.outlineVariant
                            val lineX = ItemListRail.dateWidth + ItemListRail.channelWidth / 2
                            Box(
                                modifier = Modifier.drawBehind {
                                    drawLine(
                                        color = lineColor,
                                        start = Offset(lineX.toPx(), 40.dp.toPx()),
                                        end = Offset(lineX.toPx(), size.height),
                                        strokeWidth = ItemListRail.lineWidth.toPx(),
                                    )
                                },
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(ItemListSpacing.row)) {
                                    entries.forEach { entry ->
                                        SwipeableItemCard(
                                            isOpen = openItemId == entry.item.id,
                                            onOpenChange = { open -> openItemId = if (open) entry.item.id else null },
                                            onEdit = { onEdit(entry.item.id) },
                                            onDelete = { pendingDelete = entry.item; openItemId = null },
                                            // 与卡片 20dp 圆角对齐：默认 8dp 会在圆角缝隙露出删除按钮红底
                                            cornerRadius = ItemCardRadius.listRow,
                                        ) {
                                            TimelineRow(
                                                item = entry.item,
                                                privacyMasked = privacyMasked,
                                                showDateAnchor = entry.isFirstOfDay,
                                                onClick = {
                                                    val wasOpen = openItemId == entry.item.id
                                                    if (openItemId != null) openItemId = null
                                                    if (!wasOpen) detailItem = entry.item
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (viewMode == HomeViewMode.CARD) {
            // 模式 B 瀑布流（Step 43）：Adaptive(150dp) 在 360dp 屏恰好 2 列，宽屏自适应加列
            LazyVerticalStaggeredGrid(
                state = cardStaggerState,
                columns = StaggeredGridCells.Adaptive(ItemWaterfallLayout.columnWidth),
                modifier = listModifier,
                contentPadding = PaddingValues(
                    start = WujiSpace.lg,
                    end = WujiSpace.lg,
                    top = WujiSpace.md,
                    bottom = listBottomAvoidance(),
                ),
                verticalItemSpacing = ItemWaterfallLayout.gap,
                horizontalArrangement = Arrangement.spacedBy(ItemWaterfallLayout.gap),
            ) {
                // 头部整块合成一个跨列 item（内容与 COMPACT 臂共用 ModeGridHeader）。
                // staggered 的 verticalItemSpacing 同样作用于 item 之间，块内补 4dp 与旧 grid 节奏一致。
                item(span = StaggeredGridItemSpan.FullLine) {
                    ModeGridHeader(
                        overview = overview,
                        privacyMasked = privacyMasked,
                        bannerVisible = bannerVisible,
                        backupReminderText = backupReminderText,
                        onBackupClick = { bannerVisible = false; onBackup() },
                        onBannerDismiss = { bannerVisible = false },
                        categories = categories,
                        selectedCategoryKey = selectedColorKey,
                        onSelectCategory = { openItemId = null; viewModel.selectColorKey(it) },
                        onOpenFilter = { openItemId = null; showFilter = true },
                        filterActive = filterActive,
                    )
                }
                if (displayed.isEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        ModeGridEmpty(
                            globalEmpty = items.isEmpty(),
                            keyword = searchKeyword,
                            isCategoryFiltered = selectedColorKey != null || statusFilter.isNotEmpty(),
                        )
                    }
                } else {
                    // 左滑 Compact 密度（64dp 上下堆叠纯图标，沿用旧 2×2 口径）；圆角与卡片同为 20dp
                    staggeredItems(items = displayed, key = { it.id }) { item ->
                        SwipeableItemCard(
                            isOpen = openItemId == item.id,
                            onOpenChange = { open -> openItemId = if (open) item.id else null },
                            onEdit = { onEdit(item.id) },
                            onDelete = { pendingDelete = item; openItemId = null },
                            density = SwipeActionDensity.Compact,
                            cornerRadius = ItemCardRadius.waterfall,
                        ) {
                            ItemWaterfallCard(
                                item = item,
                                privacyMasked = privacyMasked,
                                onClick = {
                                    val wasOpen = openItemId == item.id
                                    if (openItemId != null) openItemId = null
                                    if (!wasOpen) detailItem = item
                                },
                            )
                        }
                    }
                }
            }
        } else {
            // 模式 C 紧凑列表：暂沿用旧 3 列等高网格（Step 44 陈列馆改造时重写此臂）
            LazyVerticalGrid(
                state = compactGridState,
                columns = GridCells.Fixed(3),
                modifier = listModifier,
                contentPadding = PaddingValues(
                    start = WujiSpace.lg,
                    end = WujiSpace.lg,
                    top = WujiSpace.md,
                    bottom = listBottomAvoidance(),
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ModeGridHeader(
                        overview = overview,
                        privacyMasked = privacyMasked,
                        bannerVisible = bannerVisible,
                        backupReminderText = backupReminderText,
                        onBackupClick = { bannerVisible = false; onBackup() },
                        onBannerDismiss = { bannerVisible = false },
                        categories = categories,
                        selectedCategoryKey = selectedColorKey,
                        onSelectCategory = { openItemId = null; viewModel.selectColorKey(it) },
                        onOpenFilter = { openItemId = null; showFilter = true },
                        filterActive = filterActive,
                    )
                }
                if (displayed.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ModeGridEmpty(
                            globalEmpty = items.isEmpty(),
                            keyword = searchKeyword,
                            isCategoryFiltered = selectedColorKey != null || statusFilter.isNotEmpty(),
                        )
                    }
                } else {
                    // 陈列馆（Step 44）：左滑 Gallery 密度（40dp 单列）+ 格级 clipToBounds，
                    // 展开卡越界部分被裁掉，不压左邻卡；圆角与陈列馆格同为 16dp
                    gridItems(items = displayed, key = { it.id }) { item ->
                        Box(modifier = Modifier.clipToBounds()) {
                            SwipeableItemCard(
                                isOpen = openItemId == item.id,
                                onOpenChange = { open -> openItemId = if (open) item.id else null },
                                onEdit = { onEdit(item.id) },
                                onDelete = { pendingDelete = item; openItemId = null },
                                density = SwipeActionDensity.Gallery,
                                cornerRadius = ItemCardRadius.gallery,
                            ) {
                                ItemExhibitCell(
                                    item = item,
                                    privacyMasked = privacyMasked,
                                    onClick = {
                                        val wasOpen = openItemId == item.id
                                        if (openItemId != null) openItemId = null
                                        if (!wasOpen) detailItem = item
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    detailItem?.let { item ->
        ItemDetailOverlay(item = item, privacyMasked = privacyMasked, onDismiss = { detailItem = null })
    }

    // 删除二次确认（软删除，可在「设置-最近删除」30 天内找回）
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除物品？") },
            text = { Text("删除后可在「设置 - 最近删除」中找回，30 天内可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.softDelete(item)
                        pendingDelete = null
                        openItemId = null
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    if (showFilter) {
        FilterSortSheet(
            currentSortKey = sortKey,
            currentSortOrder = sortOrder,
            currentStatusFilter = statusFilter,
            onApply = { key, order, status -> viewModel.applyFilter(key, order, status) },
            onDismiss = { showFilter = false },
        )
    }

    // overlay：视图切换菜单（参考 3.5）。完全用父 Box 内自渲染，不用 Popup/DropdownMenu，
    // 避免平台容器矩形底色穿透圆角。graphicsLayer 强制按 16dp 圆角裁剪整层。
    if (viewMenuOpen) {
        ViewModeMenuOverlay(
            anchor = viewBtnBounds,
            currentMode = viewMode,
            onPick = { m -> viewModel.setViewMode(m); viewMenuOpen = false },
            onDismiss = { viewMenuOpen = false },
        )
    }
    }  // 闭合父 Box
}

/**
 * 模式 → 图标映射（修复04）：菜单三行与顶部触发按钮共用，单一事实源，
 * 触发按钮图标跟随当前视图模式。
 */
private fun viewModeIcon(mode: HomeViewMode): ImageVector = when (mode) {
    HomeViewMode.DEFAULT -> Icons.Filled.ViewTimeline
    HomeViewMode.CARD -> Icons.Filled.SpaceDashboard
    HomeViewMode.COMPACT -> Icons.Filled.GridView
}

// [修复06] 列表底部避让悬浮胶囊底栏：胶囊 68dp + FAB 凸出 16dp + 8dp 呼吸。
// 底栏在 WujiApp 层悬浮于内容上方（Scaffold 无 bottomBar 占位，padding.bottom 仅含系统手势条），
// 原来三个列表 contentPadding bottom = 0，末卡静止时垫在胶囊后，左滑的编辑/删除操作区被盖住无法点击。
//
// [Step 54] 前置系统栏避让。做成函数而非常量，因为系统 inset 是运行时值（要读
// WindowInsets），而原版是顶层 val 算不出来。手势导航下系统不给 navigationBars
// source 时 bottomBarInsetDp() 返回 24.dp 下限；列表须同步加高，否则末卡会被胶囊压住。
// 详见 ui/designsystem/Insets.kt。
@Composable
private fun listBottomAvoidance(): Dp =
    bottomBarInsetDp() + WujiSize.bottomBarHeight +
        WujiSize.bottomBarFabProtrude + WujiSpace.sm

/**
 * 视图切换菜单 overlay（参考 3.5 视图切换按钮状态页面）：
 * - 全屏透明遮罩，点击关闭。
 * - 菜单本体用 graphicsLayer { shape = RoundedCornerShape(16dp); clip = true } 强制按 16dp 圆角
 *   裁剪整层（包括 background / border），阴影在 layer 内生成后被裁到圆角外，干净无矩形穿透。
 */
@Composable
private fun ViewModeMenuOverlay(
    anchor: androidx.compose.ui.geometry.Rect?,
    currentMode: HomeViewMode,
    onPick: (HomeViewMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val menuTopDp = if (anchor != null) {
        with(density) { (anchor.top + anchor.height).toDp() } + 4.dp
    } else {
        96.dp
    }

    Box(Modifier.fillMaxSize()) {
        // 1. 全屏透明遮罩：捕获点击关闭
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        // 2. 菜单本体：定位到按钮右下角向下 4dp，水平贴右内缩 16dp
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = menuTopDp, end = 16.dp)
                .width(224.dp)
                .graphicsLayer {
                    shape = RoundedCornerShape(WujiRadius.xl)
                    clip = true
                    shadowElevation = 8.dp.toPx()
                    ambientShadowColor = Color.Black.copy(alpha = 0.12f)
                    spotShadowColor = Color.Black.copy(alpha = 0.12f)
                }
                .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.98f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(WujiRadius.xl),
                )
                .padding(8.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ViewModeMenuRow(
                    label = "时间轴",
                    icon = viewModeIcon(HomeViewMode.DEFAULT),
                    mode = HomeViewMode.DEFAULT,
                    currentMode = currentMode,
                    onPick = onPick,
                )
                ViewModeMenuRow(
                    label = "瀑布流",
                    icon = viewModeIcon(HomeViewMode.CARD),
                    mode = HomeViewMode.CARD,
                    currentMode = currentMode,
                    onPick = onPick,
                )
                ViewModeMenuRow(
                    label = "陈列馆",
                    icon = viewModeIcon(HomeViewMode.COMPACT),
                    mode = HomeViewMode.COMPACT,
                    currentMode = currentMode,
                    onPick = onPick,
                )
            }
        }
    }
}

/**
 * 视图切换下拉菜单项（参考 3.5 视图切换按钮状态页面）：
 * 圆角矩形（rounded-xl 12dp），gap 12dp，py 12dp。
 * 选中态：BrandOrange 底 + 白字白图标 + 阴影；未选中：onSurfaceVariant 灰。
 */
@Composable
private fun ViewModeMenuRow(
    label: String,
    icon: ImageVector,
    mode: HomeViewMode,
    currentMode: HomeViewMode,
    onPick: (HomeViewMode) -> Unit,
) {
    val selected = currentMode == mode
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) BrandOrange else Color.Transparent)
            .clickable { onPick(mode) }
            .padding(horizontal = WujiSpace.md, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 首页备份阈值提示行（Step 29）：
 * 开关开且超期时出现；点击左侧区域跳备份页，点击 X 仅关闭今日提示。
 * 不用 surfaceContainerLowest + 阴影，避免与上方资产概览卡视觉重叠；
 * 改用 surfaceContainerLow  flat 条，语义上是提示行而不是又一张概览卡。
 */
@Composable
private fun BackupReminderBanner(
    text: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WujiRadius.cardLg))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = WujiSpace.md, vertical = WujiSpace.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧：图标 + 文案，整片可点击进入备份页。
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.SettingsBackupRestore,
                    contentDescription = null,
                    tint = BrandOrange,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(WujiSpace.md))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            // 右侧：单独关闭今日提示。
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭今日备份提醒",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * 空态：参考 `1.0app应用初创状态页面`（灰圆 + 主标 + 副标）。
 * 区分：全局空（无任何物品）/ 搜索无结果 / 分类过滤空。
 */
@Composable
private fun HomeEmptyState(
    globalEmpty: Boolean,
    keyword: String,
    isCategoryFiltered: Boolean,
    modifier: Modifier = Modifier,
) {
    val (title, sub) = when {
        globalEmpty -> "一张空白的画布等待着我们" to "点击底部的\"+\"号，开启您的第一条物品记录"
        keyword.isNotEmpty() -> "没有找到「$keyword」相关的物品" to "换个关键词或清空筛选试试"
        isCategoryFiltered -> "当前分类下还没有物品" to "试着切换其他分类或新增一件"
        else -> "这里空空如也" to "换个筛选条件试试"
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(WujiSpace.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(WujiSpace.sm))
        Text(
            text = sub,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 网格系视图（B 瀑布流 / C 紧凑）共用的头部合并块：
 * 资产概览 + 备份横幅 + 分类筛选条 + 1dp 分割线 + 4dp 补偿。
 * 容器的 item 间距（12dp）已计入节奏，块内补 4dp 使分割线 → 首行卡片 = 16dp，与模式 A 一致。
 */
@Composable
private fun ModeGridHeader(
    overview: HomeOverview,
    privacyMasked: Boolean,
    bannerVisible: Boolean,
    backupReminderText: String?,
    onBackupClick: () -> Unit,
    onBannerDismiss: () -> Unit,
    categories: List<CategoryEntity>,
    selectedCategoryKey: String?,
    onSelectCategory: (String?) -> Unit,
    onOpenFilter: () -> Unit,
    filterActive: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeOverview(overview = overview, privacyMasked = privacyMasked)
        if (bannerVisible && backupReminderText != null) {
            Spacer(Modifier.height(WujiSpace.md))
            BackupReminderBanner(
                text = backupReminderText,
                onClick = onBackupClick,
                onDismiss = onBannerDismiss,
            )
            Spacer(Modifier.height(WujiSpace.md))
        }
        CategoryFilterBar(
            categories = categories,
            selected = selectedCategoryKey,
            onSelect = onSelectCategory,
            onFilter = onOpenFilter,
            filterActive = filterActive,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** 网格系视图（B/C 共用）空态，跨全行 */
@Composable
private fun ModeGridEmpty(
    globalEmpty: Boolean,
    keyword: String,
    isCategoryFiltered: Boolean,
) {
    HomeEmptyState(
        globalEmpty = globalEmpty,
        keyword = keyword,
        isCategoryFiltered = isCategoryFiltered,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = WujiSpace.xl),
    )
}