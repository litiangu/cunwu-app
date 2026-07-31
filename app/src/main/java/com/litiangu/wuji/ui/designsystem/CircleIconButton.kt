package com.litiangu.wuji.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.litiangu.wuji.ui.theme.WujiSize
import com.litiangu.wuji.ui.theme.tints

/**
 * 参考设计的 40dp 白底圆钮（顶栏三枚 + 筛选栏末尾 ≡）。
 *
 * 对应 CSS：
 * `w-10 h-10 rounded-full bg-surface-container-lowest shadow-sm
 *  flex items-center justify-center text-secondary`
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.secondary
) {
    val shadowColor = tints().cardShadow
    Box(
        modifier = modifier
            .size(WujiSize.circleButton)
            // shadow-sm：小而近的柔和投影
            .shadow(
                elevation = 2.dp0(),
                shape = CircleShape,
                ambientColor = shadowColor,
                spotColor = shadowColor
            )
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(WujiSize.iconSm)
        )
    }
}

// 小工具：避免在多处重复 import androidx.compose.ui.unit.dp
private fun Int.dp0() = androidx.compose.ui.unit.Dp(this.toFloat())
