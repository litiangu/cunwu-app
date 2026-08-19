package com.litiangu.wuji.ui.analysis

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.ui.designsystem.categoryDotColor
import com.litiangu.wuji.ui.home.PRIVACY_MASK
import com.litiangu.wuji.ui.item.decodeImagePath
import com.litiangu.wuji.ui.theme.GradeExcellentLight
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSize

/**
 * 分析页（Step 32 重设计，对齐 4.0 设计稿；Step 33 底部避让 + 排行榜领奖台）：
 * 顶栏「物品分析 + 刷新圆钮」→ 健康卡（纯结果+最弱维度提示）→ 物品荣誉三联 →
 * 资产结构三栏 → 分类资产分布横向进度条 → 近半年支出趋势（6 月折线+面积+副柱）→
 * 预算遵从（Step 21 原样）→ 页脚隐私声明。
 * 卡片范式对齐 CategorySelectScreen：24dp 圆角 + surfaceContainerLowest 白底 + 1dp 软阴影。
 * 顶栏无返回箭头（4.0 形态），返回路径 = 系统返回键（BackHandler 拦截后调 onClose）。
 * 底部避让与 HomeScreen 完全一致：Scaffold bottomBar 占位，让 innerPadding 自动预留悬浮底栏空间。
 */
@Composable
fun AnalysisScreen(onClose: () -> Unit = {}) {
    // 数据经 AnalysisRepository 预热缓存（WujiApplication 启动时同步读入），VM 仅透传 StateFlow
    val viewModel = remember { AnalysisViewModel() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 隐私模式：收集后透传给金额渲染处做脱敏。默认 false = 不脱敏。
    val privacyMasked by viewModel.privacyMode.collectAsStateWithLifecycle()

    // 顶栏无返回箭头（4.0 设计稿形态），返回路径 = 系统返回键（Step 32 决策 A）
    BackHandler(onBack = onClose)

    // 与 HomeScreen 完全一致：Scaffold bottomBar 占位，让 innerPadding 自动为
    // WujiApp 层悬浮底栏预留空间，避免分析页内容滑动到底时被底栏遮挡。
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WujiSize.bottomBarHeight + 20.dp),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
        ) {
            // ===== 顶栏：物品分析 + 刷新圆钮（4.0 设计稿形态，无返回箭头）=====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "物品分析",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // 刷新按钮：数据由 Room Flow 自动驱动，点击无实际副作用；保留为视觉参照
                IconButton(onClick = { }) {
                    Icon(
                        Icons.Filled.Refresh,
                        "刷新",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // ===== 内容 =====
            if (!state.hasItems) {
                Text(
                    "暂无物品数据，先去添加几件物品吧",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    HealthCard(state)                                   // 纯结果，无金额，不接 privacyMasked
                    HonorCard(state.honors, privacyMasked)
                    AssetsCard(state, privacyMasked)
                    CategoryDistributionCard(state, privacyMasked)
                    TrendCard(state)
                    BudgetCard(state, privacyMasked)
                }
            }

            // ===== 页脚：隐私声明固定小字（Step 32 决策）=====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Lock,
                    null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "所有数据仅保存在本机，不会上传",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

// ===== 卡片视觉范式：24dp 圆角 + surfaceContainerLowest 白底 + 1dp 软阴影 =====
// （对齐 CategorySelectScreen.kt:153-157 设计稿范式；不用 surface，避免与背景同色隐形）
@Composable
private fun AnalysisCardBox(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(WujiRadius.cardLg),
            ),
        shape = RoundedCornerShape(WujiRadius.cardLg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) { content() }   // 设计稿卡内 padding 24dp
    }
}

// 卡内小标签：label-caps 风格（DESIGN.md）
@Composable
private fun AnalysisSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ===== 健康卡：圆环 + 分数 + 评语 + Items 徽章 + 数据基 + 最弱维度提示 =====
// 纯结果卡（第二轮决策去 KPI 摘要），金额全部交给 AssetsCard。
@Composable
private fun HealthCard(state: AnalysisUiState) {
    val gradeColor = parseHexColor(state.gradeColorHex)
    val ringBgColor = MaterialTheme.colorScheme.surfaceContainerHigh

    AnalysisCardBox {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 行 1：圆环 + 右侧文字
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                    Canvas(modifier = Modifier.size(96.dp)) {
                        val stroke = 8.dp.toPx()
                        val inset = stroke / 2
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        val arcTopLeft = Offset(inset, inset)
                        // 底环（随主题）
                        drawArc(
                            color = ringBgColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        // 前景（等级语义色，同源派生自 HealthGrade.colorHex）
                        if (state.score > 0) {
                            drawArc(
                                color = gradeColor,
                                startAngle = -90f,
                                sweepAngle = 360f * state.score / 100f,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${state.score}",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "健康分",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.gradeComment.ifEmpty { "断舍离时刻" },
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                            ),
                            color = gradeColor,
                            maxLines = 1,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Items 圆角徽章
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(WujiRadius.pill),
                        ) {
                            Text(
                                "${state.itemTotalCount} items",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "数据基于所有物品流",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            // 行 2：行动出口（最弱维度提示）
            if (state.weakestHint.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Circle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(6.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        state.weakestHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ===== 物品荣誉三联：中央大图（陪伴最久）+ 左右次卡（日均价值 / 收益最高）=====
// 三张白色圆角背板卡片：中央放大为视觉核心，左右缩小；仅中央名称旁带一枚小奖牌图标，
// 主次关系靠卡片尺寸、图片大小、字号比例与留白自然体现，不再用数字/奖牌圆钮强提示。
@Composable
private fun HonorCard(honors: Honors, privacyMasked: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 「物品排行榜」标题加粗加大（Step 32 验收反馈修订）
        Text(
            text = "物品排行榜",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HonorPod(
                modifier = Modifier.weight(1f),
                height = 164.dp,
                iconSize = 52.dp,
                imagePath = honors.highestDailyValue?.imagePath,
                primaryLabel = "日均价值最高",
                name = honors.highestDailyValue?.name ?: "—",
                primaryText = if (privacyMasked) PRIVACY_MASK else (honors.highestDailyValue?.primaryText ?: "—"),
                primaryColor = MaterialTheme.colorScheme.primary,
                showMedal = false,
            )
            HonorPod(
                modifier = Modifier.weight(1.3f),
                height = 208.dp,
                iconSize = 88.dp,
                imagePath = honors.longestCompanion?.imagePath,
                primaryLabel = "陪伴最久",
                name = honors.longestCompanion?.name ?: "—",
                primaryText = honors.longestCompanion?.primaryText ?: "—",
                primaryColor = MaterialTheme.colorScheme.primary,
                showMedal = true,
            )
            HonorPod(
                modifier = Modifier.weight(1f),
                height = 164.dp,
                iconSize = 52.dp,
                imagePath = honors.highestRecovery?.imagePath,
                primaryLabel = honors.highestRecovery?.primaryLabel ?: "收益最高",
                name = honors.highestRecovery?.name ?: "—",
                primaryText = if (privacyMasked) PRIVACY_MASK else (honors.highestRecovery?.primaryText ?: "—"),
                primaryColor = GradeExcellentLight,
                showMedal = false,
            )
        }
    }
}

@Composable
private fun HonorPod(
    modifier: Modifier = Modifier,
    height: Dp,
    iconSize: Dp,
    imagePath: String?,
    primaryLabel: String,
    name: String,
    primaryText: String,
    primaryColor: Color,
    showMedal: Boolean,                 // 仅中央核心物品为 true，名称旁贴一枚小奖牌图标
) {
    // 白色圆角背板卡片：与页面背景拉开层级，用尺寸/比例/留白体现主次，而非数字或奖牌台。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
            )
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 顶部标签行：中央卡片在标签后再贴一枚小奖牌图标（金色固定语义色，不随主题翻转）。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    primaryLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (showMedal) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.WorkspacePremium,
                        null,
                        tint = Color(0xFFE0A800),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // 物品图：透明 PNG 直接铺在卡片上；无图占位
            val bitmap = decodeImagePath(imagePath)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = name,
                    modifier = Modifier.size(iconSize),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    Icons.Filled.BrokenImage,
                    null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(iconSize / 2),
                )
            }
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                primaryText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = primaryColor,
                maxLines = 1,
            )
        }
    }
}

// ===== 资产结构卡：总投入 / 已变现 / 净持有（文档 §2.3 口径）=====
@Composable
private fun AssetsCard(state: AnalysisUiState, privacyMasked: Boolean) {
    AnalysisCardBox {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AnalysisSectionLabel("资产结构")
            AssetRow(
                "总投入",
                if (privacyMasked) PRIVACY_MASK else formatYuan(state.totalInput),
                emphasized = false,
            )
            AssetRow(
                "已变现",
                if (privacyMasked) PRIVACY_MASK else formatYuan(state.totalRecovery),
                emphasized = false,
            )
            AssetRow(
                "净持有",
                if (privacyMasked) PRIVACY_MASK else formatYuan(state.netHolding),
                emphasized = true,
            )
        }
    }
}

@Composable
private fun AssetRow(label: String, value: String, emphasized: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ===== 分类资产分布：横向进度条 + 分类色 + 金额 =====
@Composable
private fun CategoryDistributionCard(state: AnalysisUiState, privacyMasked: Boolean) {
    AnalysisCardBox {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                "分类资产分布",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.categoryBars.isEmpty()) {
                Text(
                    "暂无数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val maxValue = state.categoryBars.maxOfOrNull { it.value } ?: 0.0
                state.categoryBars.forEach { bar ->
                    val color = categoryDotColor(bar.categoryKey)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                bar.label,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Text(
                                if (privacyMasked) PRIVACY_MASK else formatYuan(bar.value),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        // 横向进度条：填充比例 = 分类值 / maxValue
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            val frac = (if (maxValue > 0) bar.value / maxValue else 0.0)
                                .toFloat()
                                .coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(frac)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(color)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===== 近半年支出趋势：6 月折线 + 面积渐变 + 总物品数量副柱 + 数据点 =====
// 趋势图无 Y 轴金额刻度（设计稿亦无），副柱是物品数量非金额，故不接 privacyMasked。
@Composable
private fun TrendCard(state: AnalysisUiState) {
    AnalysisCardBox {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "近半年支出趋势",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.monthlyTrend.all { it.amount <= 0.0 }) {
                Text(
                    "近半年暂无支出数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // 图例
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(
                        color = MaterialTheme.colorScheme.primary,
                        label = "支出金额",
                        line = true,
                    )
                    LegendDot(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                        label = "总物品数量",
                        line = false,
                    )
                }
                // 图表（颜色须在 Composable 作用域取值，DrawScope 内不能读 MaterialTheme）
                val chartPrimary = MaterialTheme.colorScheme.primary
                val chartSecondary = MaterialTheme.colorScheme.secondary
                val pointInner = MaterialTheme.colorScheme.surfaceContainerLowest
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                ) {
                    val w = size.width
                    val h = size.height
                    val pad = 16.dp.toPx()
                    val maxAmount = state.monthlyTrend.maxOf { it.amount }.coerceAtLeast(1.0)
                    val maxDelta = maxOf(state.monthlyTrend.maxOf { it.itemDelta }, 1)
                    val stepX = (w - pad * 2) / (state.monthlyTrend.size - 1).coerceAtLeast(1)
                    val chartH = h - pad * 2

                    // 1. 副柱（总物品数量，半透明；净变动为负时不画）
                    state.monthlyTrend.forEachIndexed { i, p ->
                        if (p.itemDelta <= 0) return@forEachIndexed
                        val barH = chartH * (p.itemDelta.toFloat() / maxDelta)
                        val x = pad + stepX * i - 2.dp.toPx()
                        drawRect(
                            color = chartSecondary.copy(alpha = 0.2f),
                            topLeft = Offset(x, h - pad - barH),
                            size = Size(4.dp.toPx(), barH),
                        )
                    }
                    // 2. 折线路径 + 面积路径
                    val path = Path()
                    val fillPath = Path()
                    state.monthlyTrend.forEachIndexed { i, p ->
                        val x = pad + stepX * i
                        val y = pad + chartH * (1f - (p.amount / maxAmount).toFloat())
                        if (i == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, h - pad)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                        if (i == state.monthlyTrend.lastIndex) {
                            fillPath.lineTo(x, h - pad)
                        }
                    }
                    fillPath.close()
                    // 3. 面积渐变（上深下透明）
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                chartPrimary.copy(alpha = 0.3f),
                                chartPrimary.copy(alpha = 0f),
                            ),
                            startY = pad,
                            endY = h - pad,
                        ),
                    )
                    // 4. 折线 stroke
                    drawPath(
                        path = path,
                        color = chartPrimary,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                    // 5. 数据点（橙点 + 白芯）
                    state.monthlyTrend.forEachIndexed { i, p ->
                        val x = pad + stepX * i
                        val y = pad + chartH * (1f - (p.amount / maxAmount).toFloat())
                        drawCircle(chartPrimary, radius = 4.dp.toPx(), center = Offset(x, y))
                        drawCircle(pointInner, radius = 1.5.dp.toPx(), center = Offset(x, y))
                    }
                }
                // X 月份标签
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    state.monthlyTrend.forEach { p ->
                        Text(
                            p.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, line: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (line) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(2.dp)
                    .background(color)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ===== 预算遵从卡（Step 21 原样保留，仅位置挪到底部最后一张）=====
@Composable
private fun BudgetCard(state: AnalysisUiState, privacyMasked: Boolean = false) {
    val visible = state.monthlyBudget > 0 || state.categoryBudgetRows.isNotEmpty()
    if (!visible) return

    val overGlobal = state.monthlyBudget > 0 && state.recentSpend30 > state.monthlyBudget

    AnalysisCardBox {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "预算遵从",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 全局月度比对（仅配置了全局月度预算才显示本段）
            if (state.monthlyBudget > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "本月支出 ${if (privacyMasked) PRIVACY_MASK else formatYuan(state.recentSpend30)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "预算 ${if (privacyMasked) PRIVACY_MASK else formatYuan(state.monthlyBudget)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 超支徽标：红=已超支，灰=未超支（主题安全，不依赖固定色）
                val badgeText = if (overGlobal) "已超支" else "未超支"
                val badgeColor = if (overGlobal) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = badgeColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // 分类限额高亮（仅含「已设限额」的分类）
            if (state.categoryBudgetRows.isNotEmpty()) {
                if (state.monthlyBudget > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )
                }
                Text(
                    "分类限额（近 30 天）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.categoryBudgetRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (row.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${if (privacyMasked) PRIVACY_MASK else formatYuan(row.spend)} / ${if (privacyMasked) PRIVACY_MASK else formatYuan(row.limit)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.Bold,
                            color = if (row.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ===== 工具函数 =====
private fun formatYuan(value: Double): String =
    if (value % 1.0 == 0.0) "¥${value.toLong()}" else "¥%.1f".format(value)

// 解析 HealthGrade.colorHex（"#34C759"）为 Color；解析失败回退绿色
private fun parseHexColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color(0xFF34C759)
}
