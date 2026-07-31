package com.litiangu.wuji.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.data.db.CategoryEntity
import com.litiangu.wuji.ui.theme.categoryPalette
import com.litiangu.wuji.ui.theme.BrandOrange

@Composable
fun CategoryFilterBar(
    categories: List<CategoryEntity>,
    selected: String?,
    onSelect: (String?) -> Unit
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
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            .size(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(colorMap[cat.colorKey] ?: BrandOrange),
                        contentAlignment = Alignment.Center
                    ) { }
                },
                label = { Text(cat.name) }
            )
        }
    }
}
