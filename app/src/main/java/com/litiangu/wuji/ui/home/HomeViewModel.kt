package com.litiangu.wuji.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.litiangu.wuji.data.db.ItemDao
import com.litiangu.wuji.data.db.CategoryDao
import com.litiangu.wuji.data.settings.SettingsDataStore
import com.litiangu.wuji.data.settings.DarkModePref
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val itemDao: ItemDao,
    private val categoryDao: CategoryDao,
    private val context: Context
) : ViewModel() {

    private val settings = SettingsDataStore(context)
    private val _selectedCategory = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val selectedCategory = _selectedCategory.asStateFlow()

    fun selectCategory(key: String?) { _selectedCategory.value = key }

    val categories = categoryDao.observeAll()

    val rows = combine(itemDao.observeAll(), settings.privacyModeFlow) { entities, privacy ->
        mapToRows(entities, privacy, categoryDao)
    }

    val privacyMode = settings.privacyModeFlow
    val darkMode = settings.darkModeFlow

    fun togglePrivacyMode() {
        viewModelScope.launch {
            val current = settings.privacyModeFlow.first()
            settings.setPrivacyMode(!current)
        }
    }
}
