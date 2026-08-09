package com.litiangu.wuji.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litiangu.wuji.data.settings.DarkModePref
import com.litiangu.wuji.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页状态：深色三档 / 隐私模式 / 预算锚点。
 * 深色档位只持久化到 DataStore，由 MainActivity 的 WujiTheme(darkTheme = flow)
 * 驱动全局重组变色（不重建 Activity，因此切档不会退出设置页）。
 */
class SettingsViewModel(
    private val settings: SettingsDataStore
) : ViewModel() {

    val darkMode: StateFlow<DarkModePref> = settings.darkModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DarkModePref.FOLLOW_SYSTEM)

    val privacyMode: StateFlow<Boolean> = settings.privacyModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hapticFeedback: StateFlow<Boolean> = settings.hapticFeedbackFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // 预算锚点直接暴露 Flow（不用 StateFlow）。
    // StateFlow 的初始默认值会先于 DataStore 真实值到达，导致输入框先闪默认数字再跳变。
    // Flow + UI 层 NaN 初始值可等 DataStore 第一次真实发射后再回填，消除跳变。
    val budgetAnchor: Flow<Double> = settings.budgetAnchorFlow

    // L1 月度预算锚点（Step 21）：默认 0 = 未配置。同样用 Flow 避免输入框跳变。
    val monthlyBudgetAnchor: Flow<Double> = settings.monthlyBudgetAnchorFlow

    fun setDarkMode(pref: DarkModePref) {
        viewModelScope.launch { settings.setDarkMode(pref) }
    }

    fun setPrivacyMode(enabled: Boolean) {
        viewModelScope.launch { settings.setPrivacyMode(enabled) }
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { settings.setHapticFeedback(enabled) }
    }

    fun saveBudgetAnchor(value: Double) {
        viewModelScope.launch { settings.setBudgetAnchor(value) }
    }

    // L1 月度预算锚点写入入口（Step 21）。
    fun saveMonthlyBudgetAnchor(value: Double) {
        viewModelScope.launch { settings.setMonthlyBudgetAnchor(value) }
    }
}
