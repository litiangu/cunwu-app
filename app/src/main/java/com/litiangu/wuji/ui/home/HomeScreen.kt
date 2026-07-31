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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.data.settings.DarkModePref
import com.litiangu.wuji.ui.item.ItemEditScreen

/**
 * 首页：物品列表 + 分类筛选 + 隐私开关。
 * 点击 FAB 新增、点卡片编辑，进入 ItemEditScreen；返回后列表随 Room Flow 自动刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val viewModel = remember {
        val db = com.litiangu.wuji.data.db.WujiDatabaseProvider.get(context)
        HomeViewModel(db.itemDao(), db.categoryDao(), context)
    }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }

    when (val target = editTarget) {
        is EditTarget.New -> ItemEditScreen(itemId = null, onDone = { editTarget = null })
        is EditTarget.Existing -> ItemEditScreen(itemId = target.id, onDone = { editTarget = null })
        null -> HomeContent(
            viewModel = viewModel,
            onNew = { editTarget = EditTarget.New },
            onEdit = { id -> editTarget = EditTarget.Existing(id) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    viewModel: HomeViewModel,
    onNew: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle(emptyList())
    val privacy by viewModel.privacyMode.collectAsStateWithLifecycle(false)
    val selected by viewModel.selectedCategory.collectAsStateWithLifecycle(null)
    val categories by viewModel.categories.collectAsStateWithLifecycle(emptyList())
    val dark by viewModel.darkMode.collectAsStateWithLifecycle(DarkModePref.FOLLOW_SYSTEM)

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

/** 编辑入口目标：新增或编辑指定 id 的物品 */
sealed interface EditTarget {
    data object New : EditTarget
    data class Existing(val id: Long) : EditTarget
}
