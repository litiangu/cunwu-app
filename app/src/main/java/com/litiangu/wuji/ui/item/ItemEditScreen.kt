package com.litiangu.wuji.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.data.db.ItemStatus
import com.litiangu.wuji.data.db.WujiDatabaseProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 新增 / 编辑物品表单。
 * itemId == null 为新增，否则编辑该物品（回显）。
 * preselectCategoryId：分类选择页预选的分类 id（Step 12，非 null 时自动选中该分类）。
 * onDone：保存成功或按返回键时调用，回到首页。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemEditScreen(
    itemId: Long?,
    preselectCategoryId: Long? = null,   // Step 12: 分类选择页预选
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = remember {
        val db = WujiDatabaseProvider.get(context)
        ItemEditViewModel(db.itemDao(), db.categoryDao(), itemId)
    }
    val categories by viewModel.categories.collectAsStateWithLifecycle(emptyList())
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Step 12 方案 B：首次组合时将预选 categoryId 设入 ViewModel
    // （最小改动，不侵入 ViewModel 构造签名；Step 13 表单重构时可统一改为方案 A）
    LaunchedEffect(preselectCategoryId) {
        if (preselectCategoryId != null) {
            viewModel.onCategorySelect(preselectCategoryId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (itemId == null) "新增物品" else "编辑物品") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 名称（必填）
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("物品名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 分类选择（未分类 + 预置/自定义分类）
            Text("分类", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = viewModel.categoryId == null,
                    onClick = { viewModel.onCategorySelect(null) },
                    label = { Text("未分类") }
                )
                categories.forEach { cat ->
                    FilterChip(
                        selected = viewModel.categoryId == cat.id,
                        onClick = { viewModel.onCategorySelect(cat.id) },
                        label = { Text(cat.name) }
                    )
                }
            }

            // 状态选择
            Text("状态", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ItemStatus.entries.forEach { s ->
                    FilterChip(
                        selected = viewModel.status == s,
                        onClick = { viewModel.onStatusSelect(s) },
                        label = { Text(s.label) }
                    )
                }
            }

            // 购买日期（默认今天，编辑时回显）
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("购买日期：${dateFormat.format(Date(viewModel.purchaseDate))}")
            }

            // 价值录入（购入价必填，其余可选）
            OutlinedTextField(
                value = viewModel.purchasePriceText,
                onValueChange = viewModel::onPurchasePriceChange,
                label = { Text("购入价（元）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = viewModel.extraCostText,
                onValueChange = viewModel::onExtraCostChange,
                label = { Text("附加费用（元，可选）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = viewModel.resaleValueText,
                onValueChange = viewModel::onResaleValueChange,
                label = { Text("二手变现收益（元，可选）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // 校验错误提示
            viewModel.errorMessage?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 保存
            Button(
                onClick = { viewModel.save(onDone) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = viewModel.purchaseDate
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            viewModel.onPurchaseDateChange(it)
                        }
                        showDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
