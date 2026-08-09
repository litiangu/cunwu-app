package com.litiangu.wuji.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSize

/**
 * 底部 pill 导航（参考 `3.0应用主页`）：白色胶囊底栏 + 中央橙色凸起 FAB。
 *
 * Box 高度 = pill 高度 + FAB 凸出量（28dp），保证 FAB 上半完整露出、
 * Scaffold 的 innerPadding.bottom 自动包含凸出部分，列表内容不被 FAB 遮挡。
 */
@Composable
fun BottomPillBar(
    items: List<BottomTab>,
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fabProtrude = 20.dp
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(WujiSize.bottomBarHeight + fabProtrude),
    ) {
        // pill 主体：底部对齐，圆角 24dp，白底 + 阴影
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(WujiSize.bottomBarHeight)
                .clip(RoundedCornerShape(WujiRadius.xxl))
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(WujiRadius.xxl),
                    spotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                    ambientColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                ),
        ) {
            items.forEach { tab ->
                NavigationBarItem(
                    selected = tab == selected,
                    onClick = {
                        onSelect(tab)
                        context.fireHapticIfEnabled()
                    },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label) },
                )
            }
        }
        // 中央 FAB：凸起于 pill 顶部（FAB 半高 28dp，顶到 Box 顶部）
        FloatingActionButton(
            onClick = {
                onFabClick()
                context.fireHapticIfEnabled()
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                ),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

data class BottomTab(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)