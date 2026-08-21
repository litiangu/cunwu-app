package com.litiangu.wuji.ui.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.ui.home.ItemUiModel
import com.litiangu.wuji.ui.home.PRIVACY_MASK
import com.litiangu.wuji.ui.item.decodeImagePath
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSize
import com.litiangu.wuji.ui.theme.WujiSpace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 时间轴单行，对齐 `3.0应用主页-显示添加物品后的页面/screen.png`。
 *
 * 左列：竖直连线贯穿 + 40dp 图标圈（分类 tint 底 + 分类色图标，压住竖线）
 * 右卡：名称（左粗体）+ 购入日期「N年M月D日 购入」（右小灰字）；
 *       内嵌浅底卡（logo 占位 + 价格 + 「日均：¥x.xx/天」+ 右上 8dp 分类色点 + 「已陪伴 N 天」）
 */
@Composable
fun TimelineRow(
    item: ItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLine: Boolean = true,
    privacyMasked: Boolean = false,
) {
    val visual = categoryVisual(item.categoryKey)
    // 竖线色在 @Composable 上下文取（drawBehind 内非 @Composable，不能读主题）
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // 时间轴竖线：与左列图标圈中心同轴（48dp 列中心 = 24dp），主题驱动色（深色下自动变暗柔和）
                if (showLine) {
                    val x = 24.dp.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            },
        verticalAlignment = Alignment.Top,
    ) {
        // 左列：图标圈（40dp，水平居中于 48dp 列）；有图时显示实际图片
        val itemBitmap = decodeImagePath(item.displayImagePath())
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(WujiSize.circleButton),
            contentAlignment = Alignment.Center,
        ) {
            if (itemBitmap != null) {
                Image(
                    bitmap = itemBitmap.asImageBitmap(),
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(WujiSize.circleButton)
                        .clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(WujiSize.circleButton)
                        .clip(CircleShape)
                        .background(visual.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = null,
                        tint = visual.foreground,
                        modifier = Modifier.size(WujiSize.iconSm),
                    )
                }
            }
        }
        Spacer(Modifier.width(WujiSpace.sm))
        // 右卡：只留水平 padding，顶部与左图标圈对齐
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(WujiRadius.lg))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .padding(horizontal = WujiSpace.md),
        ) {
            // 顶行：名称 + 购入日期
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatPurchaseLabel(item.purchaseDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(WujiSpace.sm))
            // 内嵌卡：logo 占位 + 价格/日均 + 右上色点 + 已陪伴（白底，与资产卡一致）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(WujiRadius.xl))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // logo 位（有图显示实际图片，无图回退分类图标）
                    if (itemBitmap != null) {
                        Image(
                            bitmap = itemBitmap.asImageBitmap(),
                            contentDescription = item.name,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = visual.icon,
                                contentDescription = null,
                                tint = visual.foreground,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(WujiSpace.md))
                    Column {
                        Text(
                            text = if (privacyMasked) PRIVACY_MASK else "¥%,.2f".format(item.netAsset),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "日均：" + if (privacyMasked) PRIVACY_MASK else "¥%.2f/天".format(dailyDepreciation(item)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Text(
                            text = "已陪伴",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Text(
                            text = "${item.companionshipDays} 天",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }
                // 右上 8dp 分类色点（与「已陪伴」错开，不重叠）
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(visual.foreground),
                )
            }
            // 行间距内化为竖线延伸（左列竖线 fillMaxHeight 覆盖此 40dp，保证多条目竖线连续贯穿）
            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun dailyDepreciation(item: ItemUiModel): Double =
    if (item.companionshipDays > 0) item.netAsset / item.companionshipDays else 0.0

private val dateFormatter = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)

private fun formatPurchaseLabel(epochMillis: Long): String =
    "${dateFormatter.format(Date(epochMillis))} 购入"