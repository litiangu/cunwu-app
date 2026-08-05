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
import com.litiangu.wuji.data.db.CategoryEntity
import com.litiangu.wuji.data.db.WujiDatabaseProvider
import com.litiangu.wuji.data.settings.DarkModePref
import com.litiangu.wuji.data.settings.SettingsDataStore
import com.litiangu.wuji.ui.analysis.AnalysisScreen
import com.litiangu.wuji.ui.designsystem.BottomTab
import com.litiangu.wuji.ui.home.HomeViewModel
import com.litiangu.wuji.ui.home.HomeScreen
import com.litiangu.wuji.ui.item.ItemEditScreen
import com.litiangu.wuji.ui.onboarding.AppSplash
import com.litiangu.wuji.ui.onboarding.OnboardingScreen
import com.litiangu.wuji.ui.category.CategorySelectScreen
import com.litiangu.wuji.ui.category.CustomCategoryDialog
import com.litiangu.wuji.ui.settings.SettingsScreen
import com.litiangu.wuji.ui.theme.WujiTheme
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * 首页常驻，设置/新增/分析/分类选择 以全屏 overlay 叠放其上（保留首页滚动与筛选状态）。
 * FAB「+」→ 分类选择页（Step 12 新增，替代直接进表单）；
 * 顶栏 ⚙ → SettingsScreen；
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

        // ====== overlay 状态变量（布尔状态机）======
        var showAdd by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var showAnalysis by remember { mutableStateOf(false) }
        var showEdit by remember { mutableStateOf<Long?>(null) }
        var selectedTab by remember { mutableStateOf(homeTab) }

        // Step 12 新增：分类选择页状态
        var showCategorySelect by remember { mutableStateOf(false) }
        // 来源标记：null = 普通入口（首页 FAB），"onboarding" = 引导页 CTA
        var categorySource by remember { mutableStateOf<String?>(null) }
        // 用户在分类选择页选中的 categoryId，传给 ItemEditScreen 做预选
        var preselectedCategoryId by remember { mutableStateOf<Long?>(null) }
        // 自定义分类弹窗是否显示
        var showCustomCategoryDialog by remember { mutableStateOf(false) }

        // 启动就绪闸门：WujiApplication 预热完成（数据库构建完 + 首启标记读出）后 isReady=true。
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
            // ====== 首页（常驻底层）======
            if (showHome) {
                HomeScreen(
                    viewModel = homeVm!!,
                    selectedTab = selectedTab,
                    onTabSelect = { tab ->
                        selectedTab = tab
                        if (tab.label == analysisTab.label) showAnalysis = true
                    },
                    onNew = {
                        // Step 12 改造：FAB 不再直进表单，先走分类选择页
                        categorySource = null
                        preselectedCategoryId = null
                        showCategorySelect = true
                    },
                    onSetting = { showSettings = true },
                    onEdit = { showEdit = it },
                )
            }

            // ====== 引导页（首次启动）======
            if (showOnboarding) {
                OnboardingScreen(
                    onStart = {
                        // Step 12 改造：CTA 进分类选择页（source=onboarding），不再直进表单
                        forceHome = true
                        scope.launch { SettingsDataStore(context).setHasLaunchedBefore(true) }
                        categorySource = "onboarding"
                        preselectedCategoryId = null
                        showCategorySelect = true
                    },
                )
            }

            // ====== 品牌启动页：仅覆盖进程预热期 ======
            if (!isReady) {
                AppSplash()
            }

            // ====== 分类选择页 overlay（Step 12 新增）======
            if (showCategorySelect && homeVm != null) {
                // 从 HomeViewModel 拿全部分类数据
                val categories by homeVm.categories
                    .collectAsStateWithLifecycle(emptyList())

                CategorySelectScreen(
                    categories = categories,
                    source = categorySource,
                    onCategorySelect = { categoryId ->
                        // 选中某个分类 → 关闭选择页 → 打开表单页（携带预选分类）
                        preselectedCategoryId = categoryId
                        showCategorySelect = false
                        showAdd = true
                    },
                    onCustomCategory = {
                        // 点击「添加个人专属分类」→ 打开自定义弹窗
                        showCustomCategoryDialog = true
                    },
                    onDismiss = {
                        // 底部按钮关闭/跳过 → 回到首页或引导页后的首页
                        showCategorySelect = false
                        categorySource = null
                    },
                )

                // 自定义分类弹窗（嵌套在分类选择页内）
                if (showCustomCategoryDialog) {
                    CustomCategoryDialog(
                        existingNames = categories.map { it.name },
                        onConfirm = { trimmedName ->
                            // 校验通过 → IO 线程写库创建分类
                            scope.launch {
                                val db = WujiDatabaseProvider.get(context)
                                val newCat = CategoryEntity(
                                    name = trimmedName,
                                    colorKey = "custom",       // 统一灰色
                                    isPreset = false,           // 标记用户自建
                                    sortOrder = (categories.maxOfOrNull { it.sortOrder } ?: -1) + 1 // 永远排在末尾
                                )
                                val newId = db.categoryDao().insert(newCat)
                                // 创建成功 → 关闭弹窗 + 选择页 → 进表单页携带新分类 id
                                showCustomCategoryDialog = false
                                preselectedCategoryId = newId
                                showCategorySelect = false
                                showAdd = true
                            }
                        },
                        onDismiss = {
                            showCustomCategoryDialog = false
                        },
                    )
                }
            }

            // ====== 设置页 overlay ======
            if (showSettings) {
                SettingsScreen(onBack = { showSettings = false })
            }

            // ====== 物品编辑/新增表单 overlay ======
            if (showAdd) {
                ItemEditScreen(
                    itemId = null,
                    initialCategoryId = preselectedCategoryId,   // Step 13 方案 A：构造参数注入预选分类
                    onDone = {
                        showAdd = false
                        preselectedCategoryId = null             // 清理预选状态
                    },
                )
            }
            if (showEdit != null) {
                ItemEditScreen(
                    itemId = showEdit,
                    onDone = { showEdit = null },
                )
            }

            // ====== 分析页 overlay ======
            if (showAnalysis) {
                AnalysisScreen(onBack = {
                    showAnalysis = false
                    selectedTab = homeTab
                })
            }
        }
    }
}
