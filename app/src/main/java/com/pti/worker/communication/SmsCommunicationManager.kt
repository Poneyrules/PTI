package com.pti.worker.communication

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.pti.worker.alert.AlertEvent
import com.pti.worker.alert.AlertType
import com.pti.worker.data.db.entities.ContactEntity
import com.pti.worker.location.PtiLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsCommunicationManager(private val context: Context) : CommunicationManager {

    private val tag = "SmsCommunication"

    override suspend fun sendAlert(
        alert: AlertEvent,
        contacts: List<ContactEntity>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasSmsPermission()) {
            return@withContext Result.failure(SecurityException("Permission SEND_SMS non accordée"))
        }
        if (contacts.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Aucun contact d'urgence"))
        }

        val message = buildAlertMessage(alert)
        var successCount = 0
        var lastError: Exception? = null
        val smsManager = getSmsManager()

        for (contact in contacts) {
            try {
                sendSms(smsManager, contact.phoneNumber, message)
                successCount++
                Log.i(tag, "SMS envoyé à ${contact.name} (${contact.phoneNumber})")
            } catch (e: Exception) {
                Log.e(tag, "Échec SMS vers ${contact.phoneNumber}", e)
                lastError = e
            }
        }

        if (successCount > 0) Result.success(Unit)
        else Result.failure(lastError ?: Exception("Aucun SMS envoyé"))
    }

    override suspend fun sendPreAlert(alert: AlertEvent, contacts: List<ContactEntity>): Result<Unit> {
        Log.i(tag, "Pré-alerte (pas d'envoi SMS) : ${alert.type}")
        return Result.success(Unit)
    }

    private fun buildAlertMessage(alert: AlertEvent): String {
        val typeLabel = when (alert.type) {
            AlertType.MANUAL_SOS -> "SOS MANUEL"
            AlertType.FALL -> "CHUTE DÉTECTÉE"
            AlertType.IMMOBILITY -> "IMMOBILITÉ PROLONGÉE"
            AlertType.ORIENTATION -> "PERTE DE VERTICALITÉ"
            else -> "ALERTE PTI"
        }
        val locationPart = formatLocation(alert.location)
        val timePart = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(Date(alert.timestamp))

        return buildString {
            append("ALERTE PTI – $typeLabel\n")
            val name = alert.workerName?.trim().orEmpty()
            if (name.isNotEmpty()) {
                append("Travailleur : $name\n")
            }
            append("Heure : $timePart\n")
            if (locationPart != null && alert.location != null) {
                append("Position : $locationPart\n")
                append("Maps : https://maps.google.com/?q=${alert.location.latitude},${alert.location.longitude}\n")
            } else {
                append("Position : indisponible\n")
            }
            append(alert.message ?: "")
        }.take(400)
    }

    private fun formatLocation(location: PtiLocation?): String? {
        if (location == null) return null
        return "%.5f, %.5f (±%.0fm)".format(location.latitude, location.longitude, location.accuracy)
    }

    private fun sendSms(smsManager: SmsManager, phoneNumber: String, message: String) {
        val sentIntent = PendingIntent.getBroadcast(
            context, phoneNumber.hashCode(), Intent("SMS_SENT"), PendingIntent.FLAG_IMMUTABLE
        )
        val parts = smsManager.divideMessage(message)
        if (parts.size == 1) {
            smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, null)
        } else {
            val sentIntents = ArrayList<PendingIntent>()
            repeat(parts.size) { sentIntents.add(sentIntent) }
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
        }
    }

    private fun getSmsManager(): SmsManager {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
    }
}
