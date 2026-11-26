package com.litiangu.wuji.ui.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litiangu.wuji.ui.format.formatMoney
import com.litiangu.wuji.ui.home.ItemCardRadius
import com.litiangu.wuji.ui.home.ItemListRail
import com.litiangu.wuji.ui.home.ItemListRow
import com.litiangu.wuji.ui.home.ItemListThumb
import com.litiangu.wuji.ui.home.ItemUiModel
import com.litiangu.wuji.ui.home.PRIVACY_MASK
import com.litiangu.wuji.ui.item.rememberThumbnail
import com.litiangu.wuji.ui.theme.Ink
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 模式 A（默认列表）左栏：日期锚 + 节点通道。
 *
 * 与卡片拆成两个组件，目的是让日期栏在左滑时保持固定：
 * 整行一起位移会把日期与时间轴节点推出屏幕，列表结构随之断裂。
 * 本组件只占左侧 34 + 18dp 两条窄列，高度与卡片同为 [ItemListRow.height]，
 * 因此节点圆心天然对齐组块竖线与卡片垂直中心。
 *
 * 竖线**不在本组件绘制**：由 HomeScreen 组块 `Box.drawBehind` 统一画全高（跨行缝连续），
 * 节点画在行内容层，天然盖在线上。
 */
@Composable
fun TimelineRail(
    item: ItemUiModel,
    showDateAnchor: Boolean,
    modifier: Modifier = Modifier,
) {
    val visual = categoryVisual(item.categoryKey)
    Row(
        modifier = modifier.height(ItemListRow.height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 日期锚（34dp，内容右对齐；仅当日首行显示）
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
    }
}

/**
 * 模式 A（默认列表）右卡：缩略图 + 名称 / 价格 + 陪伴 chip。
 *
 * 卡本体不做位移，左滑手势与位移由外层 `SwipeableItemCard` 承担，
 * 并由其按行左缘裁切，卡片滑出时不会盖住固定的日期栏。
 * 圆角与左滑操作区同为 [ItemCardRadius.listRow]。
 */
@Composable
fun TimelineCard(
    item: ItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    privacyMasked: Boolean = false,
) {
    val visual = categoryVisual(item.categoryKey)
    Row(
        modifier = modifier
            .fillMaxWidth()
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
        // 后台按 56dp 降采样解码 + 内存缓存（见 ui/item/ThumbnailLoader.kt），不再主线程整图解码
        val thumb = rememberThumbnail(item.displayImagePath(), ItemListThumb.size)
        val itemBitmap = thumb.bitmap
        // [修复19-C] 抠图态用 surfaceContainerLowest（深色模式=#0E0D0E 黑），与深色页面统一。
        // 白边贴纸在黑底上对比强（白线 vs 黑），深色主体靠白边勾勒轮廓。修复11-UI 原意（避免分类色透出）由
        // 此统一黑底达成，避免在深色页面里浮一块浅灰底。
        val isCutout = item.displayImagePath() == item.croppedImagePath
        val thumbBg = if (isCutout) MaterialTheme.colorScheme.surfaceContainerLowest
                      else visual.background
        Box(
            modifier = Modifier
                .size(ItemListThumb.size)
                .clip(RoundedCornerShape(ItemListThumb.radius))
                .background(thumbBg),
            contentAlignment = Alignment.Center,
        ) {
            if (itemBitmap != null) {
                Image(
                    bitmap = itemBitmap,
                    contentDescription = item.name,
                    // Fit：物品完整显示不裁切（Crop 会按短边裁掉 TV/bike 等横图），空白由分类色 tint 底补齐
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = thumb.alpha },
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
                    text = if (privacyMasked) PRIVACY_MASK else formatMoney(item.netAsset),
                    // 陪伴 chip 占固定宽度，金额按剩余空间自动降字号，万元级不会被截断。
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 12.sp,
                        maxFontSize = 18.sp,
                        stepSize = 0.5.sp,
                    ),
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
                        // 第三档：陪伴天数属于可略过的信息，退到 tertiary 让价格与名称先被读到。
                        color = Ink.tertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val dayFormat = SimpleDateFormat("d", Locale.CHINA)
private val weekdayFormat = SimpleDateFormat("E", Locale.CHINA)

private fun dayOf(epochMillis: Long): String = dayFormat.format(Date(epochMillis))

private fun weekdayOf(epochMillis: Long): String = weekdayFormat.format(Date(epochMillis))
