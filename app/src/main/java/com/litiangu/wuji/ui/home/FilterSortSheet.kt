package com.litiangu.wuji.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.data.db.ItemStatus
import com.litiangu.wuji.ui.theme.BrandOrange
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSpace

/**
 * 筛选与排序弹窗（ModalBottomSheet，底部上滑）。
 * 退出：下滑手势 或 点击遮罩（由 ModalBottomSheet.onDismissRequest 处理），
 * 无右上角 × 按钮，避免冗余。
 *
 * 规则（逻辑文档《筛选与排序弹窗功能》）：
 * - 打开时草稿态 = 当前生效态副本；操作只改草稿，不点确定不生效。
 * - 排序：日期/金额 互斥为主键，每行右侧 ↑/↓ 图标按钮显式选择升降序。
 * - 状态：使用中 / 未使用 / 已售出 多选胶囊按钮，都不选 = 不过滤。
 * - 清除：草稿重置为默认（日期降序、无状态），弹窗不关。
 * - 确定：草稿提交生效态并关弹窗。遮罩/下滑：丢弃草稿、保持原生效态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSortSheet(
    currentSortKey: String,
    currentSortOrder: String,
    currentStatusFilter: Set<ItemStatus>,
    onApply: (key: String, order: String, status: Set<ItemStatus>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    // 草稿态：每次弹窗打开（重组）从生效态复制一份
    val draftKey = remember { mutableStateOf(currentSortKey) }
    val draftOrder = remember { mutableStateOf(currentSortOrder) }
    val draftStatus = remember { mutableStateOf(currentStatusFilter.toMutableSet()) }

    fun resetDraft() {
        draftKey.value = "date"
        draftOrder.value = "desc"
        draftStatus.value = mutableSetOf()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WujiSpace.lg, vertical = WujiSpace.md)
        ) {
            // 标题（无 × 按钮；退出靠下滑/遮罩）
            Text(
                text = "筛选与排序",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(WujiSpace.md))

            // 排序区：日期 / 金额 各带 ↑ ↓ 图标按钮
            Text(
                text = "排序",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(WujiSpace.sm))
            SortKeyRow(Icons.Filled.DateRange, "日期", "date", draftKey, draftOrder)
            SortKeyRow(Icons.Filled.AccountBalanceWallet, "金额", "price", draftKey, draftOrder)
            Spacer(Modifier.height(WujiSpace.md))

            // 使用状态区：使用中 / 未使用 / 已售出 1×3 胶囊按钮
            Text(
                text = "使用状态",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(WujiSpace.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(WujiSpace.sm)) {
                StatusPill("使用中", ItemStatus.IN_USE, draftStatus, Modifier.weight(1f))
                StatusPill("未使用", ItemStatus.UNUSED, draftStatus, Modifier.weight(1f))
                StatusPill("已售出", ItemStatus.SOLD, draftStatus, Modifier.weight(1f))
            }
            Spacer(Modifier.height(WujiSpace.lg))

            // 底部操作：清除 / 确定
            Row(horizontalArrangement = Arrangement.spacedBy(WujiSpace.md)) {
                OutlinedButton(
                    onClick = { resetDraft() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(WujiRadius.lg),
                ) { Text("清除") }
                Button(
                    onClick = {
                        onApply(draftKey.value, draftOrder.value, draftStatus.value.toSet())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(WujiRadius.lg),
                ) { Text("确定") }
            }
            Spacer(Modifier.height(WujiSpace.md))
        }
    }
}

/**
 * 排序键行：左侧分类图标 + 文字，右侧 ↑/↓ 图标按钮。
 * - 选中态：当前 draftKey == key 且 draftOrder 与图标对应序一致 → 图标橙底白箭头；
 *           文字 + 前置图标也变橙强调。
 * - 未选中：图标浅底灰箭头，文字中性色。
 * 点击 ↑/↓ 直接设置 (key, order)，无副作用切换。
 */
@Composable
private fun SortKeyRow(
    icon: ImageVector,
    label: String,
    key: String,
    draftKey: MutableState<String>,
    draftOrder: MutableState<String>,
) {
    val keySelected = draftKey.value == key
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (keySelected) BrandOrange else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(WujiSpace.md))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (keySelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (keySelected) BrandOrange else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        OrderIconButton(
            isAsc = true,
            selected = keySelected && draftOrder.value == "asc",
            onClick = {
                draftKey.value = key
                draftOrder.value = "asc"
            },
        )
        Spacer(Modifier.width(WujiSpace.sm))
        OrderIconButton(
            isAsc = false,
            selected = keySelected && draftOrder.value == "desc",
            onClick = {
                draftKey.value = key
                draftOrder.value = "desc"
            },
        )
    }
}

@Composable
private fun OrderIconButton(
    isAsc: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) BrandOrange else MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isAsc) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            contentDescription = if (isAsc) "升序" else "降序",
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 状态胶囊按钮：1×3 排列使用 RoundedCornerShape(50) 呈现胶囊外形。 */
@Composable
private fun StatusPill(
    label: String,
    status: ItemStatus,
    draftStatus: MutableState<MutableSet<ItemStatus>>,
    modifier: Modifier = Modifier,
) {
    val selected = draftStatus.value.contains(status)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) BrandOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .clickable {
                val next = draftStatus.value.toMutableSet()
                if (selected) next.remove(status) else next.add(status)
                draftStatus.value = next
            }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (selected) BrandOrange else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}