package com.litiangu.wuji.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// region Light palette
val PrimaryLight = Color(0xFFFF5500)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFD04400)
val OnPrimaryContainerLight = Color(0xFFFFFBFF)
val SecondaryLight = Color(0xFF5F5E5E)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFE2DFDE)
val OnSecondaryContainerLight = Color(0xFF636262)
val TertiaryLight = Color(0xFF5B5C5C)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFF737575)
val OnTertiaryContainerLight = Color(0xFFFCFCFC)
val BackgroundLight = Color(0xFFFAF9F9)
val OnBackgroundLight = Color(0xFF1A1C1C)
val SurfaceLight = Color(0xFFFAF9F9)
val OnSurfaceLight = Color(0xFF1A1C1C)
val SurfaceVariantLight = Color(0xFFE3E2E2)
val OnSurfaceVariantLight = Color(0xFF5C4037)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF4F3F3)
val SurfaceContainerLight = Color(0xFFEFEEED)
val SurfaceContainerHighLight = Color(0xFFE9E8E8)
val SurfaceContainerHighestLight = Color(0xFFE3E2E2)
val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF93000A)
val OutlineLight = Color(0xFF907065)
val OutlineVariantLight = Color(0xFFE5BEB2)
// endregion

// region Dark palette
val PrimaryDark = Color(0xFFFF8A50)
val OnPrimaryDark = Color(0xFF5A1D00)
val PrimaryContainerDark = Color(0xFFD04400)
val OnPrimaryContainerDark = Color(0xFFFFFBFF)
val SecondaryDark = Color(0xFFCCC2DC)
val OnSecondaryDark = Color(0xFFFFFFFF)
val SecondaryContainerDark = Color(0xFF4A4458)
val OnSecondaryContainerDark = Color(0xFFE8DEF8)
val TertiaryDark = Color(0xFFEFB8C8)
val OnTertiaryDark = Color(0xFFFFFFFF)
val TertiaryContainerDark = Color(0xFF633B48)
val OnTertiaryContainerDark = Color(0xFFFBD8E3)
// 深色层级：画布压到近黑，卡片比画布亮 18 级，再往上逐级 +6 / +6 / +10 / +10。
// 层级方向必须是「越靠前景越亮」。早先 surfaceContainerLowest 取 0xFF0E0D0E，
// 比背景 0xFF121212 还暗，卡片在深色下看起来是凹陷的，整页糊成一块黑。
val BackgroundDark = Color(0xFF0A0A0C)
val OnBackgroundDark = Color(0xFFE6E1E5)
val SurfaceDark = Color(0xFF0A0A0C)
val OnSurfaceDark = Color(0xFFE6E1E5)
val SurfaceVariantDark = Color(0xFF3C3C42)
val OnSurfaceVariantDark = Color(0xFFA8A8B0)
val SurfaceContainerLowestDark = Color(0xFF1C1C1F)
val SurfaceContainerLowDark = Color(0xFF222226)
val SurfaceContainerDark = Color(0xFF28282C)
val SurfaceContainerHighDark = Color(0xFF323236)
val SurfaceContainerHighestDark = Color(0xFF3C3C41)
val ErrorDark = Color(0xFFF2B8B5)
val OnErrorDark = Color(0xFFFFFFFF)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerDark = Color(0xFFF9DEDC)
val OutlineDark = Color(0xFF938F99)
val OutlineVariantDark = Color(0xFF3E3E44)
// endregion

// Tints
val TintOrangeLight = Color(0xFFFFE5E0)
val TintBlueLight = Color(0xFFE5F3FF)
val TintGreenLight = Color(0xFFE5F6E5)
val TintOrangeDark = Color(0xFF3A2A26)
val TintBlueDark = Color(0xFF1A2A35)
val TintGreenDark = Color(0xFF1A2E22)

// Brand & category palettes
val BrandOrange = Color(0xFFFF5500)
val BrandOrangeDark = Color(0xFFFF8A50)

// 左滑操作按钮色：固定语义，不随明暗翻转。色值取样自 3.2 参考截图，
// 编辑 #F2A33A（橙黄）、删除 #E6484C（红粉），前景统一纯白保证对比度。
val SwipeActionEdit = Color(0xFFF2A33A)
val SwipeActionDelete = Color(0xFFE6484C)
val OnSwipeAction = Color(0xFFFFFFFF)

