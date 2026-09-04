package com.litiangu.wuji.ui.home

import androidx.compose.ui.unit.dp

/**
 * Step 40 新增：首页三视图（默认列表 / 卡片瀑布流 / 紧凑陈列馆）专用布局令牌。
 *
 * 为什么不直接复用 `WujiRadius` / `WujiSpace`：
 * 规格文档里写的 `WujiRadius.lg=16 / xl=20`、`WujiSpace.xs=8 / sm=12` 与代码实际值
 * （`ui/theme/Tokens.kt`：lg=8、xl=16、xs=4、sm=8）对不上，而 lg / xl 已被全 App
 * 数十处引用，按规格改值会引发大范围视觉回归。因此三视图一律**新增专用常量，不动既有令牌**。
 *
 * 数值口径全部来自 `E:\app demo\首页三视图布局规格_v1.md`（v2 内容）。
 */

/** 三视图卡片圆角 */
object ItemCardRadius {
    val listRow = 20.dp    // 模式 A 列表行卡片
    val listChip = 8.dp    // 模式 A「已陪伴 N 天」chip
    val waterfall = 20.dp  // 模式 B 瀑布流卡片（Step 42）
    val gallery = 16.dp    // 模式 C 陈列馆单元格（Step 43）
}

/** 模式 A 时间轴左栏（Step 42，参考日历时间轴复刻） */
object ItemListRail {
    val dateWidth = 34.dp    // 日期锚区（内容右对齐）
    val channelWidth = 18.dp // 节点/竖线通道；线中心 = dateWidth + channelWidth / 2
    val lineWidth = 2.dp     // 竖线宽（outlineVariant，组块级绘制）
    val nodeOuter = 12.dp    // 节点外径（含背景色环）
    val nodeRing = 2.dp      // 节点环宽（背景色，把节点与竖线隔开）
    val nodeInner = 8.dp     // 节点内点（分类色 visual.foreground）
}

/** 模式 A 卡内缩略图（Step 42：图入卡，56 方图贴卡高，图 2 样式） */
object ItemListThumb {
    val size = 56.dp
    val radius = 16.dp
}

/** 模式 A 行与组的间距节律（组相关两项 Step 41 启用） */
object ItemListSpacing {
    val row = 8.dp         // 组内行间距
    val groupTop = 12.dp   // 组内首行上间距
    val groupGap = 16.dp   // 组与组之间
}

/** 模式 A 行容器 */
object ItemListRow {
    val height = 80.dp     // 含内边距
    val paddingV = 12.dp
    val paddingH = 12.dp   // 规格写 h=0，实测文字会顶到 20dp 圆角，取与左列间距同值
}

/** 模式 B 瀑布流（Step 42 启用，40 先落常量便于口径校核） */
object ItemWaterfallLayout {
    val columnWidth = 150.dp       // (360 - 24*2 - 12) / 2
    val imageMinHeight = 112.dp    // 极长图封底
    val imageMaxHeight = 149.dp    // 极宽图封顶
    val contentHeight = 70.dp      // 文案区固定高
    val gap = 12.dp                // 行/列间隙
}

/** 模式 C 陈列馆（Step 43 启用） */
object ItemGalleryLayout {
    val columnWidth = 96.dp        // (360 - 24*2 - 12*2) / 3
    val imageSize = 96.dp          // 方图，与列宽一致
    val contentHeight = 60.dp      // 文案区固定高
    val gap = 12.dp                // 行/列间隙
}
