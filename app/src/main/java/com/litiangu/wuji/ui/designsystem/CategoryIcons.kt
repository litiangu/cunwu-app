package com.litiangu.wuji.ui.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.litiangu.wuji.ui.theme.categoryPalette
import com.litiangu.wuji.ui.theme.TintBlueLight
import com.litiangu.wuji.ui.theme.TintGreenLight
import com.litiangu.wuji.ui.theme.TintOrangeLight

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
 */
@Composable
fun categoryVisual(colorKey: String?): CategoryVisual {
    val cats = categoryPalette()
    // 底色用浅 tint（参考设计：浅色底 + 同色相实色图标），图标圈才看得清
    return when (colorKey) {
        "digital" -> CategoryVisual(Icons.Filled.Devices, cats.digital, TintOrangeLight)
        "appliance" -> CategoryVisual(Icons.Filled.Kitchen, cats.appliance, TintBlueLight)
        "furniture" -> CategoryVisual(Icons.Filled.Chair, cats.furniture, TintGreenLight)
        "tool" -> CategoryVisual(Icons.Filled.Build, cats.tool, TintOrangeLight)
        "traffic" -> CategoryVisual(Icons.Filled.DirectionsCar, cats.traffic, TintBlueLight)
        "sport" -> CategoryVisual(Icons.Filled.FitnessCenter, cats.sport, TintGreenLight)
        else -> CategoryVisual(Icons.Filled.Category, cats.custom, TintOrangeLight)
    }
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
