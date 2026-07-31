package com.litiangu.wuji.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.litiangu.wuji.ui.analysis.AnalysisScreen
import com.litiangu.wuji.ui.home.HomeScreen
import com.litiangu.wuji.ui.item.ItemEditScreen
import com.litiangu.wuji.ui.settings.SettingsScreen

// 路由表：三个主 tab + 一个二级页（新增/编辑物品）
object Routes {
    const val HOME = "home"
    const val ANALYSIS = "analysis"
    const val SETTINGS = "settings"
    const val ITEM_EDIT = "item_edit/{itemId}"

    // itemId == null 或 "new" 表示新增；否则为编辑目标 id
    fun itemEdit(itemId: Long? = null): String = "item_edit/${itemId ?: "new"}"
}

/**
 * 应用级导航：NavHost 串联首页 / 分析 / 设置 / 物品编辑，
 * 底部导航栏只在三个主 tab 显示（编辑页隐藏）。
 */
@Composable
fun AppNav() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.HOME ||
        currentRoute == Routes.ANALYSIS ||
        currentRoute == Routes.SETTINGS

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.HOME,
                        onClick = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("首页") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.ANALYSIS,
                        onClick = {
                            navController.navigate(Routes.ANALYSIS) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                        label = { Text("分析") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = {
                            navController.navigate(Routes.SETTINGS) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("设置") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNew = { navController.navigate(Routes.itemEdit()) },
                    onEdit = { id -> navController.navigate(Routes.itemEdit(id)) }
                )
            }
            composable(Routes.ANALYSIS) {
                AnalysisScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(
                route = Routes.ITEM_EDIT,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType })
            ) { entry ->
                val raw = entry.arguments?.getString("itemId")
                val itemId = raw?.takeIf { it != "new" }?.toLongOrNull()
                ItemEditScreen(itemId = itemId, onDone = { navController.popBackStack() })
            }
        }
    }
}
