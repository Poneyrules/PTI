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
import com.pti.worker.data.db.entities.ContactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsCommunicationManager(private val context: Context) : CommunicationManager {

    private val tag = "SmsCommunication"

    suspend fun sendCoverageStatus(contact: ContactEntity, floor: Int, durationMinutes: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (!hasSmsPermission()) {
                return@withContext Result.failure(SecurityException("Permission SEND_SMS non accordée"))
            }
            if (floor !in -2..-1 || durationMinutes !in 0..60) {
                return@withContext Result.failure(IllegalArgumentException("Niveau ou durée invalide"))
            }
            try {
                sendSms(getSmsManager(), contact.phoneNumber, "${floor}_${durationMinutes}")
                Log.i(tag, "SMS hors couverture envoyé au contact de priorité 1")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(tag, "Échec SMS hors couverture", e)
                Result.failure(e)
            }
        }

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

        val message = AlertMessageFormatter.formatAlert(alert)
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
