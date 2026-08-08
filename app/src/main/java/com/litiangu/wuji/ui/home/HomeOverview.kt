package com.litiangu.wuji.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DonutSmall
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litiangu.wuji.ui.theme.BrandOrange
import com.litiangu.wuji.ui.theme.TintBlueLight
import com.litiangu.wuji.ui.theme.TintGreenLight
import com.litiangu.wuji.ui.theme.TintOrangeLight
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSize
import com.litiangu.wuji.ui.theme.WujiSpace

/**
 * 2x2 资产概览卡，对齐参考设计 `3.0应用主页-显示添加物品后的页面` 第 1-4 卡。
 *
 * 卡1 资产总额（橙 tint + 钱图标）｜卡2 物品总数（橙 tint + 盒图标）
 * 卡3 使用状态（蓝 tint + 图表图标 + 副标 "正在使用/暂无数据"）
 * 卡4 消费健康（绿 tint + 盾图标 + 绿字 "优/良/中差/—"）
 */
@Composable
fun HomeOverview(
    overview: HomeOverview,
    modifier: Modifier = Modifier,
    privacyMasked: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(WujiSpace.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(WujiSpace.md)) {
            OverviewCard(
                modifier = Modifier.weight(1f),
                tint = Color(0xFFF5E2DF),
                iconTint = BrandOrange,
                icon = Icons.Filled.AccountBalanceWallet,
                title = "资产总额",
                value = if (privacyMasked) PRIVACY_MASK else "¥%,.0f".format(overview.netAsset),
            )
            OverviewCard(
                modifier = Modifier.weight(1f),
                tint = Color(0xFFF5ECEB),
                iconTint = Color(0xFF21110F),
                icon = Icons.Filled.Inventory2,
                title = "物品总数",
                value = "${overview.totalCount}",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WujiSpace.md)) {
            OverviewCard(
                modifier = Modifier.weight(1f),
                tint = TintBlueLight,
                iconTint = Color(0xFF0088FF),
                icon = Icons.Filled.DonutSmall,
                title = "使用状态",
                value = "%.0f%%".format(overview.useRatePercent),
                subLabel = overview.useStateLabel,
                subColor = Color(0xFF0088FF),
            )
            OverviewCard(
                modifier = Modifier.weight(1f),
                tint = TintGreenLight,
                iconTint = healthColor(overview.healthLabel),
                icon = Icons.Filled.HealthAndSafety,
                title = "消费健康",
                value = overview.healthLabel,
                valueColor = healthColor(overview.healthLabel),
            )
        }
    }
}

// 消费健康图标/文字色（文档 §5 四态色板：优绿 / 良蓝 / 差橙黄 / 空灰）
private fun healthColor(label: String): Color = when (label) {
    "优" -> Color(0xFF52C41A)
    "良" -> Color(0xFF1677FF)
    "差" -> Color(0xFFFA8C16)
    else -> Color(0xFFBFBFBF)
}

@Composable
private fun OverviewCard(
    modifier: Modifier = Modifier,
    tint: Color,
    icon: ImageVector,
    iconTint: Color = BrandOrange,
    title: String,
    value: String,
    subLabel: String? = null,
    subColor: Color? = null,
    valueColor: Color? = null,
) {
    Column(
        modifier = modifier
            .height(WujiSize.statCardHeight)
            .clip(RoundedCornerShape(WujiRadius.xxl))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(WujiSpace.md),
    ) {
        // 第一行：左上图标圈 + 右上数值（副标在数值正下方）
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(WujiSize.circleButton)
                    .clip(RoundedCornerShape(50))
                    .background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(WujiSize.iconSm),
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Bold,
                    color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                )
                if (subLabel != null) {
                    Text(
                        text = subLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = subColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // 标题在图标下方
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}