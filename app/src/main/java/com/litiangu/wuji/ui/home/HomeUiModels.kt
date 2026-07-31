package com.litiangu.wuji.ui.home

import androidx.compose.ui.graphics.Color
import com.litiangu.wuji.data.db.CategoryDao
import com.litiangu.wuji.data.db.ItemEntity
import com.litiangu.wuji.data.db.toItemInput
import com.litiangu.wuji.domain.computeHealthScore
import com.litiangu.wuji.ui.theme.BrandOrange
import com.litiangu.wuji.ui.theme.LightCategoryPalette
import com.litiangu.wuji.ui.theme.LightGradePalette

data class ItemRow(
    val id: Long,
    val name: String,
    val categoryColor: Color,
    val categoryKey: String,
    val statusLabel: String,
    val healthScore: Int,
    val grade: String,
    val gradeColor: Color,
    val comment: String,
    val privacyMasked: Boolean
)

suspend fun mapToRows(entities: List<ItemEntity>, privacyMode: Boolean, categoryDao: CategoryDao): List<ItemRow> {
    val inputs = entities.map { it.toItemInput() }
    val result = computeHealthScore(inputs)
    val palette = LightCategoryPalette
    val gradeP = LightGradePalette
    val colorMap = mapOf(
        "digital" to palette.digital,
        "appliance" to palette.appliance,
        "furniture" to palette.furniture,
        "tool" to palette.tool,
        "sport" to palette.sport,
        "traffic" to palette.traffic,
        "custom" to palette.custom
    )
    val gradeColorMap = mapOf(
        "优" to gradeP.excellent,
        "良" to gradeP.good,
        "中" to gradeP.fair,
        "差" to gradeP.poor
    )
    return entities.map { entity ->
        val cat = categoryDao.getById(entity.categoryId ?: -1L)
        ItemRow(
            id = entity.id,
            name = entity.name,
            categoryColor = colorMap[cat?.colorKey] ?: BrandOrange,
            categoryKey = cat?.colorKey ?: "",
            statusLabel = entity.status.name,
            healthScore = result.totalRounded,
            grade = result.grade.label,
            gradeColor = gradeColorMap[result.grade.label] ?: gradeP.excellent,
            comment = result.comment,
            privacyMasked = privacyMode
        )
    }
}
