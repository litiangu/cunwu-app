package com.litiangu.wuji.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

/**
 * 模式 C（紧凑列表）陈列馆格（Step 44），规格 §4.3 / §4.4。
 *
 * 结构：96×96 方图（图主角，Fit 完整显示，分类色 tint 底）+ 60dp 文案区
 * （名称 labelMedium 12/700 加粗 + 价格 labelSmall 11/500 弱化灰），单格总高 156dp。
 *
 * 有意不放：分类色点（图本身承担识别）、已陪伴天数（无空间）、圆头像（破坏方阵节奏）。
 * 圆角 16dp（`ItemCardRadius.gallery`）与左滑操作区 `cornerRadius` 同值。
 */
@Composable
fun ItemExhibitCell(
    item: ItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    privacyMasked: Boolean = false,
) {
    val visual = categoryVisual(item.categoryKey)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(ItemCardRadius.gallery),
                ambientColor = Color.Black.copy(alpha = 0.03f),
                spotColor = Color.Black.copy(alpha = 0.03f),
            )
            .clip(RoundedCornerShape(ItemCardRadius.gallery))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick),
    ) {
        // 图区：96×96 方图（与列宽一致），Fit 完整显示，底色承担分类识别
        val cellBitmap = decodeImagePath(item.displayImagePath())
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ItemGalleryLayout.imageSize)
                .background(visual.background),
            contentAlignment = Alignment.Center,
        ) {
            if (cellBitmap != null) {
                Image(
                    bitmap = cellBitmap.asImageBitmap(),
                    contentDescription = item.name,
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
        // 文案区：固定 60dp，名称加粗提升检索性，价格弱化为灰色补充信息
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(ItemGalleryLayout.contentHeight)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (privacyMasked) PRIVACY_MASK else "¥%,.2f".format(item.netAsset),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
