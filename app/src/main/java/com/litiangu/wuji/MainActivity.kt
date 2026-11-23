package com.litiangu.wuji

import android.graphics.Color
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.WujiApplication
import com.litiangu.wuji.data.db.CategoryEntity
import com.litiangu.wuji.data.db.WujiDatabaseProvider
import com.litiangu.wuji.data.settings.DarkModePref
import com.litiangu.wuji.data.settings.SettingsDataStore
import com.litiangu.wuji.data.settings.StartupTheme
import com.litiangu.wuji.ui.LegalPage
import com.litiangu.wuji.ui.Overlay
import com.litiangu.wuji.ui.containsOverlay
import com.litiangu.wuji.ui.designsystem.WujiIcons
import com.litiangu.wuji.ui.isDialog
import com.litiangu.wuji.ui.analysis.AnalysisScreen
import com.litiangu.wuji.ui.designsystem.BottomPillBar
import com.litiangu.wuji.ui.designsystem.FabRevealContainer
import com.litiangu.wuji.ui.designsystem.FabRevealState
import com.litiangu.wuji.ui.designsystem.KeepAliveTabHost
import com.litiangu.wuji.ui.designsystem.consumeAllTouches
import com.litiangu.wuji.ui.designsystem.fabRevealBackdropBlur
import com.litiangu.wuji.ui.designsystem.SheetBackdropHost
import com.litiangu.wuji.ui.designsystem.SheetBackdropState
import com.litiangu.wuji.ui.designsystem.BottomTab
import com.litiangu.wuji.ui.designsystem.bottomBarInsetDp
import com.litiangu.wuji.ui.home.HomeViewModel
import com.litiangu.wuji.ui.home.HomeScreen
import com.litiangu.wuji.ui.item.ItemEditScreen
import com.litiangu.wuji.ui.onboarding.AppSplash
import com.litiangu.wuji.ui.onboarding.OnboardingScreen
import com.litiangu.wuji.ui.category.CategorySelectScreen
import com.litiangu.wuji.ui.category.CustomCategoryDialog
import com.litiangu.wuji.ui.settings.SettingsScreen
import com.litiangu.wuji.ui.settings.BudgetAnchorScreen
import com.litiangu.wuji.ui.settings.ProfileEditScreen
import com.litiangu.wuji.ui.settings.TrashScreen
import com.litiangu.wuji.ui.settings.BackupScreen
import com.litiangu.wuji.ui.settings.AboutScreen
import com.litiangu.wuji.ui.theme.PendingDarkMode
import com.litiangu.wuji.ui.theme.WujiTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 冷启动兜底：Compose 首帧之前先把窗口底铺成已存偏好对应的颜色。
        // 资源里的 windowBackground 只跟随系统 uiMode，系统浅色 + 应用内深色时
        // 首帧会露出 #FAF9F9。这里同步读一次偏好（最多等 120ms，超时按跟随系统），
        // 读完再 setContent，保证第一帧就是对的。
        applyStartupWindowBackground()

        // [Step 54] 显式声明 edge-to-edge。
        //
        // targetSdk 36 在 Android 15+ 已被系统强制 edge-to-edge（窗口带
        // EDGE_TO_EDGE_ENFORCED 标志位铺满全屏），这里显式调用的作用只有两点：
        //   ① 让系统栏图标的明暗跟随主题切换（深色模式下不会白图标压在浅色背景上）
        //   ② 语义明确：本 App 自己负责 inset 处理，不指望系统代劳
        //
        // ⚠️ 它**不会提供任何底部 inset 高度**。底栏避让走 ui/designsystem/Insets.kt
        //    的 bottomBarInsetDp()，因为本机实测 navigationBars source 在手势
        //    导航下压根不存在，navigationBarsPadding() 会返回 0。
        //
        // 传 Color.TRANSPARENT（android.graphics 的 Int 常量）让系统栏完全透明，
        // 页面背景色直接透上去。注意 SystemBarStyle.auto 收的是 Int 类型的
        // ARGB 色值，不是 androidx.compose.ui.graphics.Color，两者同名易混。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
        )

        setContent {
            WujiApp()
        }
    }

    /**
     * 读取已持久化的深色三档，映射成实际明暗后设置 window 背景。
     * 只在 onCreate 调一次，运行中的变化由 WujiTheme 的 SideEffect 接管。
     */
    private fun applyStartupWindowBackground() {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        // 用 SharedPreferences 镜像同步取值。在主线程阻塞等 DataStore 首值会卡死启动，
        // 实测 splash 停留十几秒，因此只读这份缓存，首帧之后以 DataStore 的 Flow 为准。
        val pref = StartupTheme.read(applicationContext)
        val dark = when (pref) {
            DarkModePref.YES -> true
            DarkModePref.NO -> false
            else -> systemDark
        }
        window.setBackgroundDrawable(ColorDrawable(if (dark) 0xFF121212.toInt() else 0xFFFAF9F9.toInt()))
    }
}

