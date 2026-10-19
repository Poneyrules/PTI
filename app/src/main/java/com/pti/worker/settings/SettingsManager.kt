package com.pti.worker.settings

import com.pti.worker.data.db.SettingsDao
import com.pti.worker.data.db.entities.SettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsManager(private val settingsDao: SettingsDao) {

    val settingsFlow: Flow<SettingsEntity> = settingsDao.getSettings()
        .map { decode(it ?: SettingsEntity()) }

    suspend fun getSettings(): SettingsEntity {
        return decode(settingsDao.getSettingsOnce() ?: SettingsEntity().also {
            settingsDao.insert(it)
        })
    }

    suspend fun updateSettings(settings: SettingsEntity) {
        settingsDao.update(settings.copy(sipPassword = SipSecretStorage.encrypt(settings.sipPassword)))
    }

    suspend fun setFallDetectionEnabled(enabled: Boolean) {
        val current = getSettings()
        updateSettings(current.copy(fallDetectionEnabled = enabled))
    }

    suspend fun setImmobilityEnabled(enabled: Boolean) {
        val current = getSettings()
        updateSettings(current.copy(immobilityEnabled = enabled))
    }

    suspend fun setOrientationEnabled(enabled: Boolean) {
        val current = getSettings()
        updateSettings(current.copy(orientationDetectionEnabled = enabled))
    }

    suspend fun setWorkerName(name: String) {
        val current = getSettings()
        updateSettings(current.copy(workerName = name.trim().ifBlank { null }))
    }

    private fun decode(settings: SettingsEntity): SettingsEntity =
        settings.copy(sipPassword = SipSecretStorage.decrypt(settings.sipPassword))
}
