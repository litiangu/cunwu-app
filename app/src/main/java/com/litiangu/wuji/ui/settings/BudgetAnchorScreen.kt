package com.litiangu.wuji.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.data.settings.SettingsDataStore
import com.litiangu.wuji.domain.DEFAULT_DAILY_BUDGET_ANCHOR
import com.litiangu.wuji.ui.designsystem.fireHapticIfEnabled
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSpace

/**
 * 预算锚点编辑子页（Step 20）。
 *
 * 预算锚点 = 健康分 D2 价值维度比对的每日基准金额，默认 ¥100。
 * 本页只做「编辑 + 持久化」：读 [SettingsViewModel.budgetAnchor] 预填，
 * 写 [SettingsViewModel.saveBudgetAnchor] 落 DataStore。不改动健康分算法、不做分析图表。
 *
 * 导航形态：沿用 MainActivity 的布尔状态机（showBudgetAnchor），以全屏 overlay 叠在设置页之上。
 * 返回时仅关闭本页，设置页原样露出（见 MainActivity）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetAnchorScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val settings = remember { SettingsDataStore(context) }
    val viewModel = remember { SettingsViewModel(settings) }
    // 当前已存值（初始预填用）。collectAsStateWithLifecycle 保证回显落库后的值。
    val currentAnchor by viewModel.budgetAnchor.collectAsStateWithLifecycle(DEFAULT_DAILY_BUDGET_ANCHOR)

    // 输入框文本：初始预填当前值。用户编辑后这里是最新字符串。
    var text by remember(currentAnchor) { mutableStateOf(formatAnchor(currentAnchor)) }

    // 解析与校验：必须是合法非负数。
    val parsed = text.toDoubleOrNull()
    val valid = parsed != null && parsed >= 0.0

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
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

            Spacer(Modifier.weight(1f))

            // 保存按钮：实心 BrandOrange。非法输入时置灰不可点。
            Button(
                onClick = {
                    if (valid) {
                        // 落库（内部 launch 写 DataStore）。
                        viewModel.saveBudgetAnchor(parsed!!)
                        // 保存 CTA 给轻触感确认（尊重全局震动开关）。
                        context.fireHapticIfEnabled()
                        onBack()
                    }
                },
                enabled = valid,
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
            Spacer(Modifier.height(WujiSpace.lg))
        }
    }
}

/** 把 Double 锚点格式化成展示字符串：整数去小数（100.0 → "100"），否则保留原值。 */
private fun formatAnchor(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
