package com.litiangu.wuji.ui.item

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.data.db.CategoryEntity
import com.litiangu.wuji.data.db.ItemStatus
import com.litiangu.wuji.data.db.WujiDatabaseProvider
import com.litiangu.wuji.ui.category.CustomCategoryDialog
import com.litiangu.wuji.ui.designsystem.categoryDotColor
import com.litiangu.wuji.ui.designsystem.categoryIcon
import com.litiangu.wuji.ui.theme.BrandOrange
import com.litiangu.wuji.ui.theme.BrandOrangeDark
import com.litiangu.wuji.ui.theme.TintOrangeDark
import com.litiangu.wuji.ui.theme.TintOrangeLight
import com.litiangu.wuji.ui.theme.WujiRadius
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 新增 / 编辑物品表单 [Screen 2.1]。
 * itemId == null 为新增，否则编辑该物品（回显）。
 * initialCategoryId：分类选择页预选的分类 id（Step 13 方案 A）。
 * onDone：保存成功或点 × 关闭时调用，回到首页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(
    itemId: Long?,
    initialCategoryId: Long? = null,   // Step 13 方案 A：预选分类由构造参数注入
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 方案 A：构造参数直接注入预选，无需 LaunchedEffect 副作用
    val viewModel = remember {
        val db = WujiDatabaseProvider.get(context)
        ItemEditViewModel(db.itemDao(), db.categoryDao(), itemId, initialCategoryId)
    }
    val categories by viewModel.categories.collectAsStateWithLifecycle(emptyList())
    // 预置 + 自定义统一排序（与分类选择页一致）：按 sortOrder 升序、其次 id
    val sortedCats = categories.sortedWith(compareBy({ it.sortOrder }, { it.id }))

    val selectedCategory = categories.firstOrNull { it.id == viewModel.categoryId }
    val catName = selectedCategory?.name ?: ""
    val title = buildString {
        append(if (itemId != null) "编辑" else "新增")
        if (catName.isNotEmpty()) append(catName)
        append("物品")
    }

    // 深色判定（不依赖系统设置，按当前 colorScheme 亮度，覆盖跟随系统/强制档）
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tintOrange = if (isDark) TintOrangeDark else TintOrangeLight
    val brand = if (isDark) BrandOrangeDark else BrandOrange

    var showDatePicker by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy年M月d日", Locale.CHINA) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onDone) }) {
                        Text("保存", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ===== 顶部图片占位区（Step 14 接 2.5 上传，本步点击 no-op）=====
            ImagePlaceholder(categoryKey = selectedCategory?.colorKey)

            // ===== 分类选择区 =====
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 行一：自定义 + 清除选择
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = { showCustomDialog = true },
                        label = { Text("+ 自定义") },
                        leadingIcon = {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = tintOrange,
                            labelColor = brand,
                            leadingIconContentColor = brand
                        )
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.onCategorySelect(null) }) {
                        Text("清除选择", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // 行二：备选分类横向滚动（首帧空，AnimatedVisibility 防两步渲染）
                AnimatedVisibility(visible = sortedCats.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sortedCats.forEach { cat ->
                            FilterChip(
                                selected = viewModel.categoryId == cat.id,
                                onClick = { viewModel.onCategorySelect(cat.id) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(categoryDotColor(cat.colorKey), CircleShape)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(cat.name)
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = tintOrange.copy(alpha = 0.15f),
                                    selectedLabelColor = brand,
                                    selectedLeadingIconColor = brand
                                )
                            )
                        }
                    }
                }
            }

            // ===== 表单区：三张独立卡片 =====

            // 卡片 1：物品状态
            FormCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "物品状态",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ItemStatus.entries.forEach { status ->
                            FilterChip(
                                selected = viewModel.status == status,
                                onClick = { viewModel.onStatusSelect(status) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(statusDotColor(status), CircleShape)
                                    )
                                },
                                label = { Text(status.label, style = MaterialTheme.typography.bodySmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = tintOrange.copy(alpha = 0.15f),
                                    selectedLabelColor = brand,
                                    selectedLeadingIconColor = brand
                                )
                            )
                        }
                    }
                }
            }

            // 卡片 2：购买日期 + 名称 + 金额
            FormCard {
                Column {
                    // 购买日期
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "购买日期",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = { showDatePicker = true }) {
                            Text(
                                dateFormat.format(Date(viewModel.purchaseDate)),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    // 输入物品名称（左标签 + 右输入）
                    LabeledInputRow(
                        label = "物品名称",
                        value = viewModel.name,
                        onValueChange = viewModel::onNameChange,
                        placeholder = "输入物品名称",
                        keyboardType = KeyboardType.Text
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    // 物品金额（左标签 + 右输入）
                    LabeledInputRow(
                        label = "物品金额",
                        value = viewModel.purchasePriceText,
                        onValueChange = viewModel::onPurchasePriceChange,
                        placeholder = "物品金额",
                        keyboardType = KeyboardType.Decimal
                    )
                }
            }

            // 卡片 3：附加费用 + 收益金额
            FormCard {
                Column {
                    // 附加费用标题行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "附加费用",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Filled.AddCircle, null)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    MoneySubRow(
                        label = "买配件",
                        value = viewModel.accessoryCostText,
                        onValueChange = viewModel::onAccessoryCostChange
                    )
                    Spacer(Modifier.height(8.dp))
                    MoneySubRow(
                        label = "维修费用",
                        value = viewModel.repairCostText,
                        onValueChange = viewModel::onRepairCostChange
                    )
                    Spacer(Modifier.height(16.dp))
                    // 收益金额
                    Text(
                        "收益金额",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    MoneySubRow(
                        label = "二手变现",
                        value = viewModel.resaleValueText,
                        onValueChange = viewModel::onResaleValueChange
                    )
                }
            }

            // 校验错误提示
            viewModel.errorMessage?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    // 自定义分类弹窗（复用 Screen 2.3 组件）
    if (showCustomDialog) {
        CustomCategoryDialog(
            existingNames = categories.map { it.name },
            onConfirm = { trimmedName ->
                scope.launch {
                    val db = WujiDatabaseProvider.get(context)
                    val newCat = CategoryEntity(
                        name = trimmedName,
                        colorKey = "custom",
                        isPreset = false,
                        sortOrder = (categories.maxOfOrNull { it.sortOrder } ?: -1) + 1
                    )
                    val newId = db.categoryDao().insert(newCat)
                    showCustomDialog = false
                    viewModel.onCategorySelect(newId)
                }
            },
            onDismiss = { showCustomDialog = false }
        )
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

/** 表单卡片容器：白底圆角 + 软阴影 + 细描边，内边距 16dp。三张表单卡片共用。 */
@Composable
private fun FormCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(WujiRadius.cardLg),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(WujiRadius.cardLg),
                ambientColor = Color.Black.copy(alpha = 0.02f),
                spotColor = Color.Black.copy(alpha = 0.02f)
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(WujiRadius.cardLg)
            )
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/** 顶部图片占位区：显示当前分类图标（未分类用通用图标），右下角相机角标；点击留待 Step 14。 */
@Composable
private fun ImagePlaceholder(categoryKey: String?) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(WujiRadius.cardLg))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    RoundedCornerShape(WujiRadius.cardLg)
                )
                .clickable { /* Step 14：进入 2.5 上传图片页 */ },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                categoryIcon(categoryKey),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(36.dp)
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .offset((-2).dp, (-2).dp)
                    .background(BrandOrange, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AddAPhoto,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/** 左标签 + 右输入行：用于物品名称/金额，左侧字段名 + 右侧输入框 + × 清除按钮。 */
@Composable
private fun LabeledInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    placeholder,
                    textAlign = TextAlign.End,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(
                textAlign = TextAlign.End,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            trailingIcon = if (value.isNotEmpty()) {
                {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else null,
            colors = transparentTextFieldColors(),
            modifier = Modifier.weight(1f)
        )
    }
}

/** 附加费用 / 收益金额的子金额行：浅底圆角容器，左侧标签 + 右侧右对齐数字输入。 */
@Composable
private fun MoneySubRow(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(WujiRadius.md)
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = TextStyle(
                textAlign = TextAlign.End,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            placeholder = {
                Text(
                    "0.00",
                    textAlign = TextAlign.End,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            colors = transparentTextFieldColors(),
            modifier = Modifier.width(120.dp)
        )
    }
}

/** 状态点语义色：使用中绿、未使用灰、已售出橙（固定语义，不随主题翻转）。 */
private fun statusDotColor(status: ItemStatus): Color = when (status) {
    ItemStatus.IN_USE -> Color(0xFF22C55E)
    ItemStatus.UNUSED -> Color(0xFF9E9E9E)
    ItemStatus.SOLD -> BrandOrange
}

/** 无边框输入样式：透明容器 + 透明指示线，仅保留光标。 */
@Composable
private fun transparentTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
)
