package com.litiangu.wuji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.data.settings.DarkModePref
import com.litiangu.wuji.data.settings.SettingsDataStore
import com.litiangu.wuji.ui.home.HomeScreen
import com.litiangu.wuji.ui.item.ItemEditScreen
import com.litiangu.wuji.ui.settings.SettingsScreen
import com.litiangu.wuji.ui.theme.WujiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WujiApp()
        }
    }
}

/**
 * 应用根：读取深色偏好驱动主题（三档：跟随系统/浅色/深色），
 * 并在 首页（含 pill 底栏）↔ 新增/编辑页 ↔ 设置页 之间互斥切换。
 * FAB「+」→ ItemEditScreen(null) 新增；顶栏 ⚙ → SettingsScreen；保存/返回 → 回首页。
 */
@Composable
fun WujiApp() {
    val context = LocalContext.current
    val darkMode by remember { SettingsDataStore(context).darkModeFlow }
        .collectAsStateWithLifecycle(DarkModePref.FOLLOW_SYSTEM)
    val darkTheme = when (darkMode) {
        DarkModePref.YES -> true
        DarkModePref.NO -> false
        else -> isSystemInDarkTheme()
    }
    WujiTheme(darkTheme = darkTheme) {
        var showAdd by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        when {
            showSettings -> SettingsScreen(onBack = { showSettings = false })
            showAdd -> ItemEditScreen(
                itemId = null,
                onDone = { showAdd = false },
            )
            else -> HomeScreen(
                onNew = { showAdd = true },
                onAnalysis = { /* TODO Step 10 分析页导航 */ },
                onSetting = { showSettings = true },
            )
        }
    }
}