package com.litiangu.wuji.ui.home

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import com.litiangu.wuji.ui.designsystem.categoryVisual
import com.litiangu.wuji.ui.item.decodeImagePath
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSize
import com.litiangu.wuji.ui.theme.WujiSpace

/**
 * 视图模式 card-2x2 的网格卡：白底卡片（surfaceContainerLowest #FFFFFF）+ 柔和阴影。
 * 内容：分类图标圈 + 名称 + 价格 + 右上色点 + 价格右侧"XX天"。
 * 大图位用分类图标占位（图片链路 Step 12）。
 */
@Composable
fun ItemGridCard(
    item: ItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = categoryVisual(item.categoryKey)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(WujiRadius.xl),
                ambientColor = Color.Black.copy(alpha = 0.03f),
                spotColor = Color.Black.copy(alpha = 0.03f),
            )
            .clip(RoundedCornerShape(WujiRadius.xl))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(WujiSpace.md)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(WujiSpace.sm),
        ) {
            // 图标圈（有图显示实际图片，无图回退分类图标）
            val gridBitmap = decodeImagePath(item.imagePath)
            if (gridBitmap != null) {
                Image(
                    bitmap = gridBitmap.asImageBitmap(),
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
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
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 价格 + 使用天数（XX天置于价格右侧）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(WujiSpace.xs),
            ) {
                Text(
                    text = "¥%,.0f".format(item.purchasePrice),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "${item.companionshipDays}天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        // 右上 8dp 分类色点
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(8.dp)
                .clip(CircleShape)
                .background(visual.foreground)
        )
    }
}

/**
 * 视图模式 list-compact 的紧凑格：白底卡片 + 柔和阴影，3 列，信息密度最高。
 */
@Composable
fun ItemCompactCell(
    item: ItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = categoryVisual(item.categoryKey)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(WujiRadius.lg),
                ambientColor = Color.Black.copy(alpha = 0.03f),
                spotColor = Color.Black.copy(alpha = 0.03f),
            )
            .clip(RoundedCornerShape(WujiRadius.lg))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 图标圈（有图显示实际图片，无图回退分类图标）
        val compactBitmap = decodeImagePath(item.imagePath)
        if (compactBitmap != null) {
            Image(
                bitmap = compactBitmap.asImageBitmap(),
                contentDescription = item.name,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(visual.background),
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
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "¥%,.0f".format(item.purchasePrice),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
