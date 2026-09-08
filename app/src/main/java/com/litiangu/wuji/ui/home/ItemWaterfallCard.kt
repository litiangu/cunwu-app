package com.litiangu.wuji.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.ui.designsystem.categoryVisual
import com.litiangu.wuji.ui.item.decodeImagePath
import com.litiangu.wuji.ui.theme.CutoutContainerBg

/**
 * 模式 B（卡片列表）瀑布流卡（Step 43），规格 §3.4 / §3.5。
 *
 * 结构：图区（宽 150dp，高 = 150 ÷ 图片宽高比，封顶 149 / 封底 112）+ 文案区固定 70dp。
 * 图片比例来自 `item.imageAspectRatio`（HomeViewModel 流内读文件头算好），
 * Composable 内零 IO；无图 / 解析失败回退 1.0f → 高度 149dp，图区回退分类色底 + 图标。
 *
 * 圆角 20dp 与左滑操作区 `cornerRadius` 同值（41 教训：不一致会露删除键红底）。
 */
@Composable
fun ItemWaterfallCard(
    item: ItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    privacyMasked: Boolean = false,
) {
    val visual = categoryVisual(item.categoryKey)
    // 图高 = 列宽 ÷ 宽高比，封顶封底（规格 §3.3）；ratio 异常已在 VM 侧回退 1.0f
    val imageHeight = (ItemWaterfallLayout.columnWidth / item.imageAspectRatio)
        .coerceIn(ItemWaterfallLayout.imageMinHeight, ItemWaterfallLayout.imageMaxHeight)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(ItemCardRadius.waterfall),
                ambientColor = Color.Black.copy(alpha = 0.03f),
                spotColor = Color.Black.copy(alpha = 0.03f),
            )
            .clip(RoundedCornerShape(ItemCardRadius.waterfall))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick),
    ) {
        // 图区：高按图片比例浮动（112-149dp）
        val itemBitmap = decodeImagePath(item.displayImagePath())
        // [修复11-UI] 抠图态用中性底色，避免分类色从楔形区透出
        val isCutout = item.displayImagePath() == item.croppedImagePath
        val cardBg = if (isCutout) CutoutContainerBg else visual.background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .background(cardBg),
            contentAlignment = Alignment.Center,
        ) {
            if (itemBitmap != null) {
                Image(
                    bitmap = itemBitmap.asImageBitmap(),
                    contentDescription = item.name,
                    // Fit：完整显示不裁切；四周内缩 8dp 让抠图白边/阴影不贴图区边缘
                    //（贴边在深色 tint 底上会显得被截断，Step 43 用户反馈）
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
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
        // 文案区：固定 70dp（padding v8 + 名称 24 + 间距 4 + 价格行 22）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(ItemWaterfallLayout.contentHeight)
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (privacyMasked) PRIVACY_MASK else "¥%,.2f".format(item.netAsset),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${item.companionshipDays}天",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