/**
 * 应用根：读取深色偏好驱动主题（三档：跟随系统/浅色/深色）。
 *
 * ── [Step 55] 导航模型 ────────────────────────────────────────
 *
 * 改造前用 10 个并行布尔表示导航状态，带来两个问题：
 *   · 侧滑返回退到桌面（7 个页面漏注册 `BackHandler`）
 *   · 页面切换无任何过渡动画（`if` 硬挂载，composable 凭空出现）
 *
 * 现在收敛为**一个 overlay 栈**（`mutableStateListOf<Overlay>`）：
 *   · 返回 = 弹栈尾（[BackHandler] 统一在根层注册一次，覆盖全部页面）
 *   · 转场 = `AnimatedContent` 按 targetState 切换，自带进出场
 *   · 参数 = 随 sealed 分支携带，不再需要并行布尔同步
 *
 * 首页 / 分析页是**底层 tab**（不属于栈），由 [selectedTab] 表示：
 * 底栏在这两者间切换；栈非空时底栏隐藏，由 [showBottomBar] 控制。
 *
 * ── 返回语义（自上而下）────────────────────────────────────
 *
 * 1. 栈非空 → 弹栈尾（关掉当前 overlay，下层页面原样露出）
 * 2. 当前在分析页 → 回首页
 * 3. 当前在首页且列表未在顶部 → 滚回顶部（不清筛选、不改布局）
 * 4. 当前在首页且已在顶部 → 退出 App
 *
 * 引导页（`showOnboarding`）由 `launchedBefore` 决定，不属于栈，
 * 返回时按系统默认退出 —— 首次启动没有「上一页」可退。
 */
