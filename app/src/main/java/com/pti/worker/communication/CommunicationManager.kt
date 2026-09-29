package com.pti.worker.communication

import com.pti.worker.alert.AlertEvent
import com.pti.worker.data.db.entities.ContactEntity

interface CommunicationManager {
    suspend fun sendAlert(alert: AlertEvent, contacts: List<ContactEntity>): Result<Unit>
    suspend fun sendPreAlert(alert: AlertEvent, contacts: List<ContactEntity>): Result<Unit>
}