// Health grade colors (fixed semantics, not theme-flipped)
val GradeExcellentLight = Color(0xFF2E7D32)
val GradeGoodLight = Color(0xFF1976D2)
val GradeFairLight = Color(0xFFF9A825)
val GradePoorLight = Color(0xFFC62828)

data class CategoryPalette(
    val digital: Color,
    val appliance: Color,
    val furniture: Color,
    val tool: Color,
    val sport: Color,
    val traffic: Color,
    val custom: Color
)

data class GradePalette(
    val excellent: Color,
    val good: Color,
    val fair: Color,
    val poor: Color
)

// 分类色权威映射（逻辑设计文档：数码橙 / 电器蓝 / 家具绿 / 工具黄 / 运动紫 / 交通青 / 自定义灰）
val CategoryDigitalLight = Color(0xFFFF5500)
val CategoryApplianceLight = Color(0xFF0088FF)
val CategoryFurnitureLight = Color(0xFF2E7D32)
val CategoryToolLight = Color(0xFFFFB300)
val CategorySportLight = Color(0xFF9C27B0)
val CategoryTrafficLight = Color(0xFF00BCD4)
val CategoryCustomLight = Color(0xFF9E9E9E)

// 分类前景色暗色变体：在深色 tint 圈底（TintXxxDark）上保证可读性。
// 取值对齐「深色模式令牌清单(-night)」第 2.1 节建议的 cat-* dark。
val CategoryDigitalDark = Color(0xFFFFB59C)
val CategoryApplianceDark = Color(0xFF4DA6FF)
val CategoryFurnitureDark = Color(0xFF66BB6A)
val CategoryToolDark = Color(0xFFFFCA4D)
val CategoryTrafficDark = Color(0xFF4DD0E1)
val CategorySportDark = Color(0xFFCE93D8)
val CategoryCustomDark = Color(0xFFBDBDBD)

val LightCategoryPalette = CategoryPalette(
    digital = CategoryDigitalLight,
    appliance = CategoryApplianceLight,
    furniture = CategoryFurnitureLight,
    tool = CategoryToolLight,
    sport = CategorySportLight,
    traffic = CategoryTrafficLight,
    custom = CategoryCustomLight,
)
val LightGradePalette = GradePalette(
    excellent = BrandOrange,
    good = BrandOrange,
    fair = BrandOrange,
    poor = BrandOrange
)
fun categoryPalette(): CategoryPalette = LightCategoryPalette

// Spacing
object WujiSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}
val bottomBarHeight = 68.dp   // Step 34 胶囊底栏高度（旧值 80.dp，历史遗留别名，无引用）

// Tints with cardShadow
data class TintPalette(
    val primary: Color,
    val blue: Color,
    val green: Color,
    val orange: Color,
    val cardShadow: Color
)
val LightTints = TintPalette(
    PrimaryLight, TintBlueLight, TintGreenLight, BrandOrange, Color(0xFF000000)
)
val DarkTints = TintPalette(
    PrimaryDark, TintBlueDark, TintGreenDark, BrandOrangeDark, Color(0xFF000000)
)
val LocalTints = compositionLocalOf { LightTints }
@Composable
fun tints(): TintPalette = LocalTints.current

// Step 34 新增：当前是否深色主题（WujiTheme 注入，供组件按明暗切换容器层级色）
val LocalWujiDarkTheme = compositionLocalOf { false }

object WujiRadius {
    val lg = 8.dp
    val xl = 16.dp
    val cardLg = 24.dp       // 分类卡片圆角（Screen 2.0 规格）
    val xxl = 24.dp
    val md = 12.dp          // 弹窗/按钮圆角（Screen 2.3 规格）
    val pill = 9999.dp
}

object WujiSize {
    val circleButton = 40.dp
    val iconSm = 20.dp
    val statCardHeight = 112.dp
    val bottomBarHeight = 68.dp        // Step 34 胶囊底栏高度（原 80.dp，改矮）
    val bottomBarFabProtrude = 16.dp   // Step 34 中央 + 按钮凸出胶囊顶部的高度
}


/**
 * 文字三档。
 *
 * primary 是看一眼就要读到的内容；secondary 是补充（副标题、说明）；
 * tertiary 是几乎可以略过的信息（计数、时间戳、图表轴标签）。
 * 只有两档时，次要与次次要会挤在同一层，长页面读起来没有节奏。
 */
object Ink {
    val primary: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface

    val secondary: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

    val tertiary: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
}
