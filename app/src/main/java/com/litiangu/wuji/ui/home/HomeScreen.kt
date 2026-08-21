package com.litiangu.wuji.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.automirrored.filled.ViewList
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.litiangu.wuji.ui.designsystem.CircleIconButton
import com.litiangu.wuji.ui.designsystem.TimelineRow
import com.litiangu.wuji.ui.home.CategoryFilterBar
import com.litiangu.wuji.ui.onboarding.AppSplash
import com.litiangu.wuji.ui.theme.BrandOrange
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSpace

/**
 * 首页常量：一天毫秒数（Step 29 备份阈值天数换算用）。
 */
private const val DAY_MS = 86_400_000L

/**
 * 首页备份提示行自动消失时长（Step 29）：超期提示显示后停留 30s 自动消失，本会话不再打扰。
 */
private const val BACKUP_REMINDER_AUTO_HIDE_MS = 30_000L

/**
 * 首页：与 `stitch_app_settings_page_redesign/3.0应用主页-显示添加物品后的页面/screen.png` 1:1 对齐。
 *
 * 顶栏：「你的物品」+ 三圆钮。
 * - 列表切换：点击在按钮处弹出 DropdownMenu 三选项（默认列表 / 卡片 2X2 / 紧凑 3X3），
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
) {
    val items by viewModel.items.collectAsStateWithLifecycle(emptyList())
    val itemsLoaded by viewModel.itemsLoaded.collectAsStateWithLifecycle(false)
    val categories by viewModel.categories.collectAsStateWithLifecycle(emptyList())
    val selectedColorKey by viewModel.selectedColorKey.collectAsStateWithLifecycle(null)
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle("list-default")
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

    val overview = remember(items) { items.toOverview() }

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
                            icon = Icons.AutoMirrored.Filled.ViewList,
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

        if (viewMode == "list-default") {
            LazyColumn(
                modifier = listModifier,
                contentPadding = PaddingValues(
                    start = WujiSpace.lg,
                    end = WujiSpace.lg,
                    top = WujiSpace.md,
                    bottom = 0.dp,
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
                item { Spacer(Modifier.height(16.dp)) }
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
                    items(items = displayed, key = { it.id }) { item ->
                        SwipeableItemCard(
                            isOpen = openItemId == item.id,
                            onOpenChange = { open -> openItemId = if (open) item.id else null },
                            onEdit = { onEdit(item.id) },
                            onDelete = { pendingDelete = item; openItemId = null },
                        ) {
                            TimelineRow(
                                item = item,
                                privacyMasked = privacyMasked,
                                onClick = {
                                    val wasOpen = openItemId == item.id
                                    if (openItemId != null) openItemId = null
                                    if (!wasOpen) detailItem = item
                                },
                                showLine = displayed.size > 1,
                            )
                        }
                    }
                }
            }
        } else {
            val cols = if (viewMode == "card-2x2") 2 else 3
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = listModifier,
                contentPadding = PaddingValues(
                    start = WujiSpace.lg,
                    end = WujiSpace.lg,
                    top = WujiSpace.md,
                    bottom = 0.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 头部整块合成一个跨列 item。LazyVerticalGrid 的 verticalArrangement(12dp)
                // 作用于「行与行之间」，头部若拆成多个 item 会被额外插入 12dp，
                // 使分割线到首行卡片的间距大于默认列表。合并后由块内 Spacer 精确控制。
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HomeOverview(overview = overview, privacyMasked = privacyMasked)
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
                        CategoryFilterBar(
                            categories = categories,
                            selected = selectedColorKey,
                            onSelect = { openItemId = null; viewModel.selectColorKey(it) },
                            onFilter = { openItemId = null; showFilter = true },
                            filterActive = filterActive,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        )
                        // 分割线 → 首行卡片目标间距 16dp（与默认列表一致）；
                        // 行间 verticalArrangement 已提供 12dp，此处补 4dp。
                        Spacer(Modifier.height(4.dp))
                    }
                }
                if (displayed.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                    // 三种视图模式统一使用「左滑露出编辑/删除」，仅按单格面积调整按钮密度：
                    // 2×2 用 Compact（88dp 纯图标），3×3 用 Mini（64dp 纯图标）。
                    val swipeDensity = if (viewMode == "card-2x2") {
                        SwipeActionDensity.Compact
                    } else {
                        SwipeActionDensity.Mini
                    }
                    gridItems(items = displayed, key = { it.id }) { item ->
                        SwipeableItemCard(
                            isOpen = openItemId == item.id,
                            onOpenChange = { open -> openItemId = if (open) item.id else null },
                            onEdit = { onEdit(item.id) },
                            onDelete = { pendingDelete = item; openItemId = null },
                            density = swipeDensity,
                            cornerRadius = if (viewMode == "card-2x2") WujiRadius.xl else WujiRadius.lg,
                        ) {
                            if (viewMode == "card-2x2") {
                                ItemGridCard(
                                    item = item,
                                    privacyMasked = privacyMasked,
                                    onClick = {
                                        val wasOpen = openItemId == item.id
                                    if (openItemId != null) openItemId = null
                                    if (!wasOpen) detailItem = item
                                    },
                                )
                            } else {
                                ItemCompactCell(
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
 * 视图切换菜单 overlay（参考 3.5 视图切换按钮状态页面）：
 * - 全屏透明遮罩，点击关闭。
 * - 菜单本体用 graphicsLayer { shape = RoundedCornerShape(16dp); clip = true } 强制按 16dp 圆角
 *   裁剪整层（包括 background / border），阴影在 layer 内生成后被裁到圆角外，干净无矩形穿透。
 */
@Composable
private fun ViewModeMenuOverlay(
    anchor: androidx.compose.ui.geometry.Rect?,
    currentMode: String,
    onPick: (String) -> Unit,
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
                    label = "默认列表",
                    icon = Icons.AutoMirrored.Filled.ViewList,
                    mode = "list-default",
                    currentMode = currentMode,
                    onPick = onPick,
                )
                ViewModeMenuRow(
                    label = "卡片列表 (2X2)",
                    icon = Icons.Filled.GridView,
                    mode = "card-2x2",
                    currentMode = currentMode,
                    onPick = onPick,
                )
                ViewModeMenuRow(
                    label = "紧凑列表 (3X3)",
                    icon = Icons.Filled.ViewModule,
                    mode = "list-compact",
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
    mode: String,
    currentMode: String,
    onPick: (String) -> Unit,
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