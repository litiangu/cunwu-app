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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.litiangu.wuji.WujiApplication
import com.litiangu.wuji.data.db.WujiDatabaseProvider
import com.litiangu.wuji.ui.home.HomeViewModel
import com.litiangu.wuji.ui.onboarding.AppSplash
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.data.settings.DarkModePref
import com.litiangu.wuji.data.settings.SettingsDataStore
import com.litiangu.wuji.ui.analysis.AnalysisScreen
import com.litiangu.wuji.ui.designsystem.BottomTab
import com.litiangu.wuji.ui.home.HomeScreen
import com.litiangu.wuji.ui.item.ItemEditScreen
import com.litiangu.wuji.ui.onboarding.OnboardingScreen
import com.litiangu.wuji.ui.settings.SettingsScreen
import com.litiangu.wuji.ui.theme.WujiTheme
import kotlinx.coroutines.launch

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

        // 启动就绪闸门：WujiApplication 预热完成（数据库构建完 + 首启标记读出）后 isReady=true。
        // 此时 INSTANCE 已建，所有后续 get() 直接返回不阻塞；首启标记同步读出，无首帧 null 白屏。
        val app = context.applicationContext as WujiApplication
        val isReady by app.isReady.collectAsStateWithLifecycle(false)
        val scope = rememberCoroutineScope()

        // 首页 VM 延迟创建：isReady 后 INSTANCE 已建，get() 即时返回，不在主线程阻塞建库。
        val homeVm = remember(isReady) {
            if (isReady) {
                val db = WujiDatabaseProvider.get(context)
                HomeViewModel(db.itemDao(), db.categoryDao(), db.trashDao(), context)
            } else null
        }

        // 点 CTA 后本会话强制进首页，不等 DataStore 异步落盘，消除表单延迟。
        var forceHome by remember { mutableStateOf(false) }

        val showOnboarding = isReady && !app.launchedBefore && !forceHome
        val showHome = isReady && homeVm != null && !showOnboarding

        Box(modifier = Modifier.fillMaxSize()) {
            if (showHome) {
                HomeScreen(
                    viewModel = homeVm!!,
                    selectedTab = selectedTab,
                    onTabSelect = { tab ->
                        selectedTab = tab
                        if (tab.label == analysisTab.label) showAnalysis = true
                    },
                    onNew = { showAdd = true },
                    onSetting = { showSettings = true },
                    onEdit = { showEdit = it },
                )
            }
            if (showOnboarding) {
                OnboardingScreen(
                    onStart = {
                        // 立即本地翻转 + 进新增表单；标记后台异步落盘，重启后直接进首页。
                        // 注：正式 CTA 应跳「分类选择页」并携 source=onboarding（见 Step 12），
                        // 此处复用现有新增入口(showAdd)，待 Step 12 接管后调整。
                        forceHome = true
                        showAdd = true
                        scope.launch { SettingsDataStore(context).setHasLaunchedBefore(true) }
                    },
                )
            }
            // 品牌启动页：只覆盖进程预热期（建库 + 读首启标记）。
            // 首查期由 HomeScreen 内部闸门（复用同款 AppSplash 视觉）承接，
            // 保证首启/老用户启动页时长一致（都 = 预热），且点 CTA 时 isReady 已 true、启动页不在，表单不被遮盖。
            if (!isReady) {
                AppSplash()
            }
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