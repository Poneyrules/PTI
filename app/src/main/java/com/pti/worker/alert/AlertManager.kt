package com.pti.worker.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
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
    private var soundJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

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

            if (settings.vibrationEnabled) {
                vibrate(longArrayOf(0, 500, 200, 500, 200, 500, 200, 500))
            }
            if (settings.soundEnabled) {
                startAlarmSound(loop = true)
            }

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
        stopAlarmSound()
        stopVibration()
        Log.i(tag, "Pré-alerte / alarme annulée")
        onAlertCancelled?.invoke()
    }


    fun triggerAlert(type: AlertType, message: String? = null) {
        scope.launch {
            val settings = settingsManager.getSettings()

            // Tente d'obtenir une position même si le tracking n'est pas actif
            val location = try {
                locationManager.getCurrentLocation() ?: locationManager.lastLocation.value
            } catch (e: Exception) {
                locationManager.lastLocation.value
            }

            val alert = AlertEvent(
                type = type,
                location = location,
                message = message ?: defaultMessage(type)
            )

            Log.i(tag, "ALERTE DÉCLENCHÉE : $type")

            if (settings.vibrationEnabled) {
                vibrate(longArrayOf(0, 1000, 300, 1000, 300, 1000, 300, 1000))
            }
            if (settings.soundEnabled) {
                startAlarmSound(loop = true)
            }

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

            // Fenêtre d'annulation pour SOS manuel
            startCancelWindow(settings.sosCancelWindowMs)
        }
    }

    private fun startCancelWindow(durationMs: Long) {
        cancelWindowJob?.cancel()
        cancelWindowJob = scope.launch {
            delay(durationMs)
            Log.d(tag, "Fenêtre d'annulation expirée")
        }
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

    // ─── Son d'alarme ───────────────────────────────────────────

    private fun startAlarmSound(loop: Boolean) {
        stopAlarmSound()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            if (uri == null) {
                Log.w(tag, "Aucune URI son disponible")
                playFallbackTone()
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = loop
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }
            Log.i(tag, "Son d'alarme démarré (loop=$loop)")

            // Sécurité : arrête après 2 minutes max
            soundJob = scope.launch {
                delay(120_000)
                stopAlarmSound()
            }
        } catch (e: Exception) {
            Log.e(tag, "Impossible de jouer l'alarme", e)
            playFallbackTone()
        }
    }

    private fun playFallbackTone() {
        try {
            val toneGen = android.media.ToneGenerator(
                AudioManager.STREAM_ALARM,
                100
            )
            toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2000)
            soundJob = scope.launch {
                delay(2000)
                toneGen.release()
            }
        } catch (e: Exception) {
            Log.e(tag, "ToneGenerator impossible", e)
        }
    }

    private fun stopAlarmSound() {
        soundJob?.cancel()
        soundJob = null
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.w(tag, "Erreur arrêt son", e)
        }
        mediaPlayer = null
    }

    // ─── Vibration ──────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(pattern: LongArray) {
        try {
            val vibrator = getVibrator()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 = loop
            } else {
                vibrator.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.w(tag, "Vibration impossible", e)
        }
    }

    private fun stopVibration() {
        try {
            getVibrator().cancel()
        } catch (e: Exception) {
            Log.w(tag, "Arrêt vibration impossible", e)
        }
    }
}

