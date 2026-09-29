package com.pti.worker.alert

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.pti.worker.communication.CommunicationManager
import com.pti.worker.contact.ContactManager
import com.pti.worker.data.repository.EventRepository
import com.pti.worker.location.PtiLocationManager
import com.pti.worker.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlertManager(
    private val context: Context,
    private val communicationManager: CommunicationManager,
    private val contactManager: ContactManager,
    private val locationManager: PtiLocationManager,
    private val eventRepository: EventRepository,
    private val settingsManager: SettingsManager,
    private val scope: CoroutineScope
) {
    private val tag = "AlertManager"
    private var preAlertJob: Job? = null
    private var cancelWindowJob: Job? = null

    var onPreAlertStarted: ((AlertType, Long) -> Unit)? = null
    var onAlertTriggered: ((AlertEvent) -> Unit)? = null
    var onAlertCancelled: (() -> Unit)? = null
    var onPreAlertTimeout: (() -> Unit)? = null

    fun startPreAlert(type: AlertType, message: String? = null) {
        cancelPreAlert()
        scope.launch {
            val settings = settingsManager.getSettings()
            val duration = settings.preAlertDurationMs
            Log.i(tag, "Pré-alerte démarrée ($type) – ${duration}ms")
            vibrate(longArrayOf(0, 500, 200, 500, 200, 500))

            val location = locationManager.lastLocation.value
            eventRepository.logEvent(
                type = "PRE_ALERT",
                message = message ?: type.name,
                latitude = location?.latitude,
                longitude = location?.longitude,
                accuracy = location?.accuracy
            )
            onPreAlertStarted?.invoke(type, duration)

            preAlertJob = scope.launch {
                delay(duration)
                Log.i(tag, "Timeout pré-alerte → ALERT")
                onPreAlertTimeout?.invoke()
                triggerAlert(type, message)
            }
        }
    }

    fun cancelPreAlert() {
        preAlertJob?.cancel()
        preAlertJob = null
        cancelWindowJob?.cancel()
        cancelWindowJob = null
        onAlertCancelled?.invoke()
    }

    fun triggerAlert(type: AlertType, message: String? = null) {
        scope.launch {
            val settings = settingsManager.getSettings()
            val location = locationManager.getCurrentLocation() ?: locationManager.lastLocation.value
            val alert = AlertEvent(type = type, location = location, message = message ?: defaultMessage(type))

            Log.i(tag, "ALERTE DÉCLENCHÉE : $type")
            vibrate(longArrayOf(0, 1000, 300, 1000, 300, 1000))

            eventRepository.logEvent(
                type = type.name,
                message = alert.message,
                latitude = location?.latitude,
                longitude = location?.longitude,
                accuracy = location?.accuracy,
                altitude = location?.altitude
            )

            val contacts = contactManager.getEnabledContacts()
            val result = communicationManager.sendAlert(alert, contacts)
            if (result.isFailure) {
                Log.e(tag, "Échec envoi", result.exceptionOrNull())
                eventRepository.logEvent("SMS_SEND_FAILED", result.exceptionOrNull()?.message)
            }
            onAlertTriggered?.invoke(alert)

            if (type == AlertType.MANUAL_SOS) {
                startCancelWindow(settings.sosCancelWindowMs)
            }
        }
    }

    private fun startCancelWindow(durationMs: Long) {
        cancelWindowJob?.cancel()
        cancelWindowJob = scope.launch { delay(durationMs) }
    }

    fun acknowledgeAlert() {
        scope.launch {
            eventRepository.logEvent(type = "ACKNOWLEDGED", message = "Alerte acquittée")
            cancelPreAlert()
        }
    }

    private fun defaultMessage(type: AlertType) = when (type) {
        AlertType.MANUAL_SOS -> "SOS manuel déclenché"
        AlertType.FALL -> "Chute détectée"
        AlertType.IMMOBILITY -> "Immobilité prolongée détectée"
        AlertType.ORIENTATION -> "Perte de verticalité détectée"
        else -> "Alerte PTI"
    }

    @Suppress("DEPRECATION")
    private fun vibrate(pattern: LongArray) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.w(tag, "Vibration impossible", e)
        }
    }
}
