package com.litiangu.wuji

import android.app.UiModeManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.litiangu.wuji.data.settings.DarkModePref
import com.litiangu.wuji.data.settings.SettingsDataStore
import com.litiangu.wuji.ui.home.HomeScreen
import com.litiangu.wuji.ui.theme.WujiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 清除早期版本 UiModeManager 留下的应用级深色覆盖（已持久化到系统），
        // 否则"跟随系统"档读到的 isSystemInDarkTheme() 是被覆盖后的配置而非系统真实值。
        (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager)
            .setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)
        setContent {
            // 深色由 DataStore 驱动（不重建 Activity）：设置页切档 → flow 更新 → 全局重组变色
            val settings = remember { SettingsDataStore(this@MainActivity) }
            val darkPref by settings.darkModeFlow.collectAsState(initial = DarkModePref.FOLLOW_SYSTEM)
            val darkTheme = when (darkPref) {
                DarkModePref.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                DarkModePref.NO -> false
                DarkModePref.YES -> true
            }
            WujiTheme(darkTheme = darkTheme) {
                HomeScreen()
            }
        }
    }
}
