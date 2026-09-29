package com.pti.worker.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pti.worker.util.Constants

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val immobilityThresholdMs: Long = Constants.Defaults.IMMODILITY_THRESHOLD_MS,
    val preAlertDurationMs: Long = Constants.Defaults.PRE_ALERT_DURATION_MS,
    val immobilityEnabled: Boolean = true,
    val immobilitySensitivity: Float = 0.5f,
    val fallDetectionEnabled: Boolean = true,
    val fallAccelerationThreshold: Float = Constants.Defaults.FALL_ACCELERATION_THRESHOLD,
    val fallImpactThreshold: Float = Constants.Defaults.FALL_IMPACT_THRESHOLD,
    val fallPostImpactStillnessMs: Long = Constants.Defaults.FALL_POST_IMPACT_STILLNESS_MS,
    val fallSensitivity: Float = 0.6f,
    val orientationDetectionEnabled: Boolean = true,
    val orientationAbnormalDurationMs: Long = Constants.Defaults.ORIENTATION_ABNORMAL_DURATION_MS,
    val orientationThresholdDegrees: Float = 60f,
    val locationIntervalMs: Long = Constants.Defaults.LOCATION_INTERVAL_MS,
    val sosCancelWindowMs: Long = Constants.Defaults.SOS_CANCEL_WINDOW_MS,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val restoreAfterBoot: Boolean = true,
    /** URI de la sonnerie d'alerte (null = sonnerie alarme système par défaut) */
    val alertRingtoneUri: String? = null,
    val alertRingtoneName: String? = null
)
