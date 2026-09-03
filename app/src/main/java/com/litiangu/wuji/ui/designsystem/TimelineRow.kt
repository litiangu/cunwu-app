package com.litiangu.wuji.ui.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.litiangu.wuji.ui.home.ItemListAvatar
import com.litiangu.wuji.ui.home.ItemListRow
import com.litiangu.wuji.ui.home.ItemUiModel
import com.litiangu.wuji.ui.home.PRIVACY_MASK
import com.litiangu.wuji.ui.item.decodeImagePath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 模式 A（默认列表）单行，Step 40 按 `首页三视图布局规格_v1.md` §2.3 重做。
 *
 * 相比旧版的三处结构性变化：
 * 1. **删掉时间轴竖线**（原 `drawBehind { drawLine() }`）：时间感改由分组头承担（Step 41）。
 * 2. **删掉内嵌白底卡**：旧版同一张图 decode 两次（左列 40dp + 内嵌卡 40dp），
 *    且内嵌卡塞了日均、分类色点、竖排「已陪伴 / N 天」，信息层级与左列重复。
 * 3. **压成 2 行**：顶行 名称 + 购入日期，底行 价格 + 「已陪伴 N 天」chip，行高固定 80dp。
 *
 * 分类识别完全由左列承担：48dp 圆图 + 1.5dp 分类色描边（`visual.foreground`）。
 */
@Composable
fun TimelineRow(
    item: ItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    privacyMasked: Boolean = false,
) {
    val visual = categoryVisual(item.categoryKey)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ItemListRow.height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左列：56dp 外框内 48dp 圆图 + 1.5dp 分类色描边（分类的唯一识别信号）
        val itemBitmap = decodeImagePath(item.displayImagePath())
        Box(
            modifier = Modifier.size(ItemListAvatar.outerBox),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(ItemListAvatar.size)
                    .border(ItemListAvatar.stroke, visual.foreground, CircleShape)
                    .clip(CircleShape)
                    .background(visual.background),
                contentAlignment = Alignment.Center,
            ) {
                if (itemBitmap != null) {
                    Image(
                        bitmap = itemBitmap.asImageBitmap(),
                        contentDescription = item.name,
                        // Crop：圆内铺满，Fit 会在方形圆里留透明边带露出页面底色
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = null,
                        tint = visual.foreground,
                        modifier = Modifier.size(ItemListAvatar.icon),
                    )
                }
            }
        }
        Spacer(Modifier.width(ItemListAvatar.gap))
        // 右卡：卡片底 + 双行内容，垂直方向用 SpaceBetween 均分剩余空间
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(ItemCardRadius.listRow))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .clickable(onClick = onClick)
                .padding(
                    horizontal = ItemListRow.paddingH,
                    vertical = ItemListRow.paddingV,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // 顶行：名称 + 购入日期（yyyy-MM-dd，无「购入」后缀）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(ItemListRow.paddingH))
                Text(
                    text = formatPurchaseDate(item.purchaseDate),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 底行：价格 + 「已陪伴 N 天」chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (privacyMasked) PRIVACY_MASK else "¥%,.2f".format(item.netAsset),
                    // 规格要 18/600，而 Type.kt 的 titleLarge 是 22/700，显式覆写
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                // chip 底比卡片底抬一档容器色：卡片已是 surfaceContainerLowest，
                // chip 若同色会完全隐形（规格原文写 lowest，此处为可辨性上调一档）
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

private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

private fun formatPurchaseDate(epochMillis: Long): String = dateFormatter.format(Date(epochMillis))
