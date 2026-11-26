package com.litiangu.wuji.ui.format

import java.util.Locale
import kotlin.math.abs

/**
 * 全应用统一的金额显示规则：千分位分组 + 固定 2 位小数。
 *
 * 统一入口的意义在于口径一致：首页、分析页、卡片、详情、预算都用同一套四舍五入与分组，
 * 避免同一个数值在不同页面出现 ¥12,434 与 ¥12,434.1 两种写法。
 * 计算仍用 Double（数据库字段），只在展示层格式化。
 */
fun formatMoney(value: Double): String {
    val sign = if (value < 0) "-" else ""
    return sign + "¥" + String.format(Locale.US, "%,.2f", abs(value))
}

/** 带方向符号的金额，用于收入/支出、增减值等需要明确正负的场景。 */
fun formatMoneyWithSign(value: Double): String {
    val sign = if (value < 0) "-" else "+"
    return sign + "¥" + String.format(Locale.US, "%,.2f", abs(value))
}

/**
 * 不带货币符号的金额，只保留千分位与 2 位小数。
 * 用于「较上月 +2,340.10」这类已经处在资产总额旁边的差额文案，避免重复 ¥。
 */
fun formatMoneyPlain(value: Double): String =
    String.format(Locale.US, "%,.2f", abs(value))
