package com.litiangu.wuji.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litiangu.wuji.BuildConfig
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSpace

/**
 * 关于页（Step 24，纯展示，无 ViewModel）。
 *
 * 顶栏「关于」+ 应用标识 Hero（物记 / 版本）+ 两张白底声明卡：
 * 1. 关于物记：一句话定位。
 * 2. 数据隐私：本地存储、不上传声明。
 *
 * 导航沿用 MainActivity 布尔状态机（showAbout），以全屏 overlay 叠在设置页之上。
 * 版本号来自 BuildConfig.VERSION_NAME（由 build.gradle.kts 的 versionName 注入）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit = {},
) {
    // 版本号直接读 BuildConfig（编译期由 build.gradle.kts versionName 注入）。
    val versionName = BuildConfig.VERSION_NAME

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 顶栏默认按 status bar inset 留白，不覆盖 windowInsets（Step 17 第三轮踩坑：覆盖会贴顶）。
            CenterAlignedTopAppBar(
                title = {
                    // headline-lg：Manrope 24sp / 600（DESIGN.md typography.headline-lg）
                    Text(
                        text = "关于",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    // 显式给 contentColor = onSurface：自定义顶栏无 Scaffold 提供 ContentColor，
                    // 深色下默认回退 Black 会隐入卡片底（Step 18 同款坑）。
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 装饰圆钮：40dp 灰圆 + Info 图标，与「关于本应用」设置行图标一致。
                    Box(
                        modifier = Modifier
                            .padding(end = WujiSpace.sm)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(WujiSpace.lg))

            // 应用标识：主色圆 + 「物」字 + 名称 + 版本。
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "物",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(WujiSpace.md))
            // headline-md：Manrope 20sp / 600（DESIGN.md typography.headline-md）
            Text(
                text = "物记",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(WujiSpace.xs))
            Text(
                text = "v$versionName",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(WujiSpace.xl))

            // 卡 1：关于物记（一句话定位）。
            AboutCard {
                AboutStatement(
                    icon = Icons.Filled.Inventory2,
                    title = "关于物记",
                    body = "本地物品管理，辅助断舍离决策。",
                )
            }

            Spacer(Modifier.height(WujiSpace.md))

            // 卡 2：数据隐私声明。
            AboutCard {
                AboutStatement(
                    icon = Icons.Filled.Lock,
                    title = "数据隐私",
                    body = "所有数据仅保存在本机，不会上传任何服务器。",
                )
            }

            Spacer(Modifier.height(WujiSpace.xl))
            Text(
                text = "© 2026 物记 Wuji",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(WujiSpace.md))
        }
    }
}

/** 白底卡：圆角 24dp + 极淡阴影 + 缩进分隔边框，对齐设置页族。 */
@Composable
private fun AboutCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(WujiRadius.cardLg),
                clip = false,
            ),
        shape = RoundedCornerShape(WujiRadius.cardLg),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), content = content)
    }
}

/** 声明行：图标圈 + 标题 + 描述正文（可换行）。 */
@Composable
private fun AboutStatement(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(WujiSpace.md))
        Column(modifier = Modifier.weight(1f)) {
            // body-lg：Manrope 16sp / 500（DESIGN.md typography.body-lg）
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(WujiSpace.xs))
            // body-md：Manrope 14sp / 400（DESIGN.md typography.body-md）
            Text(
                text = body,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
        }
    }
}
