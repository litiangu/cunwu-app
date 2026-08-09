package com.litiangu.wuji.data.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.litiangu.wuji.domain.DEFAULT_DAILY_BUDGET_ANCHOR
import com.litiangu.wuji.domain.DEFAULT_MONTHLY_BUDGET_ANCHOR
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 深色模式偏好（应用层语义，与 AppCompatDelegate 解耦）。
// value 存进 DataStore；toNightMode() 在应用主题时映射成 AppCompatDelegate 常量。
enum class DarkModePref(val value: Int) {
    FOLLOW_SYSTEM(0),
    NO(1),
    YES(2);

    fun toNightMode(): Int = when (this) {
        FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        NO -> AppCompatDelegate.MODE_NIGHT_NO
        YES -> AppCompatDelegate.MODE_NIGHT_YES
    }

    companion object {
        fun fromValue(v: Int): DarkModePref =
            entries.firstOrNull { it.value == v } ?: FOLLOW_SYSTEM
    }
}

// 每个 Context 复用同一个 DataStore 实例（名字 wuji_settings）。
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wuji_settings")

private val DARK_MODE_KEY = intPreferencesKey("dark_mode")     // 存 DarkModePref.value
private val PRIVACY_MODE_KEY = booleanPreferencesKey("privacy_mode")
private val HAPTIC_FEEDBACK_KEY = booleanPreferencesKey("haptic_feedback")  // 震动反馈开关
private val BUDGET_ANCHOR_KEY = doublePreferencesKey("budget_anchor")
private val MONTHLY_BUDGET_ANCHOR_KEY = doublePreferencesKey("monthly_budget_anchor")
private val VIEW_MODE_KEY = stringPreferencesKey("view_mode")   // list-default / card-2x2 / list-compact
private val HAS_LAUNCHED_BEFORE_KEY = booleanPreferencesKey("has_launched_before")  // 首次启动引导标记

// 设置存储：当前持久化深色三档 + 隐私模式开关。
// 供设置页（Step6）与主题切换复用。
class SettingsDataStore(private val context: Context) {

    val darkModeFlow: Flow<DarkModePref> = context.dataStore.data
        .map { prefs -> DarkModePref.fromValue(prefs[DARK_MODE_KEY] ?: DarkModePref.FOLLOW_SYSTEM.value) }

    val privacyModeFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[PRIVACY_MODE_KEY] ?: false }

    // 震动反馈开关（默认关闭）：开启时关键操作给轻震动，关闭时全局静默。
    val hapticFeedbackFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[HAPTIC_FEEDBACK_KEY] ?: false }

    suspend fun setDarkMode(pref: DarkModePref) {
        context.dataStore.edit { it[DARK_MODE_KEY] = pref.value }
    }

    suspend fun setPrivacyMode(enabled: Boolean) {
        context.dataStore.edit { it[PRIVACY_MODE_KEY] = enabled }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { it[HAPTIC_FEEDBACK_KEY] = enabled }
    }

    // 预算锚点（健康分 D2 价值维度比对的每日基准，默认 ¥100，对齐引擎常量）
    val budgetAnchorFlow: Flow<Double> = context.dataStore.data
        .map { prefs -> prefs[BUDGET_ANCHOR_KEY] ?: DEFAULT_DAILY_BUDGET_ANCHOR }

    suspend fun setBudgetAnchor(value: Double) {
        context.dataStore.edit { it[BUDGET_ANCHOR_KEY] = value }
    }

    // L1 月度预算锚点（Step 21）：默认 0 = 未配置。分析页预算卡仅在其 > 0 时渲染。
    val monthlyBudgetAnchorFlow: Flow<Double> = context.dataStore.data
        .map { prefs -> prefs[MONTHLY_BUDGET_ANCHOR_KEY] ?: DEFAULT_MONTHLY_BUDGET_ANCHOR }

    suspend fun setMonthlyBudgetAnchor(value: Double) {
        context.dataStore.edit { it[MONTHLY_BUDGET_ANCHOR_KEY] = value }
    }

    // 首页视图密度三态（list-default / card-2x2 / list-compact），默认 list-default
    val viewModeFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[VIEW_MODE_KEY] ?: "list-default" }

    suspend fun setViewMode(mode: String) {
        context.dataStore.edit { it[VIEW_MODE_KEY] = mode }
    }

    // 首次启动引导标记：默认 false（首次）。写入 true 后引导页不再展示。
    // 与深色/隐私等同库，互不影响。
    val hasLaunchedBeforeFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[HAS_LAUNCHED_BEFORE_KEY] ?: false }

    suspend fun setHasLaunchedBefore(value: Boolean) {
        context.dataStore.edit { it[HAS_LAUNCHED_BEFORE_KEY] = value }
    }
}
