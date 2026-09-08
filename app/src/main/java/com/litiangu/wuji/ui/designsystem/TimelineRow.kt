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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litiangu.wuji.ui.home.ItemCardRadius
import com.litiangu.wuji.ui.home.ItemListRail
import com.litiangu.wuji.ui.home.ItemListRow
import com.litiangu.wuji.ui.home.ItemListThumb
import com.litiangu.wuji.ui.home.ItemUiModel
import com.litiangu.wuji.ui.home.PRIVACY_MASK
import com.litiangu.wuji.ui.item.decodeImagePath
import com.litiangu.wuji.ui.theme.CutoutContainerBg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 模式 A（默认列表）单行，Step 42 按「日历时间轴」参考稿复刻。
 *
 * 结构：左栏日期锚（当日首行显示大字日 + 周几，同日后续行留空）→ 节点通道（当日首行显示
 * 分类色节点，外圈背景色环把它与竖线隔开）→ 右卡（56 方图圆角 16 贴卡高 + 名称 / 价格 + 陪伴 chip）。
 *
 * 竖线**不在本组件绘制**：由 HomeScreen 组块 `Box.drawBehind` 统一画全高（跨行缝连续），
 * 节点画在行内容层，天然盖在线上。
 *
 * 相比 Step 41：卡内日期删除（规格 §8 的有意偏离 D6，用户拍板），日期语义由组头月份 + 左栏日承担。
 */
@Composable
fun TimelineRow(
    item: ItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    privacyMasked: Boolean = false,
    showDateAnchor: Boolean = true,
) {
    val visual = categoryVisual(item.categoryKey)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ItemListRow.height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左栏：日期锚（34dp，内容右对齐；仅当日首行显示）
        Box(
            modifier = Modifier.width(ItemListRail.dateWidth),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (showDateAnchor) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = dayOf(item.purchaseDate),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = weekdayOf(item.purchaseDate),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // 节点通道（18dp）：当日首行显示节点，圆心与组块竖线同轴
        Box(
            modifier = Modifier.width(ItemListRail.channelWidth),
            contentAlignment = Alignment.Center,
        ) {
            if (showDateAnchor) {
                // 外 12dp 背景色环 + 内 8dp 分类色，覆盖在组块竖线之上
                Box(
                    modifier = Modifier
                        .size(ItemListRail.nodeOuter)
                        .background(MaterialTheme.colorScheme.background, CircleShape)
                        .padding(ItemListRail.nodeRing)
                        .background(visual.foreground, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // 右卡：缩略图 + 双行内容；底 surfaceContainerLowest，圆角与左滑操作区同为 20dp
        Row(
            modifier = Modifier
                .weight(1f)
                .height(ItemListRow.height)
                .clip(RoundedCornerShape(ItemCardRadius.listRow))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .clickable(onClick = onClick)
                .padding(
                    horizontal = ItemListRow.paddingH,
                    vertical = ItemListRow.paddingV,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 缩略图：56 方图圆角 16，贴卡高（80 - 上下 12 = 56 恰好）
            val itemBitmap = decodeImagePath(item.displayImagePath())
            // [修复11-UI] 抠图态用中性底色，避免分类色从楔形区透出
            val isCutout = item.displayImagePath() == item.croppedImagePath
            val thumbBg = if (isCutout) CutoutContainerBg else visual.background
            Box(
                modifier = Modifier
                    .size(ItemListThumb.size)
                    .clip(RoundedCornerShape(ItemListThumb.radius))
                    .background(thumbBg),
                contentAlignment = Alignment.Center,
            ) {
                if (itemBitmap != null) {
                    Image(
                        bitmap = itemBitmap.asImageBitmap(),
                        contentDescription = item.name,
                        // Fit：物品完整显示不裁切（Crop 会按短边裁掉 TV/bike 等横图），空白由分类色 tint 底补齐
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = null,
                        tint = visual.foreground,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            // 文本区：名称 / 价格 + 陪伴 chip（卡内日期已删，见计划 D6）
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (privacyMasked) PRIVACY_MASK else "¥%,.2f".format(item.netAsset),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(ItemCardRadius.listChip))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "已陪伴 ${item.companionshipDays} 天",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private val dayFormat = SimpleDateFormat("d", Locale.CHINA)
private val weekdayFormat = SimpleDateFormat("E", Locale.CHINA)

private fun dayOf(epochMillis: Long): String = dayFormat.format(Date(epochMillis))

private fun weekdayOf(epochMillis: Long): String = weekdayFormat.format(Date(epochMillis))
