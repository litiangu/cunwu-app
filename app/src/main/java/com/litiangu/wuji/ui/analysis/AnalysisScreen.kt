package com.litiangu.wuji.ui.analysis

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
// 注意：Modifier.weight 是 RowScope/ColumnScope 扩展，在 Row/Column 内容里直接用，
// 不要 import androidx.compose.foundation.layout.weight，会撞上同名的 internal 属性（Step 51 教训）。
// 另：IntrinsicSize 也无需 import；Step 51 改用固定高度后已无引用。
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow   // Step 51：窄卡金额超长省略
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit   // Step 51：金额自适应字号
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
 * 排序原则（组间从上到下，组内按「结论 → 事实 → 下钻 → 时间 → 控制 → 明细 → 趣味」）：
 *   A 组 整体概况（首屏）：健康卡（综合结论+最弱维度提示）→ 资产卡（净持有 / 已回流资金）。
 *   B 组 支出与构成：分类资产分布（资产总额的结构下钻）→ 近半年支出趋势（时间维度）→
 *      超支警示条（条件渲染）→ 预算遵从（控制维度，紧贴警示条）。
 *   C 组 明细与趣味：最近变动流水 → 物品荣誉三联 → 页脚隐私声明。
 * 旧顺序把荣誉三联放在第 2 位，把资产卡挤出首屏；本次按信息层级重排，组件实现零改动。
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
    // 滚动状态上提（Step 37）：保留给页面 verticalScroll 与后续按需滚动
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    // 预算卡锚点（重排后预算卡不再位于页尾）：超支警示条点击后按锚点滚到预算卡，
    // 替代原先「滚到底」的写法（滚到底会停在荣誉卡 / 页脚，够不到预算卡）。
    val budgetCardRequester = remember { BringIntoViewRequester() }

    // 进入页面即强制重同步（Step 39 验收修复）：后台 Room 订阅之外的确定性兜底。
    // 实测案例：徽章显示 5 件时 DB 已是 6 行（新增/恢复写入后订阅未驱动刷新），
    // 重启预热才复原。每次进入页强制重读一次（本地计算 <5ms），彻底消除滞留，
    // 对齐逻辑设计文档 §2.1「进入分析页（Tab 切换）→ 重新读取计算」的原始定义。
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // [Step 54] 显式声明 contentWindowInsets。不写时走 M3 默认值（同样是
        // safeDrawing），写出来是为了让「顶栏避让挖孔」这件事从隐式依赖变成显式契约，
        // 避免将来有人改contentWindowInsets 时无声破坏布局。
        contentWindowInsets = WindowInsets.safeDrawing,
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
                    // ===== A 组 整体概况（首屏）：先给综合结论，再给金额事实 =====
                    HealthCard(state)                                   // 纯结果，无金额，不接 privacyMasked
                    AssetsCard(state, privacyMasked)

                    // ===== B 组 支出与构成：结构下钻 → 时间变化 → 控制 =====
                    // 分类分布紧跟资产卡，构成「资产总额 → 分类拆解」的连续下钻链。
                    CategoryDistributionCard(state, privacyMasked)
                    TrendCard(state, privacyMasked)
                    // 警示条紧贴预算卡：旧序它与预算卡隔 6 张卡，点击后要跨屏跳转。
                    OverBudgetBanner(state, privacyMasked) {
                        // 精准滚到预算卡（BringIntoViewRequester），不再滚到页尾。
                        scope.launch { budgetCardRequester.bringIntoView() }
                    }
                    BudgetCard(
                        state = state,
                        privacyMasked = privacyMasked,
                        modifier = Modifier.bringIntoViewRequester(budgetCardRequester),
                    )

                    // ===== C 组 明细与趣味：最细粒度与情感化收尾 =====
                    RecentChangesCard(state, privacyMasked)
                    HonorCard(state.honors, privacyMasked)
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
    // Step 51：卡内 padding 可配。默认 24dp 保持不变；资产结构双卡用 14dp，
    // 因为每张卡只有屏幕宽的一半，留 24dp 边距会挤掉金额显示空间。
    contentPadding: Dp = 24.dp,
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
        Column(modifier = Modifier.padding(contentPadding)) { content() }   // 设计稿卡内 padding 24dp
    }
}

