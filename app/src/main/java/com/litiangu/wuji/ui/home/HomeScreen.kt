package com.litiangu.wuji.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 首页（主 tab）：物品列表 + 分类筛选 + 隐私开关。
 * 新增 / 编辑由导航回调驱动（Step8 收口：子页面切换移入 NavHost）。
 * 返回后列表随 Room Flow 自动刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNew: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    val viewModel = remember {
        val db = com.litiangu.wuji.data.db.WujiDatabaseProvider.get(context)
        HomeViewModel(db.itemDao(), db.categoryDao(), context)
    }
    val rows by viewModel.rows.collectAsStateWithLifecycle(emptyList())
    val privacy by viewModel.privacyMode.collectAsStateWithLifecycle(false)
    val selected by viewModel.selectedCategory.collectAsStateWithLifecycle(null)
    val categories by viewModel.categories.collectAsStateWithLifecycle(emptyList())

    val filtered = remember(rows, selected) {
        if (selected == null) rows else rows.filter { it.categoryKey == selected }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("物记") },
                actions = {
                    IconButton(onClick = { viewModel.togglePrivacyMode() }) {
                        Icon(
                            if (privacy) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "切换隐私模式"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) {
                Icon(Icons.Filled.Add, contentDescription = "新增物品")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            CategoryFilterBar(
                categories = categories,
                selected = selected,
                onSelect = viewModel::selectCategory
            )
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered) { row ->
                    ItemRowCard(row = row, onClick = { onEdit(row.id) })
                }
            }
        }
    }
}
