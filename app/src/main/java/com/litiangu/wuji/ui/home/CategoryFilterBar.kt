package com.litiangu.wuji.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.data.db.CategoryEntity
import com.litiangu.wuji.ui.designsystem.CircleIconButton
import com.litiangu.wuji.ui.theme.categoryPalette
import com.litiangu.wuji.ui.theme.BrandOrange

/**
 * 分类筛选栏：横向滚动 chips（8dp 色点 + 分类名）+ 末尾 ≡ 筛选按钮（占位，Step 10 接弹窗）。
 * 色点与文字间距、圆角对齐 `3.0应用主页-显示添加物品后的页面` 与逻辑文档（色点 8px）。
 */
@Composable
fun CategoryFilterBar(
    categories: List<CategoryEntity>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onFilter: () -> Unit = {},
) {
    val palette = categoryPalette()
    val colorMap = mapOf(
        "digital" to palette.digital,
        "appliance" to palette.appliance,
        "furniture" to palette.furniture,
        "tool" to palette.tool,
        "sport" to palette.sport,
        "traffic" to palette.traffic,
        "custom" to palette.custom
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("全部") }
            )
            categories.forEach { cat ->
                FilterChip(
                    selected = selected == cat.colorKey,
                    onClick = { onSelect(cat.colorKey) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(colorMap[cat.colorKey] ?: BrandOrange),
                            contentAlignment = Alignment.Center
                        ) { }
                    },
                    label = { Text(cat.name) }
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        CircleIconButton(
            icon = Icons.Filled.FilterList,
            contentDescription = "筛选与排序",
            onClick = onFilter,
        )
    }
}