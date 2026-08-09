package com.litiangu.wuji.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.data.db.CategoryBudgetEntity
import com.litiangu.wuji.data.db.WujiDatabaseProvider
import com.litiangu.wuji.data.settings.SettingsDataStore
import com.litiangu.wuji.ui.designsystem.categoryVisual
import com.litiangu.wuji.ui.designsystem.fireHapticIfEnabled
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSpace
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 预算锚点子页一次性加载态：每日/每月锚点 + 全部分类 + 已存分类预算。 */
private data class BudgetAnchorLoad(
    val daily: Double,
    val monthly: Double,
    val categories: List<com.litiangu.wuji.data.db.CategoryEntity>,
    val budgets: List<CategoryBudgetEntity>,
)

/**
 * 预算锚点编辑子页（Step 20 起，Step 21 增强）。
 *
 * - 每日预算基准：健康分 D2 价值维度比对的每日基准金额，默认 ¥100。
 * - 每月预算基准（Step 21 L1）：分析页「预算遵从」预警用，默认 0 = 未配置（留空不限制）。
 * - 分类月度预算（Step 21 L2）：直接在当前页以卡片形式列出全部分类，逐类设每月上限；
 *   留空 = 该类不设限额，分析页回落到全局月度预算。
 *
 * 本页只做「编辑 + 持久化」，不改动健康分算法。导航沿用 MainActivity 的布尔状态机（showBudgetAnchor），
 * 以全屏 overlay 叠在设置页之上；返回时仅关闭本页，设置页原样露出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetAnchorScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val settings = remember { SettingsDataStore(context) }
    val viewModel = remember { SettingsViewModel(settings) }
    val db = remember { WujiDatabaseProvider.get(context) }
    val scope = rememberCoroutineScope()

    // 本地编辑态：每日/每月/分类预算输入框。
    var text by remember { mutableStateOf("") }
    var monthlyText by remember { mutableStateOf("") }
    var anchorLoaded by remember { mutableStateOf(false) }
    var monthlyLoaded by remember { mutableStateOf(false) }
    val draftMap = remember { mutableStateMapOf<Long, String>() }
    var budgetsLoaded by remember { mutableStateOf(false) }

    // 把 DataStore + Room 四条流合成一个状态，等所有数据到齐后一次性渲染。
    // 避免「每日/每月卡先出、分类卡后出」的渐进式布局跳动。
    val loadState by remember(viewModel, db) {
        combine(
            viewModel.budgetAnchor,
            viewModel.monthlyBudgetAnchor,
            db.categoryDao().observeAll(),
            db.categoryBudgetDao().observeAll(),
        ) { daily, monthly, categories, budgets ->
            BudgetAnchorLoad(daily, monthly, categories, budgets)
        }
    }.collectAsStateWithLifecycle(null)

    // 一次性回填：只在「数据已到齐、且尚未回填、且用户未提前输入」时写入，避免覆盖用户输入。
    LaunchedEffect(loadState) {
        loadState?.let { data ->
            if (!anchorLoaded && text.isEmpty()) {
                text = formatAnchor(data.daily)
                anchorLoaded = true
            }
            if (!monthlyLoaded && monthlyText.isEmpty()) {
                monthlyText = if (data.monthly > 0) formatAnchor(data.monthly) else ""
                monthlyLoaded = true
            }
            if (!budgetsLoaded && draftMap.isEmpty()) {
                data.budgets.forEach { draftMap[it.categoryId] = formatAnchor(it.monthlyLimit) }
                budgetsLoaded = true
            }
        }
    }

    val sortedCategories = remember(loadState) {
        loadState?.categories?.sortedWith(compareBy({ it.sortOrder }, { it.id })) ?: emptyList()
    }
    val budgetByCategory = remember(loadState) {
        loadState?.budgets?.associateBy { it.categoryId } ?: emptyMap()
    }

    // 等所有数据 + 本地回填全部完成后，再一次性渲染所有卡片与数值。
    // 避免「卡片先出、数值后填」的跳变，也避免「分类卡比每日/每月卡晚出现」。
    val ready = loadState != null && anchorLoaded && monthlyLoaded && budgetsLoaded

    // 每日解析与校验：必须是合法非负数（不能为空）。
    val parsed = text.toDoubleOrNull()
    val valid = parsed != null && parsed >= 0.0
    // 每月解析与校验：空 = 未配置（合法），否则必须是合法非负数。
    val monthlyParsed = monthlyText.toDoubleOrNull()
    val monthlyValid = monthlyText.isEmpty() || (monthlyParsed != null && monthlyParsed >= 0.0)
    // 分类预算校验：所有非空字段必须能解析为非负数。
    val categoryValid = sortedCategories.all { cat ->
        val draft = draftMap[cat.id].orEmpty()
        draft.isEmpty() || (draft.toDoubleOrNull() != null && draft.toDoubleOrNull()!! >= 0.0)
    }
    val canSave = valid && monthlyValid && categoryValid

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 与设置页对称：CenterAlignedTopAppBar 默认按 status bar inset 留白，不覆盖 windowInsets。
            CenterAlignedTopAppBar(
                title = {
                    // headline-lg：Manrope 24sp / 600（DESIGN.md typography.headline-lg）
                    Text(
                        text = "预算锚点",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    // 与设置页「预算锚点」行图标对齐：右侧装饰性锚点圆钮（纯装饰，保持视觉对称）。
                    Box(
                        modifier = Modifier
                            .padding(end = WujiSpace.sm)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Anchor,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            if (!ready) {
                // 等 DataStore + Room 全部到齐并回填完成后，再一次性渲染，避免卡片/数值逐帧跳变。
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // 可滚动内容区：占满按钮上方所有剩余空间，避免分类卡与保存按钮重叠。
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(WujiSpace.md))

                // 编辑卡：白底 surfaceContainerLowest + 1dp 极轻阴影 + 24dp 圆角（对齐设置页卡片规格）。
                Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 1.dp,
                        shape = RoundedCornerShape(WujiRadius.cardLg),
                        clip = false,
                    ),
                shape = RoundedCornerShape(WujiRadius.cardLg),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // 标签：body-lg（Manrope 16sp / 500）
                    Text(
                        text = "每日预算基准",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(WujiSpace.sm))

                    // 金额输入：¥ 前缀 + 数字键盘 + 右对齐 + 18sp 加粗（对齐 ItemEditScreen 输入规格）。
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        // 输入框内的文字右对齐（只控输入文字）。
                        textStyle = TextStyle(
                            textAlign = TextAlign.End,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        // placeholder 单独给右对齐 + fillMaxWidth，否则占位符会靠左（Step 13 坑）。
                        placeholder = {
                            Text(
                                text = "100",
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        prefix = {
                            // ¥ 前缀：与输入文字同高、灰。
                            Text(
                                text = "¥",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                    Spacer(Modifier.height(WujiSpace.sm))

                    // 说明文字：label-sm（Manrope 12sp / 500 / 灰）。
                    Text(
                        text = "用于健康分价值维度比对，默认 ¥100。",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(WujiSpace.md))

            // 每月预算基准卡（Step 21 L1）：分析页「预算遵从」预警用。
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 1.dp,
                        shape = RoundedCornerShape(WujiRadius.cardLg),
                        clip = false,
                    ),
                shape = RoundedCornerShape(WujiRadius.cardLg),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "每月预算基准",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(WujiSpace.sm))

                    OutlinedTextField(
                        value = monthlyText,
                        onValueChange = { monthlyText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            textAlign = TextAlign.End,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        placeholder = {
                            Text(
                                text = "留空=不限制",
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        prefix = {
                            Text(
                                text = "¥",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                    Spacer(Modifier.height(WujiSpace.sm))

                    Text(
                        text = "用于分析页预算遵从预警；留空表示不限制月度支出。",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(WujiSpace.md))

            // 分类月度预算卡（Step 21 L2）：直接在当前页以卡片形式编辑。
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 1.dp,
                        shape = RoundedCornerShape(WujiRadius.cardLg),
                        clip = false,
                    ),
                shape = RoundedCornerShape(WujiRadius.cardLg),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "分类月度预算",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(WujiSpace.sm))
                    Text(
                        text = "为各分类设置每月支出上限，分析页会按近 30 天支出高亮超支分类。留空表示不设限额。",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(WujiSpace.md))

                    sortedCategories.forEach { cat ->
                        CategoryBudgetRow(
                            category = cat,
                            value = draftMap[cat.id].orEmpty(),
                            onValueChange = { draftMap[cat.id] = it },
                        )
                        if (cat != sortedCategories.lastOrNull()) {
                            Spacer(Modifier.height(WujiSpace.md))
                        }
                    }
                }
            }
            } // 关闭内部可滚动内容区 Column

            // 保存按钮：实心 BrandOrange。每日/每月/分类任一非法时置灰不可点。
            Button(
                onClick = {
                    if (canSave) {
                        // 落库（内部 launch 写 DataStore）。每日必存；每月空=未配置存 0。
                        viewModel.saveBudgetAnchor(parsed!!)
                        viewModel.saveMonthlyBudgetAnchor(if (monthlyText.isEmpty()) 0.0 else monthlyParsed!!)
                        // 分类预算：空=删、非空=upsert。
                        scope.launch {
                            sortedCategories.forEach { cat ->
                                val draft = draftMap[cat.id].orEmpty()
                                if (draft.isEmpty()) {
                                    budgetByCategory[cat.id]?.let {
                                        db.categoryBudgetDao().delete(it.categoryId)
                                    }
                                } else {
                                    val limit = draft.toDoubleOrNull() ?: 0.0
                                    db.categoryBudgetDao().upsert(
                                        CategoryBudgetEntity(categoryId = cat.id, monthlyLimit = limit)
                                    )
                                }
                            }
                        }
                        // 保存 CTA 给轻触感确认（尊重全局震动开关）。
                        context.fireHapticIfEnabled()
                        onBack()
                    }
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(WujiRadius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(
                    text = "保存",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            } // end content (loadState != null)
        }
    }
}

/**
 * 分类月度预算的一行：左侧图标圈 + 名称，右侧金额输入框（空=不限）。
 * 图标圈视觉与新增物品页分类卡片对齐（categoryVisual 取背景色 + 前景色）。
 */
@Composable
private fun CategoryBudgetRow(
    category: com.litiangu.wuji.data.db.CategoryEntity,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val visual = categoryVisual(category.colorKey)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧图标圈：与 CategorySelectScreen 预置分类卡片一致的 48dp 圆。
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(color = visual.background, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                tint = visual.foreground,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(WujiSpace.md))
        Text(
            text = category.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(WujiSpace.md))

        // 金额输入框：较窄，右对齐，placeholder「不限」。
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(132.dp),
            textStyle = TextStyle(
                textAlign = TextAlign.End,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            placeholder = {
                Text(
                    text = "不限",
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            prefix = {
                Text(
                    text = "¥",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}

/** 把 Double 锚点格式化成展示字符串：整数去小数（100.0 → "100"），否则保留原值。 */
private fun formatAnchor(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