// 双卡标题小字：与 B1 设计稿对齐（净持有 / 已回流资金，13px 常规浅灰层级）。
// 走 labelMedium(12sp) + 常规字重 + onSurfaceVariant，去掉 label-caps 的大写间距感。
// onSurfaceVariant 双主题安全（浅色近似 #999999 层级），不硬编码灰色以兼容深色模式。
@Composable
private fun AnalysisSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Step 51：双卡场景下卡宽只有屏幕一半，标签必须单行，否则会换行撑高卡片。
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ===== 健康卡 v3（Step 52 同心多环重设计）：标题行 + 同心双环 + 权重图例 + 提示行 =====
// 结构：卡顶纯文字标题「物品健康指数」+件数徽章；左侧同心环，每个健康分维度独占一圈，
// 弧长 = 该维度自身分数%（维度数变化时节拍自动收缩，环数随列表长度增减）；
// 右上四档评语（文字本身染等级色，无前缀圆点）；右下权重图例（色点色即环色）；卡底最弱维度提示。
// 权重统一读 DIMENSION_WEIGHTS 单点真相（HealthScore.kt），UI 侧禁止硬编码。

// 环色：外环利用率 / 内环价值健康。与 HealthGrade 四色同源取色，双主题共用（现状 gradeColor 亦为硬编码 hex）
private val HealthRingUsageColor = Color(0xFF34C759)
private val HealthRingValueColor = Color(0xFF0095FF)
private val HealthRingStroke = 10.dp          // 单环线宽（双环后比原单环 8dp 略粗，保有存在感）
private val HealthRingSpacing = 8.dp          // 相邻两环之间的空隙，防粘连
private val HealthRingColumnGap = 14.dp       // 圆环与右侧文字列的间距
private val HealthRightColumnMin = 136.dp     // 右列最小宽：色点10+隙8+「价值健康权重」13sp×6字=78+隙8+百分比约30+余量
                                              // （第二轮校准 122→136：图例放大后窄屏不截断；360dp 屏环让到 122dp）
private val HealthRingMaxSize = 140.dp        // 宽屏上限（第二轮校准：160dp 压到 140dp，把视觉重心让给右列，防左右失衡）
private val HealthRingMinSize = 96.dp         // 极窄屏下限：保右列仍有 122dp，图例不截断

// 一圈环的绘制规格：颜色 + 该维度分数（0~100）
private data class HealthRingSpec(val color: Color, val score: Double)

