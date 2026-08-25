package com.litiangu.wuji.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litiangu.wuji.BuildConfig
import com.litiangu.wuji.R
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSpace

/**
 * 关于页（Step 24，纯展示，无 ViewModel）。
 *
 * 顶栏「关于」+ 应用标识 Hero（存物 / 版本）+ 声明卡：
 * 1. 关于存物：一句话定位。
 * 2. 数据隐私：本地存储、不上传声明。
 * 3. 法律与许可（Step 27）：用户协议 / 开源许可两个可点击入口，进入本地全屏内容页。
 *
 * 导航沿用 MainActivity 布尔状态机（showAbout），以全屏 overlay 叠在设置页之上。
 * 版本号来自 BuildConfig.VERSION_NAME（由 build.gradle.kts 的 versionName 注入）。
 *
 * Step 27 的法律内容页以「AboutScreen 内部子 overlay」实现（本地 legalPage 状态驱动），
 * 不新增 MainActivity 全局导航布尔，保持本步单文件改动、低风险。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit = {},
) {
    // 版本号直接读 BuildConfig（编译期由 build.gradle.kts versionName 注入）。
    val versionName = BuildConfig.VERSION_NAME
    // 法律内容页本地状态：null = 显示关于主页；非 null = 叠加显示对应全屏内容（用户协议 / 开源许可）。
    var legalPage by remember { mutableStateOf<LegalPage?>(null) }

    // 用 Box 包住主 Scaffold 与法律子 overlay，保证 overlay 在 z 轴压住主 Scaffold 顶栏。
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
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

                // 应用标识：白底 logo + 名称 + 版本。
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                    )
                }
                Spacer(Modifier.height(WujiSpace.md))
                // headline-md：Manrope 20sp / 600（DESIGN.md typography.headline-md）
                Text(
                    text = "存物",
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

                // 卡 1：关于存物（一句话定位）。
                AboutCard {
                    AboutStatement(
                        icon = Icons.Filled.Inventory2,
                        title = "关于存物",
                        body = "本地物品管理，以健康分呈现物品的使用与性价比。",
                    )
                }

                Spacer(Modifier.height(WujiSpace.md))

                // 卡 2：数据隐私声明。
                AboutCard {
                    AboutStatement(
                        icon = Icons.Filled.Lock,
                        title = "数据隐私",
                        body = "所有数据仅保存在本机，不会上传。",
                    )
                }

                Spacer(Modifier.height(WujiSpace.md))

                // Step 27：法律与许可入口（两个可点击行）。
                AboutLinkRow(
                    icon = Icons.Filled.Description,
                    title = "用户协议",
                    onClick = { legalPage = LegalPage.USER_AGREEMENT },
                )
                Spacer(Modifier.height(WujiSpace.sm))
                AboutLinkRow(
                    icon = Icons.Filled.Code,
                    title = "开源许可",
                    onClick = { legalPage = LegalPage.OPEN_SOURCE },
                )

                Spacer(Modifier.height(WujiSpace.xl))
                Text(
                    text = "© 2026 存物",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(WujiSpace.md))
            }
        }

        // Step 27：法律内容全屏子 overlay（叠加在关于主页之上，不进入全局导航状态机）。
        legalPage?.let { page ->
            LegalContentOverlay(page = page, onBack = { legalPage = null })
        }
    }
}

/** 法律内容枚举：用户协议 / 开源许可。 */
private enum class LegalPage {
    USER_AGREEMENT,
    OPEN_SOURCE,
}

/**
 * 法律内容全屏子 overlay：与 AboutScreen 同款顶栏（CenterAlignedTopAppBar + 不覆盖 windowInsets），
 * 内容区竖向滚动。返回时回到关于主页（由调用方 legalPage 置 null）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegalContentOverlay(
    page: LegalPage,
    onBack: () -> Unit,
) {
    val title = when (page) {
        LegalPage.USER_AGREEMENT -> "用户协议"
        LegalPage.OPEN_SOURCE -> "开源许可"
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
        ) {
            when (page) {
                LegalPage.USER_AGREEMENT -> UserAgreementContent()
                LegalPage.OPEN_SOURCE -> OpenSourceContent()
            }
        }
    }
}

/** 用户协议正文（本机优先、数据自管的本地应用条款）。 */
@Composable
private fun UserAgreementContent() {
    val paragraphStyle = MaterialTheme.typography.bodyMedium
    val headingStyle = MaterialTheme.typography.titleMedium

    Text("欢迎使用存物", style = headingStyle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(WujiSpace.md))
    LegalParagraph("存物是一款完全在您本机运行的本地物品管理工具，用于记录家中物品、以健康分呈现物品的使用与性价比。")
    LegalParagraph("一、数据本地化。您录入的所有物品信息、图片、预算与偏好均仅存储于本机数据库与 DataStore，不会上传任何服务器，我们也无法访问。")
    LegalParagraph("二、备份与导出。数据备份与导出文件由您自行管理，请妥善保管，避免设备丢失或文件泄露导致的信息外泄。")
    LegalParagraph("三、无第三方账号。本应用不要求注册账号，不与任何第三方共享您的数据。")
    LegalParagraph("四、责任限制。本应用按「原样」提供，用于物品记录与健康分参考，不构成任何专业财务或法律建议。因使用本应用产生的任何损失，我们不承担责任。")
    LegalParagraph("五、协议变更。本协议可能随版本更新而调整，请以应用内最新版本为准。继续使用即表示您同意最新条款。")
    Spacer(Modifier.height(WujiSpace.lg))
}

/** 开源许可：第三方库与许可证清单（与 app/build.gradle.kts 实际依赖一致，统一 Apache License 2.0）。 */
@Composable
private fun OpenSourceContent() {
    val libs = listOf(
        "Kotlin" to "JetBrains · Apache License 2.0",
        "AndroidX Core KTX" to "Google · Apache License 2.0",
        "Jetpack Compose（Material 3 / UI / Activity）" to "Google · Apache License 2.0",
        "AndroidX Lifecycle" to "Google · Apache License 2.0",
        "Room" to "Google · Apache License 2.0",
        "DataStore" to "Google · Apache License 2.0",
        "Material Icons" to "Google · Apache License 2.0",
        "Navigation Compose" to "Google · Apache License 2.0",
        "LiteRT（ormbg 智能抠图推理）" to "Google · Apache License 2.0",
        "kotlinx.serialization" to "JetBrains · Apache License 2.0",
    )

    Text("第三方库声明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(WujiSpace.md))
    libs.forEach { (name, license) ->
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = WujiSpace.sm)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(WujiSpace.xs))
            Text(license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(WujiSpace.md))
    Text(
        "本应用所使用的开源组件均基于 Apache License 2.0 发布。完整许可文本见 https://www.apache.org/licenses/LICENSE-2.0",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 20.sp,
    )
    Spacer(Modifier.height(WujiSpace.lg))
}

/** 法律正文段落（body 样式 + 段间距）。 */
@Composable
private fun LegalParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 22.sp,
    )
    Spacer(Modifier.height(WujiSpace.md))
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

/** 可点击链接行（法律与许可入口）：图标圈 + 标题 + 右箭头，整体白底卡样式。 */
@Composable
private fun AboutLinkRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(WujiRadius.cardLg),
                clip = false,
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(WujiRadius.cardLg),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
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
