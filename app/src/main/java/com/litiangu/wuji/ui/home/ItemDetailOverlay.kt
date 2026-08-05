package com.litiangu.wuji.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.ui.designsystem.categoryVisual
import com.litiangu.wuji.ui.item.decodeImagePath
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSpace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 物品详情居中模态浮层，对齐 `3.1应用主页-点击物品预览详细页面/screen.png`。
 *
 * 全屏遮罩（点击关闭）+ 中央 24dp 圆角白卡：
 * 右上 分类徽章 + 状态点 + × 关闭；
 * 中央大图位（无图 → 分类图标占位）；名称 + 购入价大字 + 日均胶囊；
 * 三项数据横排等分：陪伴天数 / 购入日期 / 状态。
 */
@Composable
fun ItemDetailOverlay(
    item: ItemUiModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = categoryVisual(item.categoryKey)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(WujiRadius.xxl))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false) { }
                .padding(WujiSpace.lg),
        ) {
            // 顶行：分类徽章 + 状态点 + ×
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(visual.background)
                        .padding(horizontal = WujiSpace.sm, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = visual.icon,
                            contentDescription = null,
                            tint = visual.foreground,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = categoryName(item.categoryKey),
                            style = MaterialTheme.typography.labelSmall,
                            color = visual.foreground,
                        )
                    }
                }
                Spacer(Modifier.width(WujiSpace.sm))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (item.soldDate != null) Color(0xFF9E9E9E) else visual.foreground
                        ),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }
            Spacer(Modifier.height(WujiSpace.md))
            // 大图位（有图显示实际图片，无图回退分类图标占位）
            val detailBitmap = decodeImagePath(item.imagePath)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(192.dp)
                    .clip(RoundedCornerShape(WujiRadius.xl))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                if (detailBitmap != null) {
                    Image(
                        bitmap = detailBitmap.asImageBitmap(),
                        contentDescription = item.name,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = null,
                        tint = visual.foreground,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            Spacer(Modifier.height(WujiSpace.md))
            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            // 价格 + 日均同一水平（日均在右侧，加大样式）
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "¥%,.0f".format(item.purchasePrice),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "日均 ¥%.2f/天".format(dailyDepreciation(item)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(WujiSpace.lg))
            // 三项数据：图标 / 标签（陪伴·购入·状态）/ 数值（385天·日期·使用中）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DetailCell(
                    Icons.Outlined.Schedule,
                    "陪伴",
                    "${item.companionshipDays} 天",
                    iconTint = Color(0xFF0088FF),
                )
                DetailCell(
                    Icons.Outlined.Today,
                    "购入",
                    formatDate(item.purchaseDate),
                    iconTint = Color(0xFFC0392B),
                )
                DetailCell(
                    Icons.Outlined.CheckCircle,
                    "状态",
                    if (item.soldDate != null) "已售出" else "使用中",
                    iconTint = if (item.soldDate != null) Color(0xFF9E9E9E) else Color(0xFF52C41A),
                )
            }
        }
    }
}

@Composable
private fun DetailCell(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun dailyDepreciation(item: ItemUiModel): Double =
    if (item.companionshipDays > 0) item.netAsset / item.companionshipDays else 0.0

// colorKey → 分类名（与分类表预置名称一致）
private fun categoryName(colorKey: String?): String = when (colorKey) {
    "digital" -> "数码"
    "appliance" -> "电器"
    "furniture" -> "家具"
    "tool" -> "工具"
    "sport" -> "运动"
    "traffic" -> "交通"
    "custom" -> "自定义"
    else -> "未分类"
}

private val dateFormatter = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)

private fun formatDate(epochMillis: Long): String = dateFormatter.format(Date(epochMillis))