@Composable
private fun HealthCard(state: AnalysisUiState) {
    val gradeColor = parseHexColor(state.gradeColorHex)
    val ringBgColor = MaterialTheme.colorScheme.surfaceContainerHigh

    AnalysisCardBox {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 标题行：纯文字标题（无图标）+ 件数徽章
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "物品健康指数",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(WujiRadius.pill),
                ) {
                    Text(
                        "${state.itemTotalCount} 件",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            // 主体行：环尺寸随可用宽度自适应。BoxWithConstraints 位于卡内 padding 之后，
            // 故 maxWidth 即内容宽；右列吃满剩余宽度（weight 1f），宽屏更舒展。
            BoxWithConstraints {
                val ringSize = (maxWidth - HealthRingColumnGap - HealthRightColumnMin)
                    .coerceIn(HealthRingMinSize, HealthRingMaxSize)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HealthRings(
                        ringSize = ringSize,
                        score = state.score,
                        rings = listOf(
                            HealthRingSpec(HealthRingUsageColor, state.d1),   // 外环：利用率
                            HealthRingSpec(HealthRingValueColor, state.d2),   // 内环：价值健康
                        ),
                        trackColor = ringBgColor,
                    )
                    Spacer(modifier = Modifier.width(HealthRingColumnGap))
                    Column(
                        modifier = Modifier.weight(1f),
                        // 第二轮校准：行距 16→18dp，右列内容纵向更舒展，与环高呼应
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        // 评语：文字本身染等级色（优绿/良蓝/中黄/差红），无前缀圆点；斜体放大做右列视觉锚点
                        Text(
                            state.gradeComment.ifEmpty { "该唤醒了" },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                            ),
                            color = gradeColor,
                            maxLines = 1,
                        )
                        WeightLegendRow(
                            color = HealthRingUsageColor,
                            name = "利用率权重",
                            weight = DIMENSION_WEIGHTS[0],
                        )
                        WeightLegendRow(
                            color = HealthRingValueColor,
                            name = "价值健康权重",
                            weight = DIMENSION_WEIGHTS[1],
                        )
                    }
                }
            }
            // 行动出口（最弱维度提示）
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

// 同心多环：rings 按「由外到内」顺序排列，每一项画一整圈（轨道 + 前景弧）。
// 半径节拍：最外环贴边 (画布 − 线宽)/2，每往内一环减去「线宽 + 环间距」。
// 弧长 = 该维度分数%（满分即满圈，不设上限缺口）；起点正上方（-90°）顺时针。
@Composable
private fun HealthRings(
    ringSize: Dp,
    score: Int,
    rings: List<HealthRingSpec>,
    trackColor: Color,
) {
    // 每环独立扫入动画：List 长度稳定，逐环 animateFloatAsState 不会错位
    val fractions = rings.map { spec ->
        animateFloatAsState(
            targetValue = (spec.score / 100.0).coerceIn(0.0, 1.0).toFloat(),
            animationSpec = tween(durationMillis = 900),
            label = "healthRingSweep",
        ).value
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ringSize)) {
        Canvas(modifier = Modifier.size(ringSize)) {
            val stroke = HealthRingStroke.toPx()
            val gap = HealthRingSpacing.toPx()
            val canvasPx = ringSize.toPx()
            rings.forEachIndexed { index, spec ->
                val radius = (canvasPx - stroke) / 2f - index * (stroke + gap)
                val diameter = radius * 2f
                val arcTopLeft = Offset((canvasPx - diameter) / 2f, (canvasPx - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                val arcStyle = Stroke(width = stroke, cap = StrokeCap.Round)
                // 轨道底环（随主题）
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = arcStyle,
                )
                // 前景弧（该维度分数）
                if (fractions[index] > 0f) {
                    drawArc(
                        color = spec.color,
                        startAngle = -90f,
                        sweepAngle = 360f * fractions[index],
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = arcStyle,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$score",
                // 环心留空 Ø80dp（136dp 环），字号由 36sp 下调到 28sp 防溢出
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = if (ringSize >= 136.dp) 28.sp else 24.sp,
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
}

// 权重图例：色点色 = 环色（据此判断哪一环对应哪个维度）+ 维度名 + 右对齐权重%
// 第二轮校准：色点 8→10dp、名称 12→13sp、百分比 14→16sp，整体放大一档补足右列视觉重量
@Composable
private fun WeightLegendRow(color: Color, name: String, weight: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "${(weight * 100).roundToInt()}%",
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
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
    // [修复19] 卡片本体与图位统一 surfaceContainerLowest（深色模式=黑底），与分析页其它卡片视觉一致。
    // 抠图「黑圆」由 SmartCutoutEngine.fillHoles 近圆形大洞判定修复（模型 1024 分辨率下 bbox 填充率 ≥0.72 填回）。
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

// ===== 资产结构：左右双卡对称（Step 51）=====
// 原为单卡纵向（标签行 + 三行数据 + 比例条 + 右上角回收率百分比）。
// 两个问题驱动本次改造：
//   1）「回收率 / 回收效率」话术把应用说成二手买卖记账；本应用是物品台账，
//      变现只是物品生命周期结尾的一种处理方式，百分比做主指标会误导用户。
//   2）百分比做主轴会带出「得分感」，本次改成「陈述事实」的金额表达。
// 尺寸纪律：两卡合计宽 + 12dp 间距 = 现有单卡宽（屏幕宽 − 40dp 页面边距），
// 因此双卡共用原来那张卡的宽度，左右边界与上下卡完全对齐，不会越出物理边界。
@Composable
private fun AssetsCard(state: AnalysisUiState, privacyMasked: Boolean) {
    // 定高 170dp 强制双卡有充足的纵向留白，对齐 B1 设计稿「hero 浮在空旷卡里」的视觉。
    // 此前用 IntrinsicSize.Max 让高度跟随内容，结果 4 行卡（净持有）与 5 行卡（回流）
    // 自然高差太小，SpaceBetween 没空隙可分，挤成一条线；改固定高度后两边都通风。
    // 170dp 推导：内容上下边距各 14dp + 5 行内容（label 16 + hero 32 + sub 20 + bar 6 + footnote 16 = 90） + 4 段 12dp 间距（48）= 180。
    // 取 170dp 与 4 行卡对应同样的「4 段间距」，整体节奏感一致，hero 与 bar 间距与设计稿 1:1 比例对应。
    Row(
        modifier = Modifier.height(170.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AssetNetHoldingCard(
            state = state,
            privacyMasked = privacyMasked,
            modifier = Modifier.weight(1f),
        )
        AssetReflowCard(
            state = state,
            privacyMasked = privacyMasked,
            modifier = Modifier.weight(1f),
        )
    }
}

// 左卡：现状。回答「手里还压着多少钱」。
// 顺序：标签 → 金额 hero（橙色主指标）→ 总投入脚注一行 → 结构比例条（总投入 = 100%）。
@Composable
private fun AssetNetHoldingCard(
    state: AnalysisUiState,
    privacyMasked: Boolean,
    modifier: Modifier = Modifier,
) {
    // 已变现占「总投入」的比例，用于比例条分段；总投入为 0 时没有比例可言，直接不画条。
    val recoveryFrac = if (state.totalInput > 0) {
        (state.totalRecovery / state.totalInput).toFloat().coerceIn(0f, 1f)
    } else {
        null
    }
    AssetMiniCard(modifier = modifier) {
        AnalysisSectionLabel("净持有")
        AssetMiniHero(
            value = if (privacyMasked) PRIVACY_MASK else formatYuan(state.netHolding),
            color = MaterialTheme.colorScheme.primary,
        )
        // 标签与金额同一行：窄卡空间有限，标签用短词「总投入」，避免换行。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "总投入",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (privacyMasked) PRIVACY_MASK else formatYuan(state.totalInput),
                // 13sp 与设计稿 13px 脚注金额对齐（headline 之外的次级数据）。
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        if (recoveryFrac != null) {
            // 结构语义：绿段 = 已变现，橙段 = 净持有，两段相加恰好是总投入。
            AssetRatioBar(
                leadingFraction = recoveryFrac,
                leadingColor = GradeExcellentLight,
                trackColor = MaterialTheme.colorScheme.primary,
                restColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// 右卡：回流。回答「已经回到钱包里多少钱」。
// hero 是金额而不是百分比，百分比降级为副文案「占在持投入 X%」。
// 金额用中性色 onSurface（不用绿色），避免颜色暗示「这就更好」。
@Composable
private fun AssetReflowCard(
    state: AnalysisUiState,
    privacyMasked: Boolean,
    modifier: Modifier = Modifier,
) {
    // 总投入为 0 时无法算比例：百分比文案与进度条都不显示，卡内自然少一行。
    val recoveryPct = if (state.totalInput > 0) {
        (state.totalRecovery / state.totalInput * 100).roundToInt()
    } else {
        null
    }
    AssetMiniCard(modifier = modifier) {
        AnalysisSectionLabel("已回流资金")
        AssetMiniHero(
            value = if (privacyMasked) PRIVACY_MASK else formatYuan(state.totalRecovery),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (recoveryPct != null) {
            Text(
                "占在持投入 $recoveryPct%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (recoveryPct != null) {
            // 进度条语义：灰底 = 总投入，绿段 = 已回流部分，不传 restColor。
            // 轨道色用与设计稿 1:1 的 #F1EFE8（中性浅灰），不要用 surfaceContainerLow：
            // 浅色主题下后者太接近白底，比例感丢失，用户看不到 23% 之外的部分。
            AssetRatioBar(
                leadingFraction = (state.totalRecovery / state.totalInput).toFloat().coerceIn(0f, 1f),
                leadingColor = GradeExcellentLight,
                trackColor = Color(0xFFF1EFE8),
            )
        }
        // 底部说明：交代这笔回流资金来自几件已流转物品，让「钱回来了」落到实物语境。
        // 字号用 labelSmall (11sp) 而非 labelMedium (12sp)：实测设备上 labelMedium 渲染偏大，
        // 比同尺寸的标题/副文案显眼得多；缩一档后与卡片上方文字形成清晰的「主体-脚注」层级。
        // recoveryItemCount == 0 时不显示（避免「来自 0 件」的怪文案）。
        if (state.recoveryItemCount > 0) {
            Text(
                "来自 ${state.recoveryItemCount} 件已流转物品",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// 双卡共同外壳：14dp 内边距 + 等高。verticalArrangement 用固定 13dp 节拍而非 SpaceBetween：
// 4 行卡（净持有）与 5 行卡（回流）共用此布局，若用 SpaceBetween 会按各自内容撑高再分间隙，
// 导致两卡的「比例条」分别落在不同 y 位置，视觉上不水平对齐；改为等距节拍后两卡按相同行高排列，
// 比例条严格对齐。节拍 13dp 推导：5 行内容总高 90dp + 4×13dp 间隙 = 142dp = 170dp − 28dp padding，
// 恰好填满，4 行卡底部留白由默认 Column top-aligned 处理。
@Composable
private fun AssetMiniCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnalysisCardBox(modifier = modifier.fillMaxHeight(), contentPadding = 14.dp) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) { content() }
    }
}

// hero 金额：常规金额直接上 26sp（设计稿 display 层级），超长金额才逐档降字号。
// 降档阈值按 Manrope 数字字宽 ≈ 0.56em 推算，保证 360dp 机型（卡内可用 126dp）不溢出；
// 万一仍超长由 maxLines+Ellipsis 兜底。
@Composable
private fun AssetMiniHero(value: String, color: Color) {
    Text(
        value,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = assetAmountSize(value),
            fontWeight = FontWeight.Bold,
        ),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun assetAmountSize(text: String): TextUnit = when {
    text.length >= 13 -> 16.sp
    text.length >= 11 -> 18.sp
    text.length >= 9 -> 22.sp
    else -> 26.sp
}

// 一条横向比例条。
// restColor 为空 → 剩余部分透明露出 trackColor（进度条语义，右卡用）；
// restColor 非空 → 剩余部分被填满（结构语义，左卡用，绿+橙合计即 100%）。
//
// 注意：Modifier.weight(f) 是「按 f/总权重 占比分配」，不是「占宽度 f%」。
// 若进度条里只有一个 weight(f) 子项（如右卡只有绿色段），总权重 = f，它会拿到
// f/f = 100% 宽度，整条全绿（第三轮 bug 根因）。因此剩余部分必须始终放一个占位子项
// 把权重补到 1：占位项背景用 restColor 填满，或用 Color.Transparent 让轨道色透出。
@Composable
private fun AssetRatioBar(
    leadingFraction: Float,
    leadingColor: Color,
    trackColor: Color,
    restColor: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(trackColor),
    ) {
        if (leadingFraction > 0f) {
            Box(
                modifier = Modifier
                    .weight(leadingFraction)
                    .fillMaxHeight()
                    .background(leadingColor)
            )
        }
        if (leadingFraction < 1f) {
            Box(
                modifier = Modifier
                    .weight(1f - leadingFraction)
                    .fillMaxHeight()
                    .background(restColor ?: Color.Transparent)
            )
        }
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

// ===== 近半年支出趋势：支出额 / 件数 双独立趋势图 + 右上角 pill 切换（Step 50）=====
// Step 50 重构：原「支出折线 + 物品数量副柱」叠画拆为两张同款折线图，切换钮即图例（图例行删除）；
// 环比徽章从标题右上角移到标题下方，数据口径跟随当前页（金额环比 / 件数环比）。
@Composable
private fun TrendCard(state: AnalysisUiState, privacyMasked: Boolean) {
    AnalysisCardBox {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 当前页：0 = 支出额，1 = 件数
            var trendTab by remember { mutableIntStateOf(0) }
            // 标题行：标题居左占满，切换钮居右
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
                TrendMetricToggle(
                    selected = trendTab,
                    onSelect = { trendTab = it },
                )
            }
            // 环比徽章（Step 50 移位）：标题下方左对齐；数据口径跟随切换页
            MomBadge(
                if (trendTab == 0) state.monthlyTrend.map { it.amount }
                else state.monthlyTrend.map { it.itemDelta.toDouble() }
            )
            // 当前页的数据序列（金额看 amount，件数看净变动 itemDelta，可为负）
            val activeValues = if (trendTab == 0) state.monthlyTrend.map { it.amount }
            else state.monthlyTrend.map { it.itemDelta.toDouble() }
            if (activeValues.all { it <= 0.0 }) {
                Text(
                    if (trendTab == 0) "近半年暂无支出数据" else "近半年暂无物品变动",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // 图表（颜色须在 Composable 作用域取值，DrawScope 内不能读 MaterialTheme）
                TrendLineChart(
                    values = activeValues,
                    tooltipText = { idx ->
                        val p = state.monthlyTrend[idx]
                        if (trendTab == 0) {
                            "${p.label} " + if (privacyMasked) PRIVACY_MASK else formatYuan(p.amount)
                        } else {
                            "${p.label} ${p.itemDelta}件"
                        }
                    },
                )
                // X 月份标签：当前月（最后一月）用 primary 色高亮，对齐参考图
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    state.monthlyTrend.forEachIndexed { i, p ->
                        Text(
                            p.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (i == state.monthlyTrend.lastIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

// 双趋势图共用绘制体（Step 50 抽取）：直线段折线 + 面积渐变 + 数据点 + 点击 tooltip。
// 纵轴按 lo/hi 区间归一化（lo 可为负，容纳件数净变动的负值）；调用方保证 values 非空且不全 <= 0。
@Composable
private fun TrendLineChart(values: List<Double>, tooltipText: (Int) -> String) {
    val chartPrimary = MaterialTheme.colorScheme.primary
    val pointInner = MaterialTheme.colorScheme.surfaceContainerLowest
    val lo = minOf(0.0, values.minOf { it })
    val hi = maxOf(values.maxOf { it }, lo + 1.0)
    var selectedIdx by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(values) {
                    detectTapGestures { pos ->
                        val n = values.size
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
            val stepX = (w - pad * 2) / (values.size - 1).coerceAtLeast(1)
            val chartH = h - pad * 2
            // 归一化：数值 → y 像素坐标（与下方 tooltip 的 dp 公式同源）
            fun yOf(v: Double): Float {
                val ratio = ((v - lo) / (hi - lo)).toFloat()
                return pad + chartH * (1f - ratio)
            }
            // 折线路径 + 面积路径
            val path = Path()
            val fillPath = Path()
            values.forEachIndexed { i, v ->
                val x = pad + stepX * i
                val y = yOf(v)
                if (i == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, h - pad)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                if (i == values.lastIndex) {
                    fillPath.lineTo(x, h - pad)
                }
            }
            fillPath.close()
            // 面积渐变（上深下透明）
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
            // 折线 stroke
            drawPath(
                path = path,
                color = chartPrimary,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
            // 数据点（橙点 + 白芯）
            values.forEachIndexed { i, v ->
                drawCircle(chartPrimary, radius = 4.dp.toPx(), center = Offset(pad + stepX * i, yOf(v)))
                drawCircle(pointInner, radius = 1.5.dp.toPx(), center = Offset(pad + stepX * i, yOf(v)))
            }
        }
        // tooltip 气泡：dp 坐标与 Canvas 绘制公式同源；96dp 定宽防文本抖动
        selectedIdx?.let { idx ->
            if (idx in values.indices) {
                val padD = 16.dp
                val n = values.size
                val stepXD = (maxWidth - padD * 2) / (n - 1).coerceAtLeast(1)
                val chartHD = 180.dp - padD * 2
                val ratio = ((values[idx] - lo) / (hi - lo)).toFloat()
                val yD = padD + chartHD * (1f - ratio)
                val xD = padD + stepXD * idx
                val bx = (xD - 48.dp).coerceIn(0.dp, (maxWidth - 96.dp).coerceAtLeast(0.dp))
                val by = (yD - 46.dp).coerceAtLeast(0.dp)
                Surface(
                    modifier = Modifier.offset(x = bx, y = by),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                ) {
                    Text(
                        tooltipText(idx),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

// 右上角指标切换钮（Step 50）：单一容器内嵌两段的分段控件。
// 当前几何（与标题墨高对齐，用户指令）：标题 20sp 中文字形实际高约 17dp，容器缩到同高——
// 容器 17dp（圆角 6dp，内边距 1dp，段间 3dp），白 pill 15dp（内边距 h9/v2，圆角 4dp），文字 10sp。
// 用 Box + clickable（indication = null）而非 Surface(onClick)：后者强制 48dp 最小触控目标，
// 会撑爆小尺寸分段控件（此前尺寸失真的根因）。
@Composable
private fun TrendMetricToggle(selected: Int, onSelect: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(1.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf("支出额", "件数").forEachIndexed { i, label ->
                val isSel = selected == i
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSel) {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(i) }
                        .padding(horizontal = 9.dp, vertical = 2.dp),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = if (isSel) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

// 环比徽章（Step 50 修订）：从趋势卡标题右上角移到标题下方；数据口径改由调用方传入
// （金额页传支出序列，件数页传净变动序列），徽章始终描述当前可见图表。
// 涨=红（支出上升是坏信号）/ 降=绿 / ±0.5% 内持平灰；同色 10% 底 pill + 箭头（形状编码）+ ±%（文字编码），
// 不单靠颜色区分（无障碍要求，对齐逻辑设计文档 §5）。
@Composable
private fun MomBadge(values: List<Double>) {
    val prevAmount = values.dropLast(1).lastOrNull() ?: 0.0
    val lastAmount = values.lastOrNull() ?: 0.0
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

// 超支警示条（Step 37）：解决「最重要的红色信号沉底在最后一张卡」。
// 重排后位置由「健康卡下方」改为「预算卡正上方」：它与预算卡同属支出控制语义，
// 贴邻后点击只跳一张卡即达（旧序隔 6 张卡，需跨屏滚动）。
// errorContainer 语义红底（主题令牌，深色自动翻转）；隐私模式隐藏金额；点击滚动到预算卡。
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

// ===== 预算遵从卡（Step 21 原样保留）=====
// 位置：B 组末尾、超支警示条正下方（重排前是页尾最后一张，与警示条隔 6 张卡）。
@Composable
private fun BudgetCard(
    state: AnalysisUiState,
    privacyMasked: Boolean = false,
    modifier: Modifier = Modifier,          // 供超支警示条按锚点滚到本卡
) {
    val visible = state.monthlyBudget > 0 || state.categoryBudgetRows.isNotEmpty()
    if (!visible) return

    val overGlobal = state.monthlyBudget > 0 && state.recentSpend30 > state.monthlyBudget

    AnalysisCardBox(modifier = modifier) {
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
// 金额格式化：千分位分隔，最多 1 位小数（与设计稿 ¥9,481.6 对齐；整数则去掉 .0）。
// 旧实现「%.1f」只强制 1 位，新 DecimalFormat "#,##0.##" 会保留全部小数位导致「¥9,481.64」
// 过长；这里按旧行为：纯整数显整数，有小数最多 1 位并按 HALF_UP 四舍五入。
// DecimalFormat 受当前 Locale 影响，Android 默认 zh_CN 用半角逗号，符合用户视觉习惯。
private fun formatYuan(value: Double): String {
    val sign = if (value < 0) "-" else ""
    val abs = kotlin.math.abs(value)
    val pattern = if (abs % 1.0 == 0.0) "#,##0" else "#,##0.#"
    val df = java.text.DecimalFormat(pattern)
    df.roundingMode = java.math.RoundingMode.HALF_UP
    return "${sign}¥${df.format(abs)}"
}

// 解析 HealthGrade.colorHex（"#34C759"）为 Color；解析失败回退绿色
private fun parseHexColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color(0xFF34C759)
}
