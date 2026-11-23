@file:OptIn(ExperimentalTextApi::class)

package com.litiangu.wuji.ui.theme

import com.litiangu.wuji.R
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Manrope 是可变字体（单文件内含 wght 200-800 连续字重轴）。
 *
 * 必须用 [FontVariation.Settings] 显式把各 FontWeight 映射到 wght 轴：
 * 只写 `Font(R.font.manrope, FontWeight.Bold)` 不配 variationSettings 时，
 * 渲染永远落在默认实例 wght=400 上，SemiBold/Bold 全部失效（Step 44 验收发现的根因，
 * 症状：名称/价格显虚、粗细无对比）。minSdk 34 > 26，可变轴渲染无兼容问题。
 */
val Manrope = FontFamily(
    Font(
        R.font.manrope,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.manrope,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.manrope,
        FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.manrope,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    // [Step B] 深色专用字重实例。深底浅字会视觉膨胀（irradiation），
    // 深色下 Medium/SemiBold 各降 20 落在 480/580，必须为这两个值单独注册 Font 条目，
    // 否则 Compose 会就近匹配到 500/600，variationSettings 仍是原值，补偿失效。
    Font(
        R.font.manrope,
        FontWeight(480),
        variationSettings = FontVariation.Settings(FontVariation.weight(480)),
    ),
    Font(
        R.font.manrope,
        FontWeight(580),
        variationSettings = FontVariation.Settings(FontVariation.weight(580)),
    ),
)

/**
 * 字阶随明暗切换：[isDark] 为真时标题/强调字重降 20，抵消深底浅字的视觉膨胀。
 * 调用方需 `remember(isDark) { wujiTypography(isDark) }`，避免每次重组重建 Typography。
 *
 * 15 个槽位全部显式定义。之前只填了 8 个，剩下的落回 Material3 默认值，
 * 字体是系统 Roboto 而非 Manrope，字号也各按 M3 规范（headlineLarge 32 / Medium 28 / Small 24），
 * 于是同一个 App 的页面标题出现 24、28、32、36 四种大小和两种字体家族。
 *
 * 现在的六级阶梯：
 *   Display 36 / Page 32 / Sheet 24 / Title 22 / Body 16·14 / Caption 12·11
 * 一级页面标题统一走 headlineLarge（32 Bold），二级页与弹窗走 headlineSmall（24）。
 */
fun wujiTypography(isDark: Boolean): Typography {
    val medium = if (isDark) FontWeight(480) else FontWeight.Medium
    val semibold = if (isDark) FontWeight(580) else FontWeight.SemiBold
    // 金额、计数、日期等数字列用等宽数字，避免逐行宽度跳动；Manrope 未提供 tnum 时该设置被忽略。
    val tabular = "tnum"

    fun slot(
        size: Int,
        lineHeight: Int,
        weight: FontWeight = FontWeight.Normal,
        tabularNumbers: Boolean = false,
    ) = TextStyle(
        fontFamily = Manrope,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontFeatureSettings = if (tabularNumbers) tabular else null,
    )

    return Typography(
        displayLarge = slot(57, 64, FontWeight.Bold),
        displayMedium = slot(45, 52),
        displaySmall = slot(36, 44, FontWeight.Bold, tabularNumbers = true),
        // 一级页面标题的唯一入口：主页、分析、设置、添加。
        headlineLarge = slot(32, 40, FontWeight.Bold),
        headlineMedium = slot(28, 36),
        // 二级页与弹窗标题（表单、详情）。
        headlineSmall = slot(24, 32),
        titleLarge = slot(22, 28, FontWeight.Bold),
        titleMedium = slot(16, 24, semibold),
        titleSmall = slot(14, 20, semibold),
        bodyLarge = slot(16, 24),
        bodyMedium = slot(14, 20),
        bodySmall = slot(12, 16, tabularNumbers = true),
        labelLarge = slot(14, 20, medium),
        labelMedium = slot(12, 16, tabularNumbers = true),
        labelSmall = slot(11, 16, tabularNumbers = true),
    )
}

