package com.litiangu.wuji.ui.item

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Calendar
import java.util.Locale

private val WEEK_HEADERS = listOf("一", "二", "三", "四", "五", "六", "日")

private data class DayCell(
    val year: Int,
    val month: Int,
    val day: Int,
    val isCurrentMonth: Boolean
)

/**
 * 自绘中文日期选择器。替代 Material 3 DatePicker 的英文 UI，符合中文习惯。
 * - Day 视图：左右箭头切相邻月，点标题进年月双网格。
 * - 点任意日期格直接 onConfirm + onDismiss（便捷直接）。
 */
@Composable
fun ChineseDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val init = Calendar.getInstance(Locale.CHINA).apply { timeInMillis = initialMillis }
    var year by remember { mutableIntStateOf(init.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(init.get(Calendar.MONTH)) }

    // 选中值用 long 保存；切年/月时按原 day 重算（非法 day 回落 1）
    var selectedMillis by remember {
        mutableStateOf(normalizeToMillis(year, month, init.get(Calendar.DAY_OF_MONTH)))
    }
    // false=Day 视图，true=年月双网格视图
    var yearMonthView by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(328.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (!yearMonthView) {
                    DayView(
                        year = year,
                        month = month,
                        selectedMillis = selectedMillis,
                        onPrevMonth = {
                            month = month - 1
                            if (month < 0) { month = 11; year -= 1 }
                            selectedMillis = normalizeToMillis(year, month, dayOf(selectedMillis))
                        },
                        onNextMonth = {
                            month = month + 1
                            if (month > 11) { month = 0; year += 1 }
                            selectedMillis = normalizeToMillis(year, month, dayOf(selectedMillis))
                        },
                        onTitleClick = { yearMonthView = true },
                        onDayClick = { millis ->
                            selectedMillis = millis
                            onConfirm(millis)
                        },
                        onTodayClick = {
                            val t = Calendar.getInstance(Locale.CHINA)
                            val m = normalizeToMillis(
                                t.get(Calendar.YEAR),
                                t.get(Calendar.MONTH),
                                t.get(Calendar.DAY_OF_MONTH)
                            )
                            selectedMillis = m
                            onConfirm(m)
                        }
                    )
                } else {
                    YearMonthView(
                        year = year,
                        month = month,
                        onYearPicked = { y ->
                            year = y
                            selectedMillis = normalizeToMillis(y, month, dayOf(selectedMillis))
                            yearMonthView = false
                        },
                        onMonthPicked = { m ->
                            month = m
                            selectedMillis = normalizeToMillis(year, m, dayOf(selectedMillis))
                            yearMonthView = false
                        },
                        onBack = { yearMonthView = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayView(
    year: Int,
    month: Int,
    selectedMillis: Long,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTitleClick: () -> Unit,
    onDayClick: (Long) -> Unit,
    onTodayClick: () -> Unit
) {
    val cells = remember(year, month) { buildCells(year, month) }
    val today = remember { Calendar.getInstance(Locale.CHINA) }
    val todayY = today.get(Calendar.YEAR)
    val todayM = today.get(Calendar.MONTH)
    val todayD = today.get(Calendar.DAY_OF_MONTH)
    val sel = Calendar.getInstance(Locale.CHINA).apply { timeInMillis = selectedMillis }

    // 导航条：左右箭头切月 + 可点标题进年月双网格
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevMonth) {
            Icon(Icons.Filled.ChevronLeft, "上个月", tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            text = String.format(Locale.CHINA, "%d 年 %d 月", year, month + 1),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onTitleClick)
                .padding(8.dp),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onNextMonth) {
            Icon(Icons.Filled.ChevronRight, "下个月", tint = MaterialTheme.colorScheme.onSurface)
        }
    }

    // 星期表头（周一为首列）
    Row(modifier = Modifier.fillMaxWidth()) {
        for (h in WEEK_HEADERS) {
            Text(
                h,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    // 6×7 日期网格
    cells.chunked(7).forEach { rowCells ->
        Row(modifier = Modifier.fillMaxWidth()) {
            for (cell in rowCells) {
                val isSelected = cell.year == sel.get(Calendar.YEAR) &&
                        cell.month == sel.get(Calendar.MONTH) &&
                        cell.day == sel.get(Calendar.DAY_OF_MONTH)
                val isToday = cell.year == todayY && cell.month == todayM && cell.day == todayD
                DayCellView(
                    cell = cell,
                    isSelected = isSelected,
                    isToday = isToday,
                    onClick = { onDayClick(normalizeToMillis(cell.year, cell.month, cell.day)) }
                )
            }
        }
    }

    // 左下角「今天」块
    Row(modifier = Modifier.fillMaxWidth()) {
        val isTodaySelected = todayY == sel.get(Calendar.YEAR) &&
                todayM == sel.get(Calendar.MONTH) &&
                todayD == sel.get(Calendar.DAY_OF_MONTH)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isTodaySelected) MaterialTheme.colorScheme.primary else Color.Transparent
                )
                .then(
                    if (!isTodaySelected)
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                    else Modifier
                )
                .clickable(onClick = onTodayClick)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = "今天 $todayD",
                color = if (isTodaySelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RowScope.DayCellView(
    cell: DayCell,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()

    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primary
        pressed || hovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !cell.isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .then(
                    if (isToday && !isSelected)
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            RoundedCornerShape(8.dp)
                        )
                    else Modifier
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                cell.day.toString(),
                color = textColor,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun YearMonthView(
    year: Int,
    month: Int,
    onYearPicked: (Int) -> Unit,
    onMonthPicked: (Int) -> Unit,
    onBack: () -> Unit
) {
    var baseYear by remember { mutableIntStateOf(year - 5) }

    // 返回 Day 视图
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ChevronLeft, "返回", tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            "选择年月",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        // 占位保持对称
        Box(modifier = Modifier.size(48.dp))
    }

    // 年段翻页
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { baseYear -= 12 }) {
            Icon(Icons.Filled.ChevronLeft, "上一段", tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            text = String.format(Locale.CHINA, "%d - %d", baseYear, baseYear + 11),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        IconButton(onClick = { baseYear += 12 }) {
            Icon(Icons.Filled.ChevronRight, "下一段", tint = MaterialTheme.colorScheme.onSurface)
        }
    }

    // 年网格 4 行 × 3 列
    val years = (0..11).map { baseYear + it }
    years.chunked(3).forEach { rowYears ->
        Row(modifier = Modifier.fillMaxWidth()) {
            for (y in rowYears) {
                val isSel = y == year
                val bg = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent
                val color = if (isSel) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .clickable { onYearPicked(y) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${y}年",
                        color = color,
                        fontSize = 16.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }

    // 月网格 4 行 × 3 列
    val months = (1..12).toList()
    months.chunked(3).forEach { rowMonths ->
        Row(modifier = Modifier.fillMaxWidth()) {
            for (m in rowMonths) {
                val isSel = (m - 1) == month
                val bg = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent
                val color = if (isSel) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .clickable { onMonthPicked(m - 1) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${m}月",
                        color = color,
                        fontSize = 16.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** 把指定年月日规整为本地零点的毫秒（时分秒归零），与原 purchaseDate 语义一致。 */
private fun normalizeToMillis(year: Int, month: Int, day: Int): Long {
    val c = Calendar.getInstance(Locale.CHINA).apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
        set(Calendar.DAY_OF_MONTH, day.coerceIn(1, maxDay))
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return c.timeInMillis
}

/** 从毫秒提取日（用于切年月时保留原 day）。 */
private fun dayOf(millis: Long): Int =
    Calendar.getInstance(Locale.CHINA).apply { timeInMillis = millis }.get(Calendar.DAY_OF_MONTH)

/** 构建 6×7=42 格月历，含邻月灰显补齐；周一为首列。 */
private fun buildCells(year: Int, month: Int): List<DayCell> {
    val cal = Calendar.getInstance(Locale.CHINA).apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val firstDow = (cal.get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7 // 周一=0..周日=6
    cal.add(Calendar.DAY_OF_MONTH, -firstDow) // 移到首格（可能是上月）
    return List(42) {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val isCur = (y == year && m == month)
        DayCell(y, m, d, isCur).also { cal.add(Calendar.DAY_OF_MONTH, 1) }
    }
}
