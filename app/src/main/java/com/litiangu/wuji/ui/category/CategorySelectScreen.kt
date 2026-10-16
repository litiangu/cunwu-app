package com.litiangu.wuji.ui.category

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.data.db.CategoryEntity
import com.litiangu.wuji.ui.designsystem.categoryIcon
import com.litiangu.wuji.ui.designsystem.categoryVisual
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSpace

/**
 * 分类选择页（新增物品 · 步骤一）[Screen 2.0]
 *
 * 全屏展示预置分类卡片 + 已有自定义分类（统一按 sortOrder 排序渲染）+ 「添加个人专属分类」入口。
 * 退出入口统一为右上角 × 圆圈按钮（两种来源一致）。
 *
 * @param categories 全部分类（预置 + 用户自建），按 sortOrder 排序
 * @param source 来源标记（保留字段，当前两种来源均走右上角 ×；null=普通入口，"onboarding"=引导页）
 * @param onCategorySelect 用户选中某个分类，回调分类 id
 * @param onCustomCategory 用户点击「添加个人专属分类」入口
 * @param onDismiss 用户点击右上角 ×
 */
@Composable
fun CategorySelectScreen(
    categories: List<CategoryEntity>,
    source: String? = null,
    onCategorySelect: (Long) -> Unit,
    onCustomCategory: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 修复08：系统返回手势 / 侧滑返回 = 关闭本页（等价于右上角 ×）。
    // 本页在 WujiApp 的 Box 中后于分析页组合，BackHandler 优先级更高，
    // 侧滑不会再穿透到下层分析页（旧行为：关掉分析页后露出本页，像是「侧滑进了添加页」）。
    // 关闭后不改动下层的 showAnalysis，来源分析页时自然回到分析页。
    BackHandler(onBack = onDismiss)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // [Step 54] 显式避让状态栏/挖孔。本页没有 Scaffold，innerPadding 拿不到 inset，
            // 原先只靠 Spacer(WujiSpace.xl) 目测挡着。当前机型（152px≈46.8dp 挖孔）恰好没撞上，
            // 换挖孔更高的机型标题会被啃掉，属防御性改动，视觉上与原状一致。
            .statusBarsPadding()
            .padding(horizontal = WujiSpace.lg),
    ) {
        // ====== 顶栏：标题 + 右上角 × 圆圈按钮（两种来源统一退出入口） ======
        Spacer(modifier = Modifier.height(WujiSpace.xl))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "添加新物品",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            // 右上角 × 圆圈按钮（对齐参考截图 Screen 2.0：浅灰圆底 + 居中 X）
            IconButton(onClick = onDismiss) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(WujiSpace.sm))
        Text(
            text = "请选择一个分类，开始记录你的新物品。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(WujiSpace.lg))

        // ====== 可滚动卡片列表（整体：预置分类 + 自定义分类 + 添加入口） ======
        // categories 来自 Room Flow，首帧为空。用 AnimatedVisibility 在数据到位时整体淡入，
        // 空态不渲染任何可见内容，避免「加载中」闪烁 / 「添加入口先单独暴露」的两步渲染。
        val sortedCategories = categories.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        AnimatedVisibility(
            visible = sortedCategories.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(durationMillis = 250)),
            exit = fadeOut(animationSpec = tween(durationMillis = 150)),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(WujiSpace.md),
                contentPadding = PaddingValues(bottom = WujiSpace.xl),
            ) {
                // 数据已到位：分类 + 添加入口 一次性渲染，顺序绝对确定
                items(
                    items = sortedCategories,
                    key = { "cat_${it.id}" },
                ) { cat ->
                    if (cat.isPreset) {
                        PresetCategoryCard(
                            category = cat,
                            onClick = { onCategorySelect(cat.id) },
                        )
                    } else {
                        CustomCategoryCard(
                            category = cat,
                            onClick = { onCategorySelect(cat.id) },
                        )
                    }
                }
                // 「添加个人专属分类」入口（列表末尾固定项）
                item(key = "add_custom") {
                    AddCustomEntryCard(onClick = onCustomCategory)
                }
            }
        }
    }
}

// ==================== 分类卡片组件 ====================

/** 预置分类卡片：图标圈 + 名称 + 描述文案 */
@Composable
private fun PresetCategoryCard(
    category: CategoryEntity,
    onClick: () -> Unit,
) {
    val visual = categoryVisual(category.colorKey)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(WujiRadius.cardLg),           // 24dp 圆角
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(WujiSpace.md),
        ) {
            // 左侧图标圈（浅 tint 底色 + 实色图标）
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .padding(2.dp),          // 视觉内缩让底色更精致
                contentAlignment = Alignment.Center,
            ) {
                // 浅色背景圆
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),      // 图标与底色间距
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = null,
                        tint = visual.foreground,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(WujiSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = categoryDescription(category.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 用户自建分类卡片（同预置样式，无固定描述） */
@Composable
private fun CustomCategoryCard(
    category: CategoryEntity,
    onClick: () -> Unit,
) {
    val visual = categoryVisual(category.colorKey)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(WujiRadius.cardLg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(WujiSpace.md),
        ) {
            Box(
                modifier = Modifier.size(48.dp).padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = null,
                        tint = visual.foreground,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(WujiSpace.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "自定义分类",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 「添加个人专属分类」入口卡片 */
@Composable
private fun AddCustomEntryCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(WujiRadius.cardLg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(WujiSpace.md),
        ) {
            // 加号图标圈
            Box(
                modifier = Modifier.size(48.dp).padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(WujiSpace.md))
            Text(
                text = "添加个人专属分类",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ==================== 预置分类描述文案 ====================

/**
 * 返回预置分类的描述文字。
 * 仅对 6 个预置分类有固定描述；自定义分类走单独逻辑。
 */
fun categoryDescription(name: String): String = when (name) {
    "数码" -> "记录电子产品与配件"
    "家具" -> "记录家居与软装"
    "电器" -> "记录家电与生活电器"
    "交通" -> "记录代步工具"
    "工具" -> "记录维修与手动工具"
    "运动" -> "记录健身与户外装备"
    else -> "自定义分类"
}
