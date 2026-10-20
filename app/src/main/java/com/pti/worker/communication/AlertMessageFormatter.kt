package com.pti.worker.communication

import com.pti.worker.alert.AlertEvent
import com.pti.worker.alert.AlertType
import com.pti.worker.location.PtiLocation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keeps the alert payload identical whichever transport (SMS or SIP MESSAGE) is selected. */
object AlertMessageFormatter {
    fun formatAlert(alert: AlertEvent): String {
        val typeLabel = when (alert.type) {
            AlertType.MANUAL_SOS -> "SOS MANUEL"
            AlertType.FALL -> "CHUTE DÉTECTÉE"
            AlertType.IMMOBILITY -> "IMMOBILITÉ PROLONGÉE"
            AlertType.ORIENTATION -> "PERTE DE VERTICALITÉ"
            else -> "ALERTE PTI"
        }
        val location = formatLocation(alert.location)
        val time = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(Date(alert.timestamp))

        return buildString {
            append("ALERTE PTI – $typeLabel\n")
            alert.workerName?.trim()?.takeIf { it.isNotEmpty() }?.let { append("Travailleur : $it\n") }
            append("Heure : $time\n")
            if (location != null && alert.location != null) {
                append("Position : $location\n")
                append("Maps : https://maps.google.com/?q=${alert.location.latitude},${alert.location.longitude}\n")
            } else {
                append("Position : indisponible\n")
            }
            append(alert.message.orEmpty())
        }.take(400)
    }

    fun formatCoverageStatus(floor: Int, durationMinutes: Int): String = "${floor}_${durationMinutes}"

    private fun formatLocation(location: PtiLocation?): String? = location?.let {
        "%.5f, %.5f (±%.0fm)".format(it.latitude, it.longitude, it.accuracy)
    }
}
