package com.litiangu.wuji.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litiangu.wuji.data.settings.DarkModePref
import com.litiangu.wuji.data.settings.SettingsDataStore
import com.litiangu.wuji.domain.DEFAULT_DAILY_BUDGET_ANCHOR
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

    val budgetAnchor: StateFlow<Double> = settings.budgetAnchorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_DAILY_BUDGET_ANCHOR)

    fun setDarkMode(pref: DarkModePref) {
        viewModelScope.launch { settings.setDarkMode(pref) }
    }

    fun setPrivacyMode(enabled: Boolean) {
        viewModelScope.launch { settings.setPrivacyMode(enabled) }
    }

    fun saveBudgetAnchor(value: Double) {
        viewModelScope.launch { settings.setBudgetAnchor(value) }
    }
}
