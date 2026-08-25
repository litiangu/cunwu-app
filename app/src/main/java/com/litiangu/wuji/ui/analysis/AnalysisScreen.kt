package com.litiangu.wuji.ui.analysis

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.domain.DIMENSION_WEIGHTS
import com.litiangu.wuji.ui.designsystem.categoryDotColor
import com.litiangu.wuji.ui.designsystem.fireHapticIfEnabled
import com.litiangu.wuji.ui.home.PRIVACY_MASK
import com.litiangu.wuji.ui.item.decodeImagePath
import com.litiangu.wuji.ui.theme.CategoryApplianceDark
import com.litiangu.wuji.ui.theme.CategoryCustomDark
import com.litiangu.wuji.ui.theme.CategoryDigitalDark
import com.litiangu.wuji.ui.theme.CategoryFurnitureDark
import com.litiangu.wuji.ui.theme.CategorySportDark
import com.litiangu.wuji.ui.theme.CategoryToolDark
import com.litiangu.wuji.ui.theme.CategoryTrafficDark
import com.litiangu.wuji.ui.theme.GradeExcellentLight
import com.litiangu.wuji.ui.theme.GradeFairLight
import com.litiangu.wuji.ui.theme.LocalWujiDarkTheme
import com.litiangu.wuji.ui.theme.WujiRadius
import java.util.Calendar
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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

    // 刷新按钮（Step 35 功能化）：每次点击图标转一整圈（600ms），并行触发强制重读
    val context = LocalContext.current
    var spinTarget by remember { mutableIntStateOf(0) }
    val refreshRotation by animateFloatAsState(
        targetValue = spinTarget * 360f,
        animationSpec = tween(durationMillis = 600),
        label = "refreshSpin",
    )
    // 滚动状态上提（Step 37）：超支警示条点击后滚动到底部预算卡
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
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
                // 刷新按钮（Step 35 功能化）：轻震 + 转一圈 + 强制重读五源重建 UiState
                // （备份还原等场景后保证立即反映最新数据；Room Flow 后台订阅不受影响）
                IconButton(onClick = {
                    context.fireHapticIfEnabled()
                    spinTarget++
                    viewModel.refresh()
                }) {
                    Icon(
                        Icons.Filled.Refresh,
                        "刷新",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.graphicsLayer { rotationZ = refreshRotation },
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
                    OverBudgetBanner(state, privacyMasked) {
                        // 点击滚动到底部预算卡（预算卡是最后一张，滚到底即达）
                        scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                    }
                    HonorCard(state.honors, privacyMasked)
                    AssetsCard(state, privacyMasked)
                    RecentChangesCard(state, privacyMasked)
                    CategoryDistributionCard(state, privacyMasked)
                    TrendCard(state, privacyMasked)
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
            // 行 1.5：四维拆解条（Step 36，默认展开；最弱维度高亮，回答「为什么是这个分」）
            DimensionBars(state)
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

// 四维拆解条（Step 36；Step 37 审计修复改贡献度视角）：
// 条长 = 子分比例（0~100），行尾数字 = 贡献分（子分 × 权重取整），直接回答「为什么是这个分」，
// 消除「91 分但流转率 0」的认知矛盾。权重读 HealthScore.DIMENSION_WEIGHTS 单点真相，禁止硬编码。
// 最弱维度实心 primary 高亮，其余 35% 透明。
@Composable
private fun DimensionBars(state: AnalysisUiState) {
    val dims = listOf(
        "利用率" to state.d1,
        "价值健康" to state.d2,
        "流转率" to state.d3,
        "变现回收" to state.d4,
    )
    val minScore = dims.minOf { it.second }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerLow
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "维度拆解",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "贡献分 = 子分 × 权重",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        dims.forEachIndexed { index, (label, score) ->
            val weakest = score <= minScore
            val barColor = if (weakest) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            }
            val labelColor = if (weakest) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val scoreColor = if (weakest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = labelColor,
                    modifier = Modifier.width(60.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(trackColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((score / 100.0).toFloat().coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(barColor)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "+${(score * DIMENSION_WEIGHTS[index]).roundToInt()}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = scoreColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(32.dp),
                )
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
                primaryLabel = "日均成本最高",
                name = honors.highestDailyValue?.name ?: "—",
                primaryText = if (privacyMasked) PRIVACY_MASK else (honors.highestDailyValue?.primaryText ?: "—"),
                primaryColor = GradeFairLight,
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

// ===== 资产结构卡：总投入 / 已变现 / 净持有（文档 §2.3 口径）+ 堆叠比例条 + 回收率（Step 36）=====
@Composable
private fun AssetsCard(state: AnalysisUiState, privacyMasked: Boolean) {
    AnalysisCardBox {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 标签行 + 回收率（Step 36；Step 37 分色：<20% 灰 / 20~50% 黄 / >50% 绿——颜色不再说谎）
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AnalysisSectionLabel("资产结构", modifier = Modifier.weight(1f))
                if (state.totalInput > 0) {
                    val recoveryPct = state.totalRecovery / state.totalInput * 100
                    val recoveryColor = when {
                        recoveryPct >= 50 -> GradeExcellentLight
                        recoveryPct >= 20 -> GradeFairLight
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        "回收率 ${recoveryPct.roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = recoveryColor,
                    )
                }
            }
            AssetRow(
                "总投入",
                if (privacyMasked) PRIVACY_MASK else formatYuan(state.totalInput),
                emphasized = false,
                dotColor = null,
            )
            AssetRow(
                "已变现",
                if (privacyMasked) PRIVACY_MASK else formatYuan(state.totalRecovery),
                emphasized = false,
                dotColor = GradeExcellentLight,
            )
            AssetRow(
                "净持有",
                if (privacyMasked) PRIVACY_MASK else formatYuan(state.netHolding),
                emphasized = true,
                dotColor = MaterialTheme.colorScheme.primary,
            )
            // 堆叠比例条（Step 36）：总投入=100%，绿段=已变现 + 橙段=净持有；与上行色点对应
            if (state.totalInput > 0) {
                val recoveryFrac = (state.totalRecovery / state.totalInput).toFloat().coerceIn(0f, 1f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    if (recoveryFrac > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(recoveryFrac)
                                .fillMaxHeight()
                                .background(GradeExcellentLight)
                        )
                    }
                    if (recoveryFrac < 1f) {
                        Box(
                            modifier = Modifier
                                .weight(1f - recoveryFrac)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetRow(label: String, value: String, emphasized: Boolean, dotColor: Color? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
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

// ===== 最近变动（Step 36）：买入/卖出流水，诊断视角（钱物如何流入流出）=====
// 买入 -¥（onSurface 中性深色）/ 卖出 +¥（绿=回血正向）；支出不用红，避免与预算卡「红=超支」语义冲突。
@Composable
private fun RecentChangesCard(state: AnalysisUiState, privacyMasked: Boolean) {
    if (state.recentChanges.isEmpty()) return
    AnalysisCardBox {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "最近变动",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            state.recentChanges.forEach { change ->
                RecentChangeRow(change, privacyMasked)
            }
        }
    }
}

@Composable
private fun RecentChangeRow(change: RecentChange, privacyMasked: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 物品图 40dp 圆角底；无图占位
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = decodeImagePath(change.imagePath)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = change.name,
                    modifier = Modifier.size(34.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    Icons.Filled.BrokenImage,
                    null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                change.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 分类 chip：色点 + 名称
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(WujiRadius.pill),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(categoryDotColorAware(change.categoryKey))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            change.categoryLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    change.dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            if (privacyMasked) {
                PRIVACY_MASK
            } else {
                "${if (change.isIncome) "+" else "-"}¥%,.0f".format(change.amount)
            },
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (change.isIncome) GradeExcellentLight else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ===== 分类资产分布：环形图 + 图例（Step 35，替代横向进度条）=====
// 环形：Canvas drawArc stroke 式，扇区 = 分类值/合计×360，起 -90°；环心放合计金额+总件数。
// 图例：色点 + 名称 + 件数 + 金额 + 占比%。金额脱敏、比例按真实值（Step 28 原则）。
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
                // 分类色须在 Composable 作用域解析（DrawScope 内不能读 Composable/主题）
                val segColors = state.categoryBars.map { categoryDotColorAware(it.categoryKey) }
                val totalValue = state.categoryBars.sumOf { it.value }
                val totalCount = state.categoryBars.sumOf { it.count }
                val ringBgColor = MaterialTheme.colorScheme.surfaceContainerHigh
                val centerAmount = if (privacyMasked) PRIVACY_MASK else formatYuan(totalValue)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 环形图 + 环心合计
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(116.dp)) {
                        Canvas(modifier = Modifier.size(116.dp)) {
                            val stroke = 20.dp.toPx()
                            val inset = stroke / 2
                            val arcSize = Size(size.width - stroke, size.height - stroke)
                            val arcTopLeft = Offset(inset, inset)
                            if (totalValue <= 0.0) {
                                // 合计为 0：只画满圈底环
                                drawArc(
                                    color = ringBgColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    topLeft = arcTopLeft,
                                    size = arcSize,
                                    style = Stroke(width = stroke),
                                )
                            } else {
                                var start = -90f
                                state.categoryBars.forEachIndexed { i, bar ->
                                    val sweep = (bar.value / totalValue * 360.0).toFloat()
                                    drawArc(
                                        color = segColors[i],
                                        startAngle = start,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        topLeft = arcTopLeft,
                                        size = arcSize,
                                        style = Stroke(width = stroke),
                                    )
                                    start += sweep
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                centerAmount,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "共${totalCount}件",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // 图例
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.categoryBars.forEachIndexed { i, bar ->
                            val pct = if (totalValue > 0) bar.value / totalValue * 100.0 else 0.0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(segColors[i])
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        bar.label,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                    Text(
                                        "${bar.count}件",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (privacyMasked) PRIVACY_MASK else formatYuan(bar.value),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        "%.0f%%".format(pct),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 分类点色（深色感知，Step 35）：浅色复用 categoryDotColor，深色用 CategoryXxxDark 令牌
// （对齐 Step 18 深色可读性方案；不改动已定型的 categoryDotColor，避免波及首页）
@Composable
private fun categoryDotColorAware(colorKey: String?): Color {
    return if (LocalWujiDarkTheme.current) {
        when (colorKey) {
            "digital" -> CategoryDigitalDark
            "appliance" -> CategoryApplianceDark
            "furniture" -> CategoryFurnitureDark
            "tool" -> CategoryToolDark
            "traffic" -> CategoryTrafficDark
            "sport" -> CategorySportDark
            else -> CategoryCustomDark
        }
    } else {
        categoryDotColor(colorKey)
    }
}

// ===== 近半年支出趋势：6 月折线 + 面积渐变 + 总物品数量副柱 + 数据点（Step 35 增强）=====
// 新增：点击数据点 tooltip（Box overlay + selectedIdx，金额脱敏）+ 底部环比摘要行；
// 副柱 4dp→10dp、alpha 0.2→0.25 提高可读性。
@Composable
private fun TrendCard(state: AnalysisUiState, privacyMasked: Boolean) {
    AnalysisCardBox {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 标题 + 环比徽章（Step 35 修订：环比从底部移到右上角，更易注意到变化）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "近半年支出趋势",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                MomBadge(state.monthlyTrend)
            }
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
                val maxAmount = state.monthlyTrend.maxOf { it.amount }.coerceAtLeast(1.0)
                var selectedIdx by remember { mutableStateOf<Int?>(null) }

                BoxWithConstraints {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .pointerInput(state.monthlyTrend) {
                            detectTapGestures { pos ->
                                val n = state.monthlyTrend.size
                                if (n < 2) return@detectTapGestures
                                val padPx = 16.dp.toPx()
                                val stepPx = ((size.width - padPx * 2) / (n - 1)).coerceAtLeast(1f)
                                val idx = ((pos.x - padPx) / stepPx).roundToInt().coerceIn(0, n - 1)
                                selectedIdx = if (selectedIdx == idx) null else idx
                            }
                        },
                ) {
                    val w = size.width
                    val h = size.height
                    val pad = 16.dp.toPx()
                    val maxDelta = maxOf(state.monthlyTrend.maxOf { it.itemDelta }, 1)
                    val stepX = (w - pad * 2) / (state.monthlyTrend.size - 1).coerceAtLeast(1)
                    val chartH = h - pad * 2

                    // 1. 副柱（总物品数量，半透明；净变动为负时不画）— Step 35 加宽提亮
                    state.monthlyTrend.forEachIndexed { i, p ->
                        if (p.itemDelta <= 0) return@forEachIndexed
                        val barH = chartH * (p.itemDelta.toFloat() / maxDelta)
                        val x = pad + stepX * i - 5.dp.toPx()
                        drawRect(
                            color = chartSecondary.copy(alpha = 0.25f),
                            topLeft = Offset(x, h - pad - barH),
                            size = Size(10.dp.toPx(), barH),
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
                    // tooltip 气泡（Step 35）：dp 坐标与 Canvas 绘制公式同源；96dp 定宽防文本抖动
                    selectedIdx?.let { idx ->
                        if (idx in state.monthlyTrend.indices) {
                            val p = state.monthlyTrend[idx]
                            val padD = 16.dp
                            val n = state.monthlyTrend.size
                            val stepXD = (maxWidth - padD * 2) / (n - 1).coerceAtLeast(1)
                            val chartHD = 180.dp - padD * 2
                            val xD = padD + stepXD * idx
                            val yD = padD + chartHD * (1f - (p.amount / maxAmount).toFloat())
                            val bx = (xD - 48.dp).coerceIn(0.dp, (maxWidth - 96.dp).coerceAtLeast(0.dp))
                            val by = (yD - 46.dp).coerceAtLeast(0.dp)
                            Surface(
                                modifier = Modifier.offset(x = bx, y = by),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                            ) {
                                Text(
                                    "${p.label} " + if (privacyMasked) PRIVACY_MASK else formatYuan(p.amount),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
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

// 环比徽章（Step 35 修订）：置于趋势卡标题右上角。
// 涨=红（支出上升是坏信号）/ 降=绿 / ±0.5% 内持平灰；同色 10% 底 pill + 箭头（形状编码）+ ±%（文字编码），
// 不单靠颜色区分（无障碍要求，对齐逻辑设计文档 §5）。
@Composable
private fun MomBadge(trend: List<TrendPoint>) {
    val prevAmount = trend.dropLast(1).lastOrNull()?.amount ?: 0.0
    val lastAmount = trend.lastOrNull()?.amount ?: 0.0
    if (prevAmount <= 0.0) return
    val momPct = (lastAmount - prevAmount) / prevAmount * 100.0
    val flat = momPct >= -0.5 && momPct <= 0.5
    val momColor = when {
        flat -> MaterialTheme.colorScheme.onSurfaceVariant
        momPct > 0 -> MaterialTheme.colorScheme.error
        else -> GradeExcellentLight
    }
    Surface(
        color = momColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(WujiRadius.pill),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            if (!flat) {
                Icon(
                    if (momPct > 0) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    null,
                    tint = momColor,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                if (flat) "较上月持平" else "较上月 %+.0f%%".format(momPct),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = momColor,
            )
        }
    }
}

// 超支警示条（Step 37）：超支时前置到健康卡下方，解决「最重要的红色信号沉底在第 8 张卡」。
// errorContainer 语义红底（主题令牌，深色自动翻转）；隐私模式隐藏金额；点击滚动到底部预算卡。
// 未超支 / 未配置预算时不渲染（复用 Step 29 首页提示行的条件渲染模式）。
@Composable
private fun OverBudgetBanner(state: AnalysisUiState, privacyMasked: Boolean, onClick: () -> Unit) {
    val visible = state.monthlyBudget > 0 && state.recentSpend30 > state.monthlyBudget
    if (!visible) return
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                Icons.Filled.Warning,
                null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (privacyMasked) {
                    "近30天支出已超预算，点击查看"
                } else {
                    "近30天支出已超预算 ¥%,.0f，点击查看".format(state.recentSpend30 - state.monthlyBudget)
                },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
            )
        }
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

            // 近 30 天滚动口径比对（Step 37 文案勘误：原「本月支出」实为 recentSpend30 近30天口径）
            if (state.monthlyBudget > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "近30天支出 ${if (privacyMasked) PRIVACY_MASK else formatYuan(state.recentSpend30)}",
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
                // 三态进度条（Step 35 修订）：绿（<80% 健康）→ 黄（80~100% 提醒）→ 红（>100% 超支）。
                // 固定语义色明暗通用；正常态弃用 primary 橙（与品牌色混同难辨三态）。
                // 80% 位置画警戒刻度线，用量离警戒线的距离一眼可辨。
                val spendFrac = if (state.monthlyBudget > 0) (state.recentSpend30 / state.monthlyBudget).toFloat() else 0f
                val spendBarColor = when {
                    spendFrac > 1f -> MaterialTheme.colorScheme.error
                    spendFrac >= 0.8f -> GradeFairLight
                    else -> GradeExcellentLight
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(spendFrac.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(spendBarColor)
                    )
                    // 80% 警戒刻度线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        )
                    }
                }
                // 超支徽标：红=已超支，灰=未超支（主题安全，不依赖固定色）
                val badgeText = if (overGlobal) "已超支" else "未超支"
                val badgeColor = if (overGlobal) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                // 徽标 + 日均可花（Step 35）=（月预算 − 近30天支出）/ 当月剩余天数；超支改显「已用 N% · 超出 ¥x」（Step 37）
                val cal = Calendar.getInstance()
                val daysLeft = (cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH)).coerceAtLeast(1)
                val remaining = state.monthlyBudget - state.recentSpend30
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = badgeColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (remaining > 0) {
                        Text(
                            "日均可花 ${if (privacyMasked) PRIVACY_MASK else formatYuan(remaining / daysLeft)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        // 超支幅度可感（Step 37）：「已用 N%」比满红条直观；%% 转义字面百分号
                        val overPct = (state.recentSpend30 / state.monthlyBudget * 100).roundToInt()
                        Text(
                            if (privacyMasked) {
                                "已用 $overPct% · 超出 $PRIVACY_MASK"
                            } else {
                                "已用 $overPct%% · 超出 ¥%,.0f".format(-remaining)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
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
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        // mini 进度条（Step 35 修订）：spend/limit，超支红 / 正常绿（与全局条三态语义一致）
                        val rowFrac = if (row.limit > 0) (row.spend / row.limit).toFloat().coerceIn(0f, 1f) else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(rowFrac)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (row.over) MaterialTheme.colorScheme.error
                                        else GradeExcellentLight
                                    )
                            )
                        }
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