@Composable
fun WujiApp() {
    val context = LocalContext.current
    // 首值用 SharedPreferences 启动缓存（同步、毫秒级），而不是写死 FOLLOW_SYSTEM。
    // 否则冷启动前几帧按「跟随系统」= 浅色渲染，DataStore 异步读出 YES 后才切深色：
    // 大部分颜色当帧就变，但 M3 TopAppBar 的 containerColor 内部走 animateColorAsState，
    // 冷启动主线程正忙（建库、首查、首帧布局）时这段动画被拖得很长，顶栏就会白一阵再变黑。
    // 用缓存作首值后首帧即为正确主题，DataStore 读出的值与之相同，不再发生切换。
    val startupDarkMode = remember { StartupTheme.read(context) }
    val darkMode by remember { SettingsDataStore(context).darkModeFlow }
        .collectAsStateWithLifecycle(startupDarkMode)
    // 每次读到偏好就回写启动缓存，保证下次冷启动的窗口底与当前主题一致。
    LaunchedEffect(darkMode) {
        StartupTheme.write(context, darkMode)
        // DataStore 已追上设置页的乐观值 → 清空，回到以 DataStore 为准
        if (PendingDarkMode.pref == darkMode) PendingDarkMode.pref = null
    }
    // 设置页点选后先用乐观值立即切主题，不等 DataStore 回流（见 ui/theme/Theme.kt PendingDarkMode）
    val effectiveDarkMode = PendingDarkMode.pref ?: darkMode
    val darkTheme = when (effectiveDarkMode) {
        DarkModePref.YES -> true
        DarkModePref.NO -> false
        else -> isSystemInDarkTheme()
    }
    WujiTheme(darkTheme = darkTheme) {
        val homeTab = remember { BottomTab("主页", WujiIcons.House) }
        val analysisTab = remember { BottomTab("分析", WujiIcons.ChartLine) }
        // 设置从 overlay 提升为一级 tab，底栏三键常驻可见。
        val settingsTab = remember { BottomTab("设置", WujiIcons.Settings) }

        // ====== [Step 55] 导航状态 ======

        /**
         * overlay 栈。用 List 而非单个 Overlay?，这样「关子页后父页原样露出」
         * 才成立（设置 → 最近删除 → 返回，设置页的滚动位置与筛选状态都在）。
         */
        val overlayStack = remember { mutableStateListOf<Overlay>() }
        /** 「+」↔ 添加页 容器变换的共享状态（按钮位置 / 进度）。 */
        val fabReveal = remember { FabRevealState() }
        val currentOverlay = overlayStack.lastOrNull()

        /** 底层 tab：首页 ↔ 分析。不进栈，因为它们是常驻页面而非叠加层。 */
        var selectedTab by remember { mutableStateOf(homeTab) }

        // 打开一个 overlay。已存在则忽略（防连点导致同页压多层）。
        val openOverlay: (Overlay) -> Unit = { target ->
            if (!overlayStack.containsOverlay(target)) overlayStack.add(target)
        }
        // 弹栈顶。等价于旧代码里各个 `showXxx = false`，但语义统一。
        val popOverlay: () -> Unit = {
            if (overlayStack.isNotEmpty()) overlayStack.removeAt(overlayStack.lastIndex)
        }

        // 来源标记：null = 普通入口（首页 FAB），"onboarding" = 引导页 CTA
        var categorySource by remember { mutableStateOf<String?>(null) }
        // 用户在分类选择页选中的 categoryId，传给 ItemEditScreen 做预选
        var preselectedCategoryId by remember { mutableStateOf<Long?>(null) }
        // 自定义分类弹窗是否显示（AlertDialog 自带返回，不进栈）
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

        // 点 CTA 后本会话强制进首页，不等DataStore 异步落盘，消除表单延迟。
        var forceHome by remember { mutableStateOf(false) }

        val showOnboarding = isReady && !app.launchedBefore && !forceHome
        val showHome = isReady && homeVm != null && !showOnboarding

        /**
         * [Step 55] 首页滚动状态：供返回栈判断「是否已在顶部」。
         * HomeScreen 在三视图切换时各自持有滚动状态，这里只接收它上报的布尔值。
         */
        var homeListAtTop by remember { mutableStateOf(true) }

        /**
         * [Step 55] 首页回顶动作。HomeScreen 通过 onRequestScrollTop 暴露触发入口，
         * 因为滚动容器在 HomeScreen 内部，根层拿不到 state 对象。
         */
        var homeScrollTopSignal by remember { mutableStateOf(0) }
        val requestHomeScrollTop = { homeScrollTopSignal++ }

        // 退出 App：首页且列表已在顶部时的最终返回动作（选项 B）。
        val activity = context as? android.app.Activity
        val exitApp: () -> Unit = { activity?.finish() }

        // ============================================================
        // [Step 55] 统一返回栈：侧滑 / 导航键 / 手势返回全部走这里。
        //
        // 改造前只有分析页、分类选择页、表单页三处注册 `BackHandler`，
        // 设置页及其六个子页全部漏掉，导致侧滑返回直接结束 Activity。
        // 现在收敛到根层注册一次，新增页面自动被覆盖，不会再漏。
        //
        // enabled = showHome：仅在首页/分析页已就绪时接管返回。
        // 引导页与品牌启动页阶段（showHome 为 false）不接管，让系统默认退出 ——
        // 那两个页面没有「上一页」可退，拦截反而会让用户卡住退不出来。
        //
        // ⚠️ ItemEditScreen 的 BackHandler **保留不动**：它负责脏数据二次确认
        // （Step 14 放弃保护），删掉会丢失保护。Compose 规则是「后组合者优先」，
        // 表单在 AnimatedContent 内部组合，晚于本处，所以它天然优先 ——
        // 正好是想要的优先级顺序。
        // ============================================================
        BackHandler(enabled = showHome) {
            when {
                // 1. 有 overlay：弹栈顶（关掉当前页，下层原样露出）
                overlayStack.isNotEmpty() -> popOverlay()
                // 2. 在分析页：回首页
                selectedTab != homeTab -> selectedTab = homeTab
                // 3. 首页且列表未在顶部：先回顶部（选项 B），不清筛选不重置页面
                !homeListAtTop -> requestHomeScrollTop()
                // 4. 首页且已在顶部：才真正退出 App
                else -> exitApp()
            }
        }

        // 弹簧底部弹窗承载层：弹窗打开时在整页（含悬浮底栏）之上铺一层随进度渐暗的遮罩，
        // 弹窗本体叠在最上层。页面本身不缩放。见 ui/designsystem/SpringSheet.kt。
        val sheetBackdrop = remember { SheetBackdropState() }
        SheetBackdropHost(state = sheetBackdrop) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 根层必须自带主题底色。页面切换时新旧内容各自 alpha < 1，
                // 叠加后的空隙会透到 windowBackground；应用内深色而系统浅色时
                // 那片底是 #FAF9F9，于是整屏白闪。
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 背景图层：整页先录进一个 GraphicsLayer，底栏再取它做模糊副本。
            // 玻璃底下必须是真实页面的模糊像，用纯色或静态半透明都做不到。
            val backdrop = rememberGraphicsLayer()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        backdrop.record { this@drawWithContent.drawContent() }
                        drawLayer(backdrop)
                    }
            ) {
            // ====== 底层页面（首页 / 分析页 / 设置页，按 tab 切换）======
            // 三个 tab 常驻组合，切换只做淡入 + 轻微放大（见 ui/designsystem/KeepAliveTabHost.kt）。
            // 原先用 AnimatedContent：每次切换都从零组合新页，切回最重的首页时首帧卡几百毫秒，
            // 180ms 的转场被整帧吞掉，「分析 / 设置 → 主页」看起来就是直接出现。
            //
            // 分析页只是「底层 tab」，不是 overlay，不进栈 —— 它的返回语义是
            // 「回首页」而非「弹栈」，所以由根层 BackHandler 的第 2 分支处理。
            // 因此 Overlay 里虽然保留了 Analysis 分支作完整枚举，实际不会入栈，
            // 留它是为了让 Overlay 覆盖所有曾存在过的 overlay 形态，便于日后扩展。
            if (showHome) {
                KeepAliveTabHost(
                    selected = selectedTab,
                    tabs = listOf(homeTab, analysisTab, settingsTab),
                    // 「+」圆弧扩散 / 回收期间，背后首页随进度模糊（见 ui/designsystem/FabReveal.kt）
                    modifier = Modifier.fillMaxSize().fabRevealBackdropBlur(fabReveal),
                ) { tab, active ->
                    when (tab) {
                        settingsTab -> SettingsScreen(
                            // 作为 tab 页时没有「返回」语义，返回键由根层 BackHandler 收回首页。
                            asTab = true,
                            onBack = { selectedTab = homeTab },
                            onBudgetAnchor = { openOverlay(Overlay.BudgetAnchor) },
                            onTrash = { openOverlay(Overlay.Trash) },
                            onBackup = { openOverlay(Overlay.Backup) },
                            onAbout = { openOverlay(Overlay.About) },
                            onProfileEdit = { openOverlay(Overlay.ProfileEdit) },
                        )

                        analysisTab -> AnalysisScreen(
                            // 顶栏无返回箭头（4.0 设计稿形态）。
                            // 这里只是兜底（点不到），真正的返回路径是根层 BackHandler。
                            onClose = { selectedTab = homeTab },
                            // 常驻组合后需显式告知「用户正在看这一页」：
                            // 控制页内返回拦截与「进入即重读」（含从上层页面返回时）
                            isActive = active && overlayStack.isEmpty(),
                        )

                        else -> HomeScreen(
                            viewModel = homeVm!!,
                            onNew = {
                                // Step 12 改造：FAB 不再直进表单，先走分类选择页
                                categorySource = null
                                preselectedCategoryId = null
                                openOverlay(Overlay.CategorySelect)
                            },
                            onSetting = { selectedTab = settingsTab },
                            onEdit = { itemId -> openOverlay(Overlay.ItemEdit(itemId)) },
                            onBackup = { openOverlay(Overlay.Backup) },
                            // [Step 55] 把列表滚动状态与回顶请求接到根层
                            onScrollStateChanged = { atTop -> homeListAtTop = atTop },
                            scrollTopSignal = homeScrollTopSignal,
                        )
                    }
                }
            }

            // ====== 引导页（首次启动）======
            // 由 launchedBefore 决定，不属于 overlay 栈。
            if (showOnboarding) {
                OnboardingScreen(
                    onStart = {
                        // Step 12 改造：CTA 进分类选择页（source=onboarding），不再直进表单
                        forceHome = true
                        scope.launch { SettingsDataStore(context).setHasLaunchedBefore(true) }
                        categorySource = "onboarding"
                        preselectedCategoryId = null
                        openOverlay(Overlay.CategorySelect)
                    },
                )
            }

            // ====== 品牌启动页：仅覆盖进程预热期 ======
            if (!isReady) {
                AppSplash()
            }

            // ====== overlay 层（栈顶渲染）======
            // [Step 55] 改为 AnimatedContent 驱动，一次拿到退场动画与单一内容源。
            AnimatedContent(
                targetState = currentOverlay,
                transitionSpec = {
                    // 「+」↔ 添加页：动画全部由 FabRevealContainer 内的容器变换承担
                    val fabEnter = initialState == null && targetState == Overlay.CategorySelect &&
                        fabReveal.fromFab && fabReveal.fabBounds != null
                    val fabExit = initialState == Overlay.CategorySelect && targetState == null &&
                        fabReveal.isActive
                    if (fabEnter || fabExit) {
                        // using(null)：关闭 AnimatedContent 默认的 SizeTransform。
                        // 本 AnimatedContent 没有 fillMaxSize，栈空时内容尺寸为 0，默认 SizeTransform(clip = true)
                        // 会把容器从全屏弹簧缩到 0（锚点左上），一路从右下角裁掉页面 —— 恰好裁在圆弧收向的「+」处，
                        // 圆还没收进按钮就被切没，表现为关闭快结束时闪一下；打开时同理会被从左上角长大的矩形裁剪。
                        EnterTransition.None.togetherWith(ExitTransition.None).using(null)
                    } else if (targetState?.isDialog == true) {
                        // 弹窗类（法律条款页）：缩放 + 淡入
                        (scaleIn(
                            animationSpec = tween(200),
                            initialScale = 0.9f,
                        ) + fadeIn(animationSpec = tween(200)))
                            .togetherWith(
                                scaleOut(
                                    animationSpec = tween(160),
                                    targetScale = 0.9f,
                                ) + fadeOut(animationSpec = tween(160))
                            )
                    } else {
                        // 页面类：从右侧 1/4 位移入 + 淡入
                        // 位移只用 1/4 而非全宽推入，让用户第一帧就看到下层页面还在
                        (slideInHorizontally(
                            animationSpec = tween(280),
                        ) { it / 4 } + fadeIn(animationSpec = tween(200)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(240),
                                ) { it / 4 } + fadeOut(
                                    animationSpec = tween(160)
                                )
                            )
                    }
                },
                label = "overlay",
            ) { overlay ->
                when (overlay) {
                    // 栈空时无内容可渲染。AnimatedContent 的 targetState 为 null 时
                    // 走这一分支（退场动画结束后）。
                    null -> Unit

                    // [Step 55] 分析页已改为底层 tab（见上），不入栈。
                    // 保留分支只为保证 when 穷尽，实际不会命中。
                    Overlay.Analysis -> Unit

                    Overlay.CategorySelect -> {
                        if (homeVm != null) {
                            // 来自「+」：从按钮展开 / 缩回按钮（见 ui/designsystem/FabReveal.kt）。
                            // 被表单页替换时 exitToFab = false，保持全屏交给默认转场。
                            FabRevealContainer(
                                state = fabReveal,
                                exitToFab = { overlayStack.isEmpty() },
                            ) {
                                // 从 HomeViewModel 拿全部分类数据
                                val categories by homeVm.categories
                                    .collectAsStateWithLifecycle(emptyList())

                                CategorySelectScreen(
                                    categories = categories,
                                    source = categorySource,
                                    onCategorySelect = { categoryId ->
                                        // 选中分类 → 弹出选择页 → 压入表单（携带预选分类）
                                        // 用栈的 replace 语义：把栈顶换成表单，避免多一层
                                        preselectedCategoryId = categoryId
                                        overlayStack.removeAt(overlayStack.lastIndex)
                                        overlayStack.add(Overlay.ItemAdd(categoryId))
                                    },
                                    onCustomCategory = {
                                        // 点击「添加个人专属分类」→ 打开自定义弹窗
                                        showCustomCategoryDialog = true
                                    },
                                    onDismiss = {
                                        // 仅关闭本页，不动下层。来源分析页时分析页自然露出。
                                        popOverlay()
                                        categorySource = null
                                        preselectedCategoryId = null
                                    },
                                )

                                // 自定义分类弹窗（嵌套在分类选择页内）
                                // [Step 55] AlertDialog 自带 onDismissRequest，不进栈
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
                                                // 创建成功 → 关弹窗 + 选择页 → 进表单页携带新分类 id
                                                showCustomCategoryDialog = false
                                                preselectedCategoryId = newId
                                                overlayStack.removeAt(overlayStack.lastIndex)
                                                overlayStack.add(Overlay.ItemAdd(newId))
                                            }
                                        },
                                        onDismiss = {
                                            showCustomCategoryDialog = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    is Overlay.ItemAdd -> {
                        ItemEditScreen(
                            itemId = null,
                            initialCategoryId = overlay.preselectedCategoryId,
                            onDone = {
                                popOverlay()
                                preselectedCategoryId = null
                            },
                        )
                    }

                    is Overlay.ItemEdit -> {
                        ItemEditScreen(
                            itemId = overlay.itemId,
                            onDone = { popOverlay() },
                        )
                    }

                    Overlay.Settings -> {
                        SettingsScreen(
                            onBack = { popOverlay() },
                            onBudgetAnchor = { openOverlay(Overlay.BudgetAnchor) },
                            onTrash = { openOverlay(Overlay.Trash) },
                            onBackup = { openOverlay(Overlay.Backup) },
                            onAbout = { openOverlay(Overlay.About) },
                            onProfileEdit = { openOverlay(Overlay.ProfileEdit) },
                        )
                    }

                    Overlay.BudgetAnchor -> {
                        BudgetAnchorScreen(onBack = { popOverlay() })
                    }

                    Overlay.Trash -> {
                        val db = WujiDatabaseProvider.get(context)
                        TrashScreen(
                            onBack = { popOverlay() },
                            itemDao = db.itemDao(),
                            trashDao = db.trashDao(),
                        )
                    }

                    Overlay.Backup -> {
                        BackupScreen(onBack = { popOverlay() })
                    }

                    // [Step 58] 关于页与法律条款页共用同一个 AboutScreen，
                    // currentLegalPage 由根层回传，页面自身不持有导航状态。
                    // 两个分支渲染出的是同一棵composable 树，只是法律页多一层
                    // LegalContentOverlay；AnimatedContent 会把它们视作
                    // 同一 targetState（都是 AboutScreen 的调用）而跳过转场，
                    // 法律页的进出场动画由内部 Box 的层级切换承担。
                    //
                    // 关于页：currentLegalPage 恒为 null（法律页入栈后栈顶变了）
                    Overlay.About, is Overlay.AboutLegal -> {
                        AboutScreen(
                            // 法律页入栈时栈顶是 AboutLegal，弹它；
                            // 关于页入栈时栈顶是 About，弹它。两处都指向 popOverlay。
                            onBack = { popOverlay() },
                            currentLegalPage = (overlay as? Overlay.AboutLegal)?.page,
                            onOpenLegal = { page -> openOverlay(Overlay.AboutLegal(page)) },
                        )
                    }

                    Overlay.ProfileEdit -> {
                        ProfileEditScreen(onBack = { popOverlay() })
                    }
                }
            }

            }
            // ====== 底部悬浮导航（Step 32 修订：提升到 WujiApp 层）======
            // 首页与分析页共用同一底栏（首页 / + / 分析 三键），保证分析页也能一键切回首页。
            // 仅当「无 overlay」时显示；设置/新增/回收站等页打开时隐藏，避免遮挡其底部内容。
            // [Step 55] 判定从「一堆 showXxx 布尔」简化为「栈是否为空」。
            // 「+」容器变换进行中底栏保持在屏（透明度跟随进度），收回时最后与橙色圆重合交接；
            // 页面完全展开后移除，避免透明底栏吞掉添加页底部的点击。
            // fromFab：点「+」那一帧添加页已入栈、但过渡容器还没接管（isActive 仍为 false），
            // 若不算上它，底栏会被卸载一帧再重新挂载 —— 玻璃底重建、状态重置，视觉上就是「跳闪」。
            val fabBarVisible by remember { derivedStateOf { fabReveal.fromFab || fabReveal.barVisible } }
            val showBottomBar = showHome && (overlayStack.isEmpty() || fabBarVisible)
            if (showBottomBar) {
                // 悬浮胶囊：直接浮在页面内容上方，胶囊外区域透明，不再用不透明层遮挡下方内容。
                BottomPillBar(
                    items = listOf(homeTab, analysisTab, settingsTab),
                    selected = selectedTab,
                    onSelect = { tab -> selectedTab = tab },
                    onFabClick = {
                        categorySource = null
                        preselectedCategoryId = null
                        fabReveal.fromFab = true
                        openOverlay(Overlay.CategorySelect)
                    },
                    backdrop = backdrop,
                    onFabPositioned = { fabReveal.fabBounds = it },
                    revealState = fabReveal,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // [Step 54] 原为 navigationBarsPadding()，实测在红米 K80 至尊版
                        // 的手势导航下取到 0（系统压根不给 navigationBars source），
                        // 胶囊因此坐在手势条上。改为「系统值与24.dp 下限取大者」，
                        // 三键导航下会自动切回系统的 47.1 dp。详见 ui/designsystem/Insets.kt。
                        .padding(bottom = bottomBarInsetDp())
                        // 「+」圆弧扩散期间底栏不响应点击（淡出 / 淡入由 BottomPillBar 内按 revealState 处理）
                        .then(if (fabReveal.isActive) Modifier.consumeAllTouches() else Modifier),
                )
            }
        }
        }  // 闭合 SheetBackdropHost
    }
}
