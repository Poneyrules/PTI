package com.pti.worker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pti.worker.PtiApplication
import com.pti.worker.data.db.entities.SettingsEntity
import com.pti.worker.settings.SettingsManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(
        (application as PtiApplication).database.settingsDao()
    )

    val settings: StateFlow<SettingsEntity> = settingsManager.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsEntity())

    fun updateSettings(newSettings: SettingsEntity) {
        viewModelScope.launch { settingsManager.updateSettings(newSettings) }
    }

    fun setFallEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setFallDetectionEnabled(enabled) }
    }

    fun setImmobilityEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setImmobilityEnabled(enabled) }
    }

    fun setOrientationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setOrientationEnabled(enabled) }
    }
}
