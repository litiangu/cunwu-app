package com.litiangu.wuji.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.litiangu.wuji.data.db.ItemDao
import com.litiangu.wuji.data.db.CategoryDao
import com.litiangu.wuji.data.toUiModel
import com.litiangu.wuji.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val itemDao: ItemDao,
    private val categoryDao: CategoryDao,
    private val context: Context
) : ViewModel() {

    private val settings = SettingsDataStore(context)

    private val _selectedColorKey = MutableStateFlow<String?>(null)
    val selectedColorKey = _selectedColorKey.asStateFlow()

    fun selectColorKey(key: String?) { _selectedColorKey.value = key }

    val categories = categoryDao.observeAll()

    val items: StateFlow<List<ItemUiModel>> = combine(
        itemDao.observeAll(),
        categoryDao.observeAll(),
    ) { entities, cats ->
        val keyById = cats.associate { it.id to it.colorKey }
        entities.map { it.toUiModel().copy(categoryKey = it.categoryId?.let { id -> keyById[id] }) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val privacyMode = settings.privacyModeFlow
    val darkMode = settings.darkModeFlow

    fun togglePrivacyMode() {
        viewModelScope.launch {
            val current = settings.privacyModeFlow.first()
            settings.setPrivacyMode(!current)
        }
    }
}