package com.litiangu.wuji.ui.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.litiangu.wuji.ui.theme.CategoryApplianceDark
import com.litiangu.wuji.ui.theme.CategoryCustomDark
import com.litiangu.wuji.ui.theme.CategoryDigitalDark
import com.litiangu.wuji.ui.theme.CategoryFurnitureDark
import com.litiangu.wuji.ui.theme.CategorySportDark
import com.litiangu.wuji.ui.theme.CategoryToolDark
import com.litiangu.wuji.ui.theme.CategoryTrafficDark
import com.litiangu.wuji.ui.theme.TintBlueDark
import com.litiangu.wuji.ui.theme.TintBlueLight
import com.litiangu.wuji.ui.theme.TintGreenDark
import com.litiangu.wuji.ui.theme.TintGreenLight
import com.litiangu.wuji.ui.theme.TintOrangeDark
import com.litiangu.wuji.ui.theme.TintOrangeLight
import com.litiangu.wuji.ui.theme.categoryPalette

/**
 * 分类的视觉三件套：图标 / 前景色 / 底色 tint。
 *
 * 参考设计里时间轴图标圈是「浅色底 + 同色相实色图标」的组合
 * （数码=primary-fixed/30 底 + primary 图标，电器=tint-blue 底 + cat-active 图标，
 * 家具=tint-green 底 + cat-longest 图标，无分类=surface-container-low 底 + secondary 图标）。
 *
 * colorKey 与 Room 里 CategoryEntity.colorKey 一致，null 表示未分类。
 */
data class CategoryVisual(
    val icon: ImageVector,
    val foreground: Color,
    val background: Color
)

/** colorKey → 图标。与 Room 种子数据的 6 个预置分类一一对应。 */
fun categoryIcon(colorKey: String?): ImageVector = when (colorKey) {
    "digital" -> Icons.Filled.Devices
    "appliance" -> Icons.Filled.Kitchen
    "furniture" -> Icons.Filled.Chair
    "tool" -> Icons.Filled.Build
    "traffic" -> Icons.Filled.DirectionsCar
    "sport" -> Icons.Filled.FitnessCenter
    else -> Icons.Filled.Category   // 自定义分类与未分类共用
}

/**
 * 取分类的完整视觉。必须在 Composable 里调用（需要读主题调色板）。
 * 底色按参考设计的三种 tint 归组；未覆盖到的分类退化为「主色调低透明度」，
 * 保证自定义分类也有一致观感而不是突兀的纯灰。
 *
 * 深色模式：圈底走 TintXxxDark（避免浅色 tint 在暗底上成亮斑）；
 * 前景色同步切到更亮的暗色变体，保证在深色圈底上可读。
 * 判定沿用项目约定（ItemEditScreen）：按当前 colorScheme 亮度，覆盖跟随系统/强制档。
 */
@Composable
fun categoryVisual(colorKey: String?): CategoryVisual {
    val cats = categoryPalette()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // 圈底：浅色 tint / 暗色 tint 二选一
    val bg = when (colorKey) {
        "digital", "tool" -> if (isDark) TintOrangeDark else TintOrangeLight
        "appliance", "traffic" -> if (isDark) TintBlueDark else TintBlueLight
        "furniture", "sport" -> if (isDark) TintGreenDark else TintGreenLight
        else -> if (isDark) TintOrangeDark else TintOrangeLight
    }
    // 前景：深色下用更亮的暗色变体，避免在深色圈底上对比不足
    val fg = if (isDark) when (colorKey) {
        "digital" -> CategoryDigitalDark
        "appliance" -> CategoryApplianceDark
        "furniture" -> CategoryFurnitureDark
        "tool" -> CategoryToolDark
        "traffic" -> CategoryTrafficDark
        "sport" -> CategorySportDark
        else -> CategoryCustomDark
    } else when (colorKey) {
        "digital" -> cats.digital
        "appliance" -> cats.appliance
        "furniture" -> cats.furniture
        "tool" -> cats.tool
        "traffic" -> cats.traffic
        "sport" -> cats.sport
        else -> cats.custom
    }
    return CategoryVisual(categoryIcon(colorKey), fg, bg)
}

/** 分类点/状态点用的纯色。售出物品在调用侧改用灰色，此处只负责分类语义色。 */
@Composable
fun categoryDotColor(colorKey: String?): Color {
    val cats = categoryPalette()
    return when (colorKey) {
        "digital" -> cats.digital
        "appliance" -> cats.appliance
        "furniture" -> cats.furniture
        "tool" -> cats.tool
        "traffic" -> cats.traffic
        "sport" -> cats.sport
        else -> cats.custom
    }
}
