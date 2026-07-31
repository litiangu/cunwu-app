package com.litiangu.wuji.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.data.settings.DarkModePref
import com.litiangu.wuji.data.settings.SettingsDataStore

/**
 * 设置页（主 tab）：深色三档 / 隐私模式 / 预算锚点 / 关于。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val viewModel = remember { SettingsViewModel(SettingsDataStore(context)) }
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val privacyMode by viewModel.privacyMode.collectAsStateWithLifecycle()
    val budgetAnchor by viewModel.budgetAnchor.collectAsStateWithLifecycle()
    var budgetText by remember(budgetAnchor) { mutableStateOf(formatAmount(budgetAnchor)) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 深色三档
            Text("深色模式", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkModePref.entries.forEach { pref ->
                    FilterChip(
                        selected = darkMode == pref,
                        onClick = { viewModel.setDarkMode(pref) },
                        label = { Text(darkModeLabel(pref)) }
                    )
                }
            }
            Text(
                "切换后全局生效并持久化，重启保持",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 隐私模式
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("隐私模式", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "首页隐藏物品名称",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = privacyMode, onCheckedChange = viewModel::setPrivacyMode)
            }

            // 预算锚点
            Text("每日预算锚点（元）", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        budgetText.toDoubleOrNull()?.let(viewModel::saveBudgetAnchor)
                    }
                ) { Text("保存") }
            }
            Text(
                "健康分价值维度比对的每日基准，默认 ¥100",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 关于
            Text("关于", style = MaterialTheme.typography.titleMedium)
            Text("物记 Wuji · beta 1.0", style = MaterialTheme.typography.bodyMedium)
            Text(
                "反馈：XXX（占位）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun darkModeLabel(pref: DarkModePref): String = when (pref) {
    DarkModePref.FOLLOW_SYSTEM -> "跟随系统"
    DarkModePref.NO -> "浅色"
    DarkModePref.YES -> "深色"
}

private fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
