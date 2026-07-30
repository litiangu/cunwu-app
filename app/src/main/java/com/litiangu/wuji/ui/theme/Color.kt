package com.litiangu.wuji.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 品牌主色（设计令牌 primary #FF5500）
val BrandOrange = Color(0xFFFF5500)

// ===== 分类 7 色：亮/暗双值（同色相提亮，保住语义身份） =====
// 暗值对齐《深色模式令牌清单(-night)》2.1 节（cat-active/longest/amber/cyan/purple），
// 与设计文档一致；橙(=品牌主色)取 -night primary 暗 #ff8a50，中性灰按 M3 暗色规则提亮。

// 数码橙（= 品牌主色）
val CatDigitalLight   = Color(0xFFFF5500)
val CatDigitalDark    = Color(0xFFff8a50)
// 电器蓝（对应 cat-active #0088FF → #4da6ff）
val CatApplianceLight = Color(0xFF0088FF)
val CatApplianceDark  = Color(0xFF4da6ff)
// 家具绿（对应 cat-longest #2E7D32 → #66bb6a）
val CatFurnitureLight = Color(0xFF2E7D32)
val CatFurnitureDark  = Color(0xFF66bb6a)
// 工具黄（对应 cat-amber #FFB300 → #ffca4d）
val CatToolLight      = Color(0xFFFFB300)
val CatToolDark       = Color(0xFFffca4d)
// 运动紫（对应 cat-purple #9C27B0 → #ce93d8）
val CatSportLight     = Color(0xFF9C27B0)
val CatSportDark      = Color(0xFFce93d8)
// 交通青（对应 cat-cyan #00BCD4 → #4dd0e1）
val CatTrafficLight   = Color(0xFF00BCD4)
val CatTrafficDark    = Color(0xFF4dd0e1)
// 自定义灰（中性灰在暗底提亮为 #bdbdbd）
val CatCustomLight    = Color(0xFF9E9E9E)
val CatCustomDark     = Color(0xFFbdbdbd)

// ===== 健康分四档评级色：亮/暗双值（同色相提亮） =====
// 亮值按 -night 2.2 节对齐，并修正此前与分类色撞色的偏差：
//   GradeGood 原 #0088FF(与电器蓝撞) → #0095FF；GradeFair 原 #FFB300(与工具黄撞) → #FFCC00；
//   GradePoor 原 M3 error #BA1A1A → 文档 #FF4438。暗值取 -night 2.2 节设定。

val GradeExcellentLight = Color(0xFF34C759) // ≥85 绿
val GradeExcellentDark  = Color(0xFF66bb6a)
val GradeGoodLight      = Color(0xFF0095FF) // 70-84 蓝（按文档修正，避免与电器蓝撞色）
val GradeGoodDark       = Color(0xFF4da6ff)
val GradeFairLight      = Color(0xFFFFCC00) // 50-69 黄（按文档修正，避免与工具黄撞色）
val GradeFairDark       = Color(0xFFffca4d)
val GradePoorLight      = Color(0xFFFF4438) // <50 红（按文档修正）
val GradePoorDark       = Color(0xFFff6b5e)

// ===== 主题感知解析 =====
data class CategoryPalette(
    val digital: Color, val appliance: Color, val furniture: Color,
    val tool: Color, val sport: Color, val traffic: Color, val custom: Color
)

data class GradePalette(
    val excellent: Color, val good: Color, val fair: Color, val poor: Color
)

val LightCategoryPalette = CategoryPalette(
    digital = CatDigitalLight, appliance = CatApplianceLight, furniture = CatFurnitureLight,
    tool = CatToolLight, sport = CatSportLight, traffic = CatTrafficLight, custom = CatCustomLight
)
val DarkCategoryPalette = CategoryPalette(
    digital = CatDigitalDark, appliance = CatApplianceDark, furniture = CatFurnitureDark,
    tool = CatToolDark, sport = CatSportDark, traffic = CatTrafficDark, custom = CatCustomDark
)
val LightGradePalette = GradePalette(
    excellent = GradeExcellentLight, good = GradeGoodLight, fair = GradeFairLight, poor = GradePoorLight
)
val DarkGradePalette = GradePalette(
    excellent = GradeExcellentDark, good = GradeGoodDark, fair = GradeFairDark, poor = GradePoorDark
)

val LocalCategoryPalette = staticCompositionLocalOf { LightCategoryPalette }
val LocalGradePalette = staticCompositionLocalOf { LightGradePalette }

@Composable
fun categoryPalette(): CategoryPalette = LocalCategoryPalette.current

@Composable
fun gradePalette(): GradePalette = LocalGradePalette.current
