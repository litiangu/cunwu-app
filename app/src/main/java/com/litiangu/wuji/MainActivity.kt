package com.litiangu.wuji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.data.settings.DarkModePref
import com.litiangu.wuji.data.settings.SettingsDataStore
import com.litiangu.wuji.ui.analysis.AnalysisScreen
import com.litiangu.wuji.ui.designsystem.BottomTab
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
 * 应用根：读取深色偏好驱动主题（三档：跟随系统/浅色/深色）。
 *
 * 首页常驻，设置/新增/分析以全屏 overlay 叠放其上（保留首页滚动与筛选状态）。
 * FAB「+」→ ItemEditScreen(null) 新增；顶栏 ⚙ → SettingsScreen；
 * 底栏「分析」tab → AnalysisScreen；各 overlay 返回后回到常驻首页。
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
        val homeTab = remember { BottomTab("首页", Icons.Filled.Home) }
        val analysisTab = remember { BottomTab("分析", Icons.Filled.Insights) }
        var showAdd by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var showAnalysis by remember { mutableStateOf(false) }
        var showEdit by remember { mutableStateOf<Long?>(null) }
        var selectedTab by remember { mutableStateOf(homeTab) }

        Box(modifier = Modifier.fillMaxSize()) {
            HomeScreen(
                selectedTab = selectedTab,
                onTabSelect = { tab ->
                    selectedTab = tab
                    if (tab.label == analysisTab.label) showAnalysis = true
                },
                onNew = { showAdd = true },
                onSetting = { showSettings = true },
                onEdit = { showEdit = it },
            )
            if (showSettings) {
                SettingsScreen(onBack = { showSettings = false })
            }
            if (showAdd) {
                ItemEditScreen(
                    itemId = null,
                    onDone = { showAdd = false },
                )
            }
            if (showEdit != null) {
                ItemEditScreen(
                    itemId = showEdit,
                    onDone = { showEdit = null },
                )
            }
            if (showAnalysis) {
                AnalysisScreen(onBack = {
                    showAnalysis = false
                    selectedTab = homeTab
                })
            }
        }
    }
}