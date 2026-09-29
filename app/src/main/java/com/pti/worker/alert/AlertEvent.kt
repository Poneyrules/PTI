package com.pti.worker.alert

import com.pti.worker.location.PtiLocation

enum class AlertType {
    MANUAL_SOS, FALL, IMMOBILITY, ORIENTATION, PRE_ALERT, CANCELLED, ACKNOWLEDGED
}

data class AlertEvent(
    val type: AlertType,
    val timestamp: Long = System.currentTimeMillis(),
    val location: PtiLocation? = null,
    val message: String? = null,
    val workerName: String? = null
)
