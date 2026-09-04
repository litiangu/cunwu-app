package com.litiangu.wuji.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 模式 A 分组头（Step 42 换「图 2」样式）：
 * 大月份「8月」20sp/700 + 小年份「2026」+ 右侧「N 件」。
 *
 * 相比 Step 41：删除 1dp divider，时间语义由组块竖线承担（参考图 1），divider 与线重复。
 * 节律不变：top 8（与行尾 8dp 合计 = 组间 16）、bottom 12（组头 → 组块首行 = 组上 12）。
 * 纯展示不可点，**非 sticky**。
 */
@Composable
fun ItemSectionHeader(
    monthName: String,
    year: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = ItemListSpacing.row,
                bottom = ItemListSpacing.groupTop,
            ),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = monthName,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = year,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "$count 件",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}
