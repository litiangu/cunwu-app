package com.litiangu.wuji.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.data.settings.DarkModePref
import com.litiangu.wuji.data.settings.SettingsDataStore
import kotlinx.coroutines.launch

/**
 * 设置主页（对齐 `stitch_app_settings_page_redesign/5.0设置页面/screen.png`）。
 *
 * 顶栏 ← 返回 / 中间「设置」；
 * 滚动内容：用户信息卡 + 「偏好/管理/数据与账户/关于」四组白底卡，
 * 每行 = 40dp 圆形图标圈（浅橙 tint #FFF5F0 底 + 灰色图标）+ 标题 + 右侧控件（Switch / chevron）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val settings = remember { SettingsDataStore(context) }
    val viewModel = remember { SettingsViewModel(settings) }
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val privacyMode by viewModel.privacyMode.collectAsStateWithLifecycle()
    val budgetAnchor by viewModel.budgetAnchor.collectAsStateWithLifecycle()
    var budgetText by remember(budgetAnchor) { mutableStateOf(formatAmount(budgetAnchor)) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 用户信息卡
            UserInfoCard()
            Spacer(Modifier.height(8.dp))

            // 偏好
            SectionLabel("偏好")
            CardGroup {
                // 深色模式：三档（跟随系统/浅色/深色）
                SettingRow(
                    icon = Icons.Filled.SettingsBrightness,
                    title = "深色模式",
                    trailing = {
                        ThreeChoiceDarkMode(
                            current = darkMode,
                            onSelect = { scope.launch { settings.setDarkMode(it) } },
                        )
                    },
                )
                Divider1px()
                // 隐私模式
                SettingRow(
                    icon = if (privacyMode) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    title = "隐私模式",
                    trailing = {
                        Switch(checked = privacyMode, onCheckedChange = { viewModel.setPrivacyMode(it) })
                    },
                )
                Divider1px()
                // 震动反馈（占位，Step 10 实现）
                SettingRow(
                    icon = Icons.Filled.NotificationsActive,
                    title = "震动反馈",
                    trailing = { Switch(checked = false, onCheckedChange = {}) },
                )
            }
            Spacer(Modifier.height(8.dp))

            // 管理
            SectionLabel("管理")
            CardGroup {
                SettingRow(
                    icon = Icons.Filled.History,
                    title = "最近删除",
                    trailing = { Chevron() },
                    onClick = { /* TODO Step 13 */ },
                )
                Divider1px()
                // 预算锚点（Step 1-8 内嵌编辑）
                SettingRow(
                    icon = Icons.Filled.MonetizationOn,
                    title = "预算锚点",
                    trailing = {
                        OutlinedTextField(
                            value = budgetText,
                            onValueChange = { budgetText = it.filter { c -> c.isDigit() || c == '.' } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(120.dp),
                            singleLine = true,
                            trailingIcon = {
                                TextButton(onClick = {
                                    val v = budgetText.toDoubleOrNull()
                                    if (v != null) scope.launch { viewModel.saveBudgetAnchor(v) }
                                }) { Text("保存") }
                            },
                        )
                    },
                )
            }
            Spacer(Modifier.height(8.dp))

            // 数据与账户
            SectionLabel("数据与账户")
            CardGroup {
                SettingRow(
                    icon = Icons.Filled.CloudUpload,
                    title = "数据备份",
                    trailing = { Chevron() },
                    onClick = { /* TODO Step 14 */ },
                )
            }
            Spacer(Modifier.height(8.dp))

            // 关于
            SectionLabel("关于")
            CardGroup {
                SettingRow(
                    icon = Icons.Filled.Info,
                    title = "关于我们",
                    trailing = { Chevron() },
                    onClick = { /* TODO Step 15 */ },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UserInfoCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { /* TODO 用户信息编辑 */ }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            // 铅笔编辑角标（橙底）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "本地用户",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Text(
                text = "未命名",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun CardGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface),
        content = content,
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 16.dp, vertical = 12.dp)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFF5F0)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Divider1px() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 72.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    )
}

@Composable
private fun ThreeChoiceDarkMode(current: DarkModePref, onSelect: (DarkModePref) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChoiceChip3(label = "跟随", selected = current == DarkModePref.FOLLOW_SYSTEM, onClick = { onSelect(DarkModePref.FOLLOW_SYSTEM) })
        ChoiceChip3(label = "浅色", selected = current == DarkModePref.NO, onClick = { onSelect(DarkModePref.NO) })
        ChoiceChip3(label = "深色", selected = current == DarkModePref.YES, onClick = { onSelect(DarkModePref.YES) })
    }
}

@Composable
private fun ChoiceChip3(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatAmount(v: Double): String = if (v % 1.0 == 0.0) "%.0f".format(v) else "%.2f".format(v)