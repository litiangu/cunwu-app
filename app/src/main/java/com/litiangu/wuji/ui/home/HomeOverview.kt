package com.litiangu.wuji.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litiangu.wuji.ui.designsystem.WujiIcons
import com.litiangu.wuji.ui.designsystem.cardOutlineStroke
import com.litiangu.wuji.ui.format.formatMoney
import com.litiangu.wuji.ui.format.formatMoneyPlain
import com.litiangu.wuji.ui.theme.BrandOrange
import com.litiangu.wuji.ui.theme.BrandOrangeDark
import com.litiangu.wuji.ui.theme.TintOrangeDark
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSpace

/**
 * 资产面板：一张卡承载主指标与三个从属指标。
 *
 * 上半区只放资产总额，金额独占一行；下半区用一条分隔线接三个等宽状态列。
 * 这样资产总额拿到整行宽度，不会和图标或陪伴 chip 争空间。
 * 三列都是「标签行 18dp + 数值行 26dp」，数值统一 20sp，同一基线。
 *
 * 月度变化槽位目前留空。HomeOverview 没有历史快照，等数据接入后再按规则渲染。
 */
@Composable
fun HomeOverview(
    overview: HomeOverview,
    modifier: Modifier = Modifier,
    privacyMasked: Boolean = false,
) {
    // 深色判定（沿用项目约定：按当前 colorScheme 亮度，覆盖跟随系统/强制档）
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val brand = if (isDark) BrandOrangeDark else BrandOrange
    val assetIconBg = if (isDark) TintOrangeDark else Color(0xFFF5E2DF)
    // 蓝色在深底上提亮保对比（对齐 -night cat-active dark）
    val accentBlue = if (isDark) Color(0xFF4DA6FF) else Color(0xFF0088FF)
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outlineVariant
    val health = healthColor(overview.healthLabel)
    val shape = RoundedCornerShape(WujiRadius.xxl)
    val deltaText = monthlyDeltaText(overview, privacyMasked)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(cardOutlineStroke(), shape)
            .padding(WujiSpace.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(assetIconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = WujiIcons.Wallet,
                    contentDescription = null,
                    tint = brand,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(WujiSpace.sm))
            Text(
                text = "资产总额",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = neutral,
            )
            Spacer(Modifier.weight(1f))
            if (deltaText != null) {
                Text(
                    text = deltaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = neutral,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = if (privacyMasked) PRIVACY_MASK else formatMoney(overview.netAsset),
            // 金额位数会随资产增长，按整行宽度自动降字号，避免贴边或被裁掉。
            autoSize = TextAutoSize.StepBased(
                minFontSize = 22.sp,
                maxFontSize = 31.sp,
                stepSize = 0.5.sp,
            ),
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 31.sp,
                lineHeight = 36.sp,
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(outline),
        )

        Spacer(Modifier.height(14.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FooterStat(
                    modifier = Modifier.weight(1f),
                    icon = WujiIcons.Package,
                    iconTint = neutral,
                    label = "物品总数",
                    value = "${overview.totalCount}",
                )
                FooterStat(
                    modifier = Modifier.weight(1f),
                    icon = WujiIcons.ChartPie,
                    iconTint = accentBlue,
                    label = "使用状态",
                    value = "%.0f%%".format(overview.useRatePercent),
                    trailing = usageTrailing(overview),
                    trailingColor = accentBlue,
                )
                FooterStat(
                    modifier = Modifier.weight(1f),
                    icon = WujiIcons.ShieldCheck,
                    iconTint = health,
                    label = "消费健康",
                    value = overview.healthLabel,
                    valueColor = health,
                )
            }
            // 分隔线画在两条列间空隙的正中，高度 32dp，不顶满整列。
            Canvas(modifier = Modifier.matchParentSize()) {
                val gap = 16.dp.toPx()
                val col = (size.width - 2 * gap) / 3f
                val lineHeight = 32.dp.toPx()
                val top = (size.height - lineHeight) / 2f
                val stroke = 0.5.dp.toPx()
                listOf(col + gap / 2f, 2 * col + 3 * gap / 2f).forEach { x ->
                    drawLine(
                        color = outline,
                        start = Offset(x, top),
                        end = Offset(x, top + lineHeight),
                        strokeWidth = stroke,
                    )
                }
            }
        }
    }
}

/**
 * 从属指标列：标签行 18dp，数值行 26dp。
 * 数值统一 20sp，同一基线；状态副文案跟在数值后面，不另起一行。
 */
@Composable
private fun FooterStat(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    trailing: String? = null,
    trailingColor: Color = Color.Unspecified,
    valueColor: Color = Color.Unspecified,
) {
    Column(
        modifier = modifier.height(54.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.height(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(modifier = Modifier.height(26.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = if (valueColor == Color.Unspecified) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    valueColor
                },
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            if (trailing != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (trailingColor == Color.Unspecified) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        trailingColor
                    },
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

/** 使用状态的两字短标，保证一列宽度内放得下。 */
private fun usageTrailing(overview: HomeOverview): String? = when {
    overview.totalCount == 0 -> null
    overview.useRatePercent >= 99.5 -> "在用"
    overview.useRatePercent <= 0.5 -> "闲置"
    else -> "部分"
}

/**
 * 月度变化文案。
 * - 空库或隐私模式：不显示，避免反推金额。
 * - 上月末资产为 0 且当前有资产：本月新增，不写「较上月」。
 * - 差额在分位内：与上月持平。
 * - 其余按正负显示，差额不带 ¥，因为主金额已有货币符号。
 */
private fun monthlyDeltaText(overview: HomeOverview, privacyMasked: Boolean): String? {
    if (privacyMasked || overview.totalCount == 0) return null
    val delta = overview.monthlyDelta ?: return null
    if (overview.lastMonthNetAsset == 0.0 && overview.netAsset > 0) {
        return "本月新增 ${formatMoney(overview.netAsset)}"
    }
    return when {
        isMonthlyFlat(delta) -> "与上月持平"
        delta > 0 -> "较上月 +${formatMoneyPlain(delta)}"
        else -> "较上月 -${formatMoneyPlain(delta)}"
    }
}

// 消费健康图标/文字色（文档 §5 色板 + [修复07] 新增「中」档：优绿 / 良蓝 / 中黄 / 差橙 / 空灰）
private fun healthColor(label: String): Color = when (label) {
    "优" -> Color(0xFF52C41A)
    "良" -> Color(0xFF1677FF)
    "中" -> Color(0xFFFFCC00)
    "差" -> Color(0xFFFA8C16)
    else -> Color(0xFFBFBFBF)
}
