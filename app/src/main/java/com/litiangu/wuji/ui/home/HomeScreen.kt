package com.litiangu.wuji.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.ui.designsystem.BottomPillBar
import com.litiangu.wuji.ui.designsystem.BottomTab
import com.litiangu.wuji.ui.designsystem.CircleIconButton
import com.litiangu.wuji.ui.designsystem.TimelineRow
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSpace

/**
 * 首页：与 `stitch_app_settings_page_redesign/3.0应用主页-显示添加物品后的页面/screen.png` 1:1 对齐。
 *
 * 顶栏：「你的物品」+ 三圆钮（列表切换/搜索/更多）
 * 内容：2x2 资产概览 → 分类筛选 chips → 时间轴列表
 * 底栏：pill（我的/+ /分析），设置经首页 ⋯ 进入（不在底栏）
 * 空库：空态（参考 `1.0app应用初创状态页面`）
 * 点击物品：弹出 `ItemDetailOverlay`（ModalBottomSheet，参考 3.1）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNew: () -> Unit = {},
    onAnalysis: () -> Unit = {},
    onSetting: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel = remember {
        val db = com.litiangu.wuji.data.db.WujiDatabaseProvider.get(context)
        HomeViewModel(db.itemDao(), db.categoryDao(), context)
    }
    val items by viewModel.items.collectAsStateWithLifecycle(emptyList())
    val categories by viewModel.categories.collectAsStateWithLifecycle(emptyList())
    val selectedColorKey by viewModel.selectedColorKey.collectAsStateWithLifecycle(null)

    val overview = remember(items) { items.toOverview() }
    val filtered = remember(items, selectedColorKey) {
        if (selectedColorKey == null) items else items.filter { it.categoryKey == selectedColorKey }
    }

    var detailItem by remember { mutableStateOf<ItemUiModel?>(null) }

    val tabs = remember {
        listOf(
            BottomTab("首页", Icons.Filled.Home),
            BottomTab("分析", Icons.Filled.Insights),
        )
    }
    var selectedTab by remember { mutableStateOf(tabs[0]) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "你的物品",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                actions = {
                    CircleIconButton(
                        icon = Icons.AutoMirrored.Filled.List,
                        contentDescription = "列表切换",
                        onClick = { },
                    )
                    Spacer(Modifier.width(WujiSpace.sm))
                    CircleIconButton(
                        icon = Icons.Filled.Search,
                        contentDescription = "搜索",
                        onClick = { },
                    )
                    Spacer(Modifier.width(WujiSpace.sm))
                    CircleIconButton(
                        icon = Icons.Filled.Settings,
                        contentDescription = "设置",
                        onClick = onSetting,
                    )
                    Spacer(Modifier.width(WujiSpace.md))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            BottomPillBar(
                items = tabs,
                selected = selectedTab,
                onSelect = { tab ->
                    selectedTab = tab
                    if (tab.label == "分析") onAnalysis()
                },
                onFabClick = onNew,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = WujiSpace.lg,
                vertical = WujiSpace.md,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                HomeOverview(overview = overview)
            }
            item {
                Spacer(Modifier.height(WujiSpace.md))
            }
            item {
                CategoryFilterBar(
                    categories = categories,
                    selected = selectedColorKey,
                    onSelect = viewModel::selectColorKey,
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
            item {
                Spacer(Modifier.height(16.dp))
            }
            if (filtered.isEmpty()) {
                item {
                    HomeEmptyState(
                        isFiltered = selectedColorKey != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = WujiSpace.xl),
                    )
                }
            } else {
                items(items = filtered, key = { it.id }) { item ->
                    TimelineRow(
                        item = item,
                        onClick = { detailItem = item },
                        showLine = filtered.size > 1,
                    )
                }
            }
        }
    }

    detailItem?.let { item ->
        ItemDetailOverlay(item = item, onDismiss = { detailItem = null })
    }
}

/**
 * 空态：参考 `1.0app应用初创状态页面`（灰圆 + 主标 + 副标）。
 * 全局空（无任何物品）与分类过滤空区分文案。
 */
@Composable
private fun HomeEmptyState(
    isFiltered: Boolean,
    modifier: Modifier = Modifier,
) {
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
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(WujiSpace.lg))
        Text(
            text = if (isFiltered) "当前分类下还没有物品" else "一张空白的画布等待着我们",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(WujiSpace.sm))
        Text(
            text = if (isFiltered) "试着切换其他分类或新增一件"
                else "点击底部的\"+\"号，开启您的第一条物记",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}