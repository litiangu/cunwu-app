package com.litiangu.wuji.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.ui.theme.LocalWujiDarkTheme

/**
 * 卡片描边。
 *
 * 深色下 1dp 阴影落在近黑画布上几乎不可见，卡片的边界只能靠一道亮边勾出来；
 * 浅色下阴影本来够用，但白卡片压在白底上边缘仍会发虚，补一道极淡的黑边定形。
 *
 * 宽度取 0.5dp：视觉上是一条发丝线，不参与布局尺寸，也不与卡片圆角打架。
 */
@Composable
@ReadOnlyComposable
fun cardOutlineStroke(): BorderStroke {
    val color = if (LocalWujiDarkTheme.current) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }
    return BorderStroke(0.5.dp, color)
}
