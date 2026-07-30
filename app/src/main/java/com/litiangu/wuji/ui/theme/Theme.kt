package com.litiangu.wuji.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 浅色方案：以品牌橙为主色，明底
private val LightColorScheme = lightColorScheme(
    primary = BrandOrange,
    secondary = CatAppliance,
    tertiary = CatFurniture,
    background = Color(0xFFFCFCFC),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

// 深色方案：主色保持品牌橙，暗底（来源：深色模式令牌清单 -night）
private val DarkColorScheme = darkColorScheme(
    primary = BrandOrange,
    secondary = CatAppliance,
    tertiary = CatFurniture,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFFFFFFFF),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
)

@Composable
fun WujiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 关闭动态取色，统一使用品牌令牌
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
