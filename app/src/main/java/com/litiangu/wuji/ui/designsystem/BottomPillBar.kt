package com.litiangu.wuji.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.ui.theme.BrandOrange
import com.litiangu.wuji.ui.theme.LocalWujiDarkTheme
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSize

    /**
 * 底部胶囊导航栏（Step 34 v16 修订）：
 * - 悬浮胶囊：左右留 96dp 边距（胶囊更窄），圆角 9999dp，68dp 高，8dp 柔和阴影。
 * - 胶囊颜色跟随明暗主题（浅色纯白 / 深色 surfaceContainerHigh），始终浮于页面背景。
 * - 三栏定位改用 Row + weight，避免 align/offset 的歧义：
 *   中间「+」按钮固定 48dp 宽并居中；左右 tab 各占剩余宽度的一半。
 *   左 tab 中心 = (W - FAB) / 4（胶囊左边缘到 + 左边缘间距的一半）。
 *   右 tab 中心 = (3W + FAB) / 4（对称）。
 * - 仅图标，无文字；图标 24dp，衬底 40dp。
 * - 选中态：图标衬在 BrandOrange 15% alpha 圆角矩形背景上，图标变橙色；
 *   图标下方显示 4dp 圆点作为选中指示器，颜色取 onSurface（浅色黑、深色白，始终可见）。
 * - 点击震动保留，ripple 彻底关闭（indication = null），避免灰色方块阴影。
 */
@Composable
fun BottomPillBar(
    items: List<BottomTab>,
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillHeight = WujiSize.bottomBarHeight                 // 68.dp
    val horizontalMargin = 96.dp
    val isDark = LocalWujiDarkTheme.current
    // 浅色用纯白 #FFFFFF，深色用 surfaceContainerHigh，保证胶囊始终亮于页面背景。
    val pillColor = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh
    else MaterialTheme.colorScheme.surfaceContainerLowest
    val pillShape = RoundedCornerShape(WujiRadius.pill)        // 9999.dp
    val context = LocalContext.current

    val fabSize = 48.dp
    val leftTab = items.firstOrNull()
    val rightTab = items.getOrNull(1)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(pillHeight)
                .shadow(
                    elevation = 8.dp,
                    shape = pillShape,
                    spotColor = Color.Black.copy(alpha = 0.14f),
                    ambientColor = Color.Black.copy(alpha = 0.14f),
                )
                .background(pillColor, pillShape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    shape = pillShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Row + weight 布局：中间 FAB 固定 48dp，左右各占剩余宽度一半。
            // 左 tab 中心 = (W - FAB) / 4，右 tab 中心 = (3W + FAB) / 4。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    leftTab?.let { tab ->
                        PillTabItem(
                            tab = tab,
                            selected = tab == selected,
                            onClick = {
                                onSelect(tab)
                                context.fireHapticIfEnabled()
                            },
                        )
                    }
                }

                Box(
                    modifier = Modifier.width(fabSize),
                    contentAlignment = Alignment.Center,
                ) {
                    CenterFabButton(
                        onClick = {
                            onFabClick()
                            context.fireHapticIfEnabled()
                        },
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    rightTab?.let { tab ->
                        PillTabItem(
                            tab = tab,
                            selected = tab == selected,
                            onClick = {
                                onSelect(tab)
                                context.fireHapticIfEnabled()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个 tab 项：仅图标 + 选中态下方小黑点。
 * 图标放大到 24dp，衬底 40dp，让无文字后 still 易于识别。
 */
@Composable
private fun PillTabItem(
    modifier: Modifier = Modifier,
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) BrandOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val iconBg = if (selected) BrandOrange.copy(alpha = 0.15f) else Color.Transparent
    // 指示器用 onSurface：浅色主题下是近黑，深色主题下是近白，保证在两种胶囊底色上都可见。
    val indicatorColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .height(WujiSize.bottomBarHeight)
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            // 图标容器：选中时带橙色浅底
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.label,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            // 选中指示器：4dp 圆点
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
            }
        }
    }
}

/**
 * 中间橙色圆形 + 按钮，48dp，白色图标。
 */
@Composable
private fun CenterFabButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(BrandOrange)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "添加",
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

data class BottomTab(
    val label: String,
    val icon: ImageVector,
)
