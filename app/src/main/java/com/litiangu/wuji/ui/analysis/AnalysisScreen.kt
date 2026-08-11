package com.litiangu.wuji.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.data.db.WujiDatabaseProvider
import com.litiangu.wuji.data.settings.SettingsDataStore
import com.litiangu.wuji.ui.home.PRIVACY_MASK
import com.litiangu.wuji.ui.theme.GradeExcellentLight
import com.litiangu.wuji.ui.theme.GradeFairLight
import com.litiangu.wuji.ui.theme.GradeGoodLight
import com.litiangu.wuji.ui.theme.GradePoorLight
import kotlin.math.roundToInt

// chart-1..4 系列色（固定语义色，按设计不随主题翻转；用评级四色 light 值）
private val ChartSeries = listOf(
    GradeExcellentLight, GradeGoodLight, GradeFairLight, GradePoorLight
)

/**
 * 分析页：健康诊断（圆环+四维拆解）+ 资产结构 + 分类分布 + 月度趋势。
 * 图表前景色固定不随主题；背景/文字走 MaterialTheme（深色下正确翻转）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel = remember {
        val db = WujiDatabaseProvider.get(context)
        AnalysisViewModel(
            db.itemDao(),
            db.categoryDao(),
            db.categoryBudgetDao(),
            SettingsDataStore(context)
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 隐私模式：收集后透传给金额渲染处做脱敏。默认 false = 不脱敏。
    val privacyMasked by viewModel.privacyMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分析") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!state.hasItems) {
                Text(
                    "暂无物品数据，先去添加几件物品吧",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HealthCard(state)
            AssetsCard(state, privacyMasked)
            BudgetCard(state, privacyMasked)
            CategoryDistributionCard(state)
            TrendCard(state)
        }
    }
}

// ===== 健康诊断卡：圆环 + 分数 + 评语 + 四维拆解 =====
@Composable
private fun HealthCard(state: AnalysisUiState) {
    val gradeColor = when (state.gradeLabel) {
        "优" -> GradeExcellentLight
        "良" -> GradeGoodLight
        "中" -> GradeFairLight
        else -> GradePoorLight
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 圆环 + 分数（底环色在 Composable 作用域取，DrawScope 内不能读 MaterialTheme）
            val ringColor = MaterialTheme.colorScheme.surfaceVariant
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                Canvas(modifier = Modifier.size(140.dp)) {
                    val stroke = 12.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val arcTopLeft = Offset(inset, inset)
                    // 底环（随主题）
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    // 前景（固定语义色，不随主题翻转）
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
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "分",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    state.gradeLabel.ifEmpty { "差" },
                    color = gradeColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    state.gradeComment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 四维拆解
            DimensionBar("利用率", state.d1, ChartSeries[0])
            DimensionBar("价值健康", state.d2, ChartSeries[1])
            DimensionBar("流转率", state.d3, ChartSeries[2])
            DimensionBar("变现回收", state.d4, ChartSeries[3])
        }
    }
}

@Composable
private fun DimensionBar(label: String, value: Double, barColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(72.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((value / 100.0).toFloat().coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
        Text(
            "${value.roundToInt()}",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            modifier = Modifier.width(28.dp)
        )
    }
}

// ===== 预算遵从卡（Step 21）：全局月度比对 + 分类限额高亮 =====
// 仅当「全局月度预算已配置」或「存在分类限额」时渲染，护住零配置中性（不污染健康分、不弹无意义预警）。
@Composable
private fun BudgetCard(state: AnalysisUiState, privacyMasked: Boolean = false) {
    val visible = state.monthlyBudget > 0 || state.categoryBudgetRows.isNotEmpty()
    if (!visible) return

    val overGlobal = state.monthlyBudget > 0 && state.recentSpend30 > state.monthlyBudget

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("预算遵从", style = MaterialTheme.typography.titleMedium)

            // 全局月度比对（仅配置了全局月度预算才显示本段）
            if (state.monthlyBudget > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "本月支出 ${if (privacyMasked) PRIVACY_MASK else formatYuan(state.recentSpend30)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "预算 ${if (privacyMasked) PRIVACY_MASK else formatYuan(state.monthlyBudget)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    fontWeight = FontWeight.SemiBold
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.categoryBudgetRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (row.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${if (privacyMasked) PRIVACY_MASK else formatYuan(row.spend)} / ${if (privacyMasked) PRIVACY_MASK else formatYuan(row.limit)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (row.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (row.over) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ===== 资产结构卡：总投入 / 已变现 / 净持有 =====
@Composable
private fun AssetsCard(state: AnalysisUiState, privacyMasked: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("资产结构", style = MaterialTheme.typography.titleMedium)
            // 隐私模式开启时金额统一替换为脱敏占位符，标签（总投入等）保留。
            AssetRow(
                "总投入",
                if (privacyMasked) PRIVACY_MASK else formatYuan(state.totalInput)
            )
            AssetRow(
                "已变现",
                if (privacyMasked) PRIVACY_MASK else formatYuan(state.totalRecovery)
            )
            AssetRow(
                "净持有",
                if (privacyMasked) PRIVACY_MASK else formatYuan(state.netHolding)
            )
            // 回收比是金额衍生指标，隐私模式下一并隐藏为占位符。
            Text(
                "回收比 ${if (privacyMasked) PRIVACY_MASK else "${if (state.totalInput > 0) (state.totalRecovery / state.totalInput * 100).roundToInt() else 0}%"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AssetRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

// ===== 分类分布柱状图 =====
@Composable
private fun CategoryDistributionCard(state: AnalysisUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("分类分布", style = MaterialTheme.typography.titleMedium)
            if (state.categoryBars.isEmpty()) {
                Text(
                    "暂无数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxValue = state.categoryBars.maxOfOrNull { it.value } ?: 0.0
                BarChart(
                    bars = state.categoryBars.map { it.label to it.value },
                    colors = ChartSeries,
                    maxValue = maxValue
                )
            }
        }
    }
}

// ===== 月度趋势（最近 12 个月投入）=====
@Composable
private fun TrendCard(state: AnalysisUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("月度投入趋势", style = MaterialTheme.typography.titleMedium)
            if (state.monthlyTrend.all { it.value <= 0.0 }) {
                Text(
                    "近 12 个月暂无投入记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxValue = state.monthlyTrend.maxOfOrNull { it.value } ?: 0.0
                BarChart(
                    bars = state.monthlyTrend.map { it.label to it.value },
                    colors = listOf(ChartSeries[0]),
                    maxValue = maxValue
                )
            }
        }
    }
}

// 通用柱状图：每根柱按 value/maxValue 定高，颜色循环 series
@Composable
private fun BarChart(
    bars: List<Pair<String, Double>>,
    colors: List<Color>,
    maxValue: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEachIndexed { i, (label, value) ->
            val fraction = if (maxValue > 0) (value / maxValue).toFloat() else 0f
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height((96 * fraction).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(colors[i % colors.size])
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

private fun formatYuan(value: Double): String =
    if (value % 1.0 == 0.0) "¥${value.toLong()}" else "¥%.1f".format(value)
