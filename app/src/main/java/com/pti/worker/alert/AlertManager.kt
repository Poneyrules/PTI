package com.pti.worker.alert

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.Manifest
import androidx.core.content.ContextCompat
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import com.pti.worker.MainActivity
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
        // Nettoyage interne sans callback : sinon on repasse PRE_ALERT → ACTIVE
        // juste après la transition, et le bouton d'acquittement n'apparaît jamais.
        cancelPreAlert(notifyCancelled = false)
        scope.launch {
            val settings = settingsManager.getSettings()
            val duration = settings.preAlertDurationMs
            Log.i(tag, "Pré-alerte démarrée ($type) – ${duration}ms")

            if (settings.vibrationEnabled) {
                vibrate(longArrayOf(0, 500, 200, 500, 200, 500, 200, 500))
            }
            if (settings.soundEnabled) {
                startAlarmSound(loop = true, customUri = settings.alertRingtoneUri)
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

    /**
     * Arrête pré-alerte / son / vibration.
     * @param notifyCancelled si true, notifie le callback (acquittement utilisateur).
     *                        Doit rester false lors d'un redémarrage interne (startPreAlert)
     *                        pour ne pas casser la transition ACTIVE → PRE_ALERT.
     */
    fun cancelPreAlert(notifyCancelled: Boolean = true) {
        preAlertJob?.cancel()
        preAlertJob = null
        cancelWindowJob?.cancel()
        cancelWindowJob = null
        stopAlarmSound()
        stopVibration()
        Log.i(tag, "Pré-alerte / alarme annulée (notify=$notifyCancelled)")
        if (notifyCancelled) {
            onAlertCancelled?.invoke()
        }
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
                message = message ?: defaultMessage(type),
                workerName = settings.workerName
            )

            Log.i(tag, "ALERTE DÉCLENCHÉE : $type")

            if (settings.vibrationEnabled) {
                vibrate(longArrayOf(0, 1000, 300, 1000, 300, 1000, 300, 1000))
            }
            if (settings.soundEnabled) {
                startAlarmSound(loop = true, customUri = settings.alertRingtoneUri)
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

            // Appel vocal vers contact priorité 1 pour immobilité et perte de verticalité
            if (type == AlertType.IMMOBILITY || type == AlertType.ORIENTATION) {
                callPriority1Contact()
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
            // Pas de callback : PtiManager.acknowledgeAlert gère la transition d'état
            cancelPreAlert(notifyCancelled = false)
        }
    }

    /**
     * Déclenche un appel téléphonique vers le contact d'urgence priorité 1,
     * en haut-parleur, sans laisser l'appli Téléphone prendre le premier plan.
     *
     * Utilise TelecomManager.placeCall + EXTRA_START_CALL_WITH_SPEAKERPHONE,
     * puis ramène immédiatement l'écran PTI au premier plan.
     */
    private suspend fun callPriority1Contact() {
        val contact = contactManager.getPriority1Contact()
        if (contact == null) {
            Log.w(tag, "Aucun contact priorité 1 pour l'appel")
            eventRepository.logEvent("CALL_SKIPPED", "Aucun contact d'urgence")
            return
        }
        val hasPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) {
            Log.e(tag, "Permission CALL_PHONE manquante")
            eventRepository.logEvent("CALL_FAILED", "Permission CALL_PHONE manquante")
            return
        }
        try {
            // Coupe l'alarme pour laisser place à l'appel en haut-parleur
            stopAlarmSound()
            stopVibration()

            val phone = contact.phoneNumber.filter { it.isDigit() || it == '+' }
            val uri = Uri.fromParts("tel", phone, null)

            // 1) Place l'appel via Telecom (HP demandé dès le départ, pas d'UI dialer classique)
            var placed = false
            try {
                val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (telecom != null) {
                    val extras = Bundle().apply {
                        putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true)
                    }
                    telecom.placeCall(uri, extras)
                    placed = true
                    Log.i(tag, "Appel TelecomManager placeCall HP vers $phone")
                }
            } catch (e: Exception) {
                Log.w(tag, "TelecomManager.placeCall échec, fallback ACTION_CALL", e)
            }

            // 2) Fallback si Telecom indisponible
            if (!placed) {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phone")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true)
                }
                context.startActivity(intent)
                Log.i(tag, "Appel fallback ACTION_CALL vers $phone")
            }

            eventRepository.logEvent(
                "CALL_STARTED",
                "Appel HP vers ${contact.name} (${contact.phoneNumber})"
            )

            // 3) Force HP + volume max (renforcé car certains OEM ignorent l'extra)
            enableSpeakerphoneForCall()

            // 4) Ramène l'app PTI au premier plan (l'UI Téléphone reste en arrière-plan)
            bringPtiToForeground()
        } catch (e: Exception) {
            Log.e(tag, "Échec appel vers ${contact.phoneNumber}", e)
            eventRepository.logEvent("CALL_FAILED", e.message)
        }
    }

    /** Remet l'écran principal PTI au premier plan pendant l'appel d'alerte. */
    private fun bringPtiToForeground() {
        scope.launch {
            // Court délai pour laisser Telecom démarrer l'appel
            delay(400)
            try {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(intent)
                Log.i(tag, "PTI ramené au premier plan")
            } catch (e: Exception) {
                Log.w(tag, "Impossible de ramener PTI au premier plan", e)
            }
            // Seconde tentative (OEM lents à afficher le dialer)
            delay(800)
            try {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Force le haut-parleur + volume max pendant l'appel d'urgence.
     * - Écoute l'état téléphonique (OFFHOOK) pour activer au bon moment
     * - Retente aussi à intervalles courts (certains constructeurs retardent l'audio)
     * - Volume STREAM_VOICE_CALL porté au maximum
     */
    private fun enableSpeakerphoneForCall() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Mémorise le volume d'appel pour restauration en fin d'appel
        val previousCallVolume = try {
            audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        } catch (_: Exception) {
            -1
        }

        fun setMaxCallVolume() {
            try {
                val maxCall = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                audioManager.setStreamVolume(
                    AudioManager.STREAM_VOICE_CALL,
                    maxCall,
                    0 // pas de UI volume
                )
                // Certains appareils routent le HP sur MUSIC / SYSTEM en plus
                try {
                    val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    // Ne force pas MUSIC au max pour ne pas casser le reste, seulement si très bas
                    val curMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (curMusic < maxMusic / 2) {
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            maxOf(curMusic, maxMusic * 3 / 4),
                            0
                        )
                    }
                } catch (_: Exception) {}
                Log.i(tag, "Volume appel max ($maxCall)")
            } catch (e: Exception) {
                Log.w(tag, "Impossible de maximiser le volume", e)
            }
        }

        fun setSpeakerOn() {
            try {
                // Désactive le mode silencieux / ne pas déranger pour l'audio d'appel si possible
                try {
                    if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                        // ne force pas ringerMode (permission policy), on force seulement le stream appel
                    }
                } catch (_: Exception) {}

                // MODE_IN_CALL puis IN_COMMUNICATION : selon OEM l'un ou l'autre route le HP
                try {
                    audioManager.mode = AudioManager.MODE_IN_CALL
                } catch (_: Exception) {}
                try {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                } catch (_: Exception) {}

                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val speaker = audioManager.availableCommunicationDevices
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) {
                        val ok = audioManager.setCommunicationDevice(speaker)
                        Log.i(tag, "Haut-parleur setCommunicationDevice=$ok isSpeaker=${audioManager.isSpeakerphoneOn}")
                    } else {
                        Log.i(tag, "Pas de BUILTIN_SPEAKER listé – isSpeakerphoneOn forcé")
                    }
                }

                // Volume max à chaque tentative (le système peut le baisser au décroché)
                setMaxCallVolume()
                Log.i(tag, "HP actif mode=${audioManager.mode} speaker=${audioManager.isSpeakerphoneOn}")
            } catch (e: Exception) {
                Log.w(tag, "Impossible d'activer le haut-parleur", e)
            }
        }

        fun restoreAudio() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                }
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
                if (previousCallVolume >= 0) {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_VOICE_CALL,
                        previousCallVolume,
                        0
                    )
                }
            } catch (_: Exception) {}
        }

        // Tentatives périodiques pendant ~20 s (temps de décroché + stabilisation)
        scope.launch {
            repeat(20) {
                delay(1000)
                setSpeakerOn()
            }
        }

        // Activation au passage en communication (plus fiable)
        try {
            val hasPhoneState = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPhoneState) {
                Log.w(tag, "READ_PHONE_STATE absente – retries timer uniquement")
                setSpeakerOn()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                            Log.i(tag, "Appel OFFHOOK → haut-parleur + volume max")
                            setSpeakerOn()
                            bringPtiToForeground()
                        }
                        if (state == TelephonyManager.CALL_STATE_IDLE) {
                            try {
                                telephonyManager.unregisterTelephonyCallback(this)
                            } catch (_: Exception) {}
                            restoreAudio()
                        }
                    }
                }
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                            Log.i(tag, "Appel OFFHOOK → haut-parleur + volume max")
                            setSpeakerOn()
                            bringPtiToForeground()
                        }
                        if (state == TelephonyManager.CALL_STATE_IDLE) {
                            try {
                                @Suppress("DEPRECATION")
                                telephonyManager.listen(this, PhoneStateListener.LISTEN_NONE)
                            } catch (_: Exception) {}
                            restoreAudio()
                        }
                    }
                }
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
            // Première tentative immédiate
            setSpeakerOn()
        } catch (e: Exception) {
            Log.w(tag, "Écoute état appel impossible", e)
            setSpeakerOn()
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

    private fun startAlarmSound(loop: Boolean, customUri: String? = null) {
        stopAlarmSound()
        try {
            val uri = when {
                !customUri.isNullOrBlank() -> android.net.Uri.parse(customUri)
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

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

