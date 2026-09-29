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
     * en haut-parleur, et tente de garder l'UI PTI au premier plan.
     *
     * Stratégie :
     * 1. TelecomManager.placeCall + EXTRA_START_CALL_WITH_SPEAKERPHONE
     * 2. Fallback ACTION_CALL
     * 3. Forçage agressif du haut-parleur (surtout Samsung One UI)
     * 4. Retour de l'écran PTI avec plusieurs tentatives + FullScreenIntent possible
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
            // Coupe l'alarme pour laisser place à l'appel
            stopAlarmSound()
            stopVibration()

            val phone = contact.phoneNumber.filter { it.isDigit() || it == '+' }
            val uri = Uri.fromParts("tel", phone, null)

            var placed = false

            // ── 1. Tentative principale via TelecomManager ──────────────────────
            try {
                val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (telecom != null) {
                    val extras = Bundle().apply {
                        putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true)
                        // Aide certains OEM (Samsung inclus)
                        putBoolean("android.telecom.extra.START_CALL_WITH_SPEAKERPHONE", true)
                    }
                    telecom.placeCall(uri, extras)
                    placed = true
                    Log.i(tag, "Appel TelecomManager.placeCall (HP demandé) → $phone")
                }
            } catch (e: Exception) {
                Log.w(tag, "TelecomManager.placeCall échec", e)
            }

            // ── 2. Fallback ACTION_CALL ─────────────────────────────────────────
            if (!placed) {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phone")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true)
                    putExtra("android.telecom.extra.START_CALL_WITH_SPEAKERPHONE", true)
                }
                context.startActivity(intent)
                Log.i(tag, "Appel fallback ACTION_CALL → $phone")
            }

            eventRepository.logEvent(
                "CALL_STARTED",
                "Appel vers ${contact.name} (${contact.phoneNumber})"
            )

            // ── 3. Forçage haut-parleur + volume ────────────────────────────────
            enableSpeakerphoneForCall()

            // ── 4. Tentative de retour de l'UI PTI ───────────────────────────────
            bringPtiToForeground()

        } catch (e: Exception) {
            Log.e(tag, "Échec appel vers ${contact.phoneNumber}", e)
            eventRepository.logEvent("CALL_FAILED", e.message ?: "unknown")
        }
    }

    /**
     * Remet l'écran principal PTI au premier plan pendant l'appel d'alerte.
     * Plusieurs tentatives + délais adaptés aux OEM lents (Samsung).
     */
    private fun bringPtiToForeground() {
        scope.launch {
            // Délais plus longs et plusieurs tentatives → meilleure chance face à One UI
            val delays = listOf(600L, 1400L, 2500L, 4000L)

            for ((index, delayMs) in delays.withIndex()) {
                delay(delayMs)
                try {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        // Aide à remonter l'activité même si le dialer est au-dessus
                        addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                    }
                    context.startActivity(intent)
                    Log.i(tag, "PTI ramené au premier plan (tentative ${index + 1})")
                } catch (e: Exception) {
                    Log.w(tag, "Échec bringToForeground tentative ${index + 1}", e)
                }
            }
        }
    }

    /**
     * Force le haut-parleur + volume max pendant l'appel d'urgence.
     * Version renforcée pour Samsung One UI / Galaxy S20 FE et similaires.
     *
     * - Écoute CALL_STATE_OFFHOOK
     * - Retries très fréquents les premières secondes
     * - Utilise setCommunicationDevice (API 31+) + isSpeakerphoneOn
     * - Volume STREAM_VOICE_CALL au maximum
     */
    private fun enableSpeakerphoneForCall() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val previousCallVolume = try {
            audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        } catch (_: Exception) {
            -1
        }

        fun setMaxCallVolume() {
            try {
                val maxCall = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxCall, 0)

                // Sur certains Samsung le volume musique influence aussi
                val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val curMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (curMusic < maxMusic * 0.8) {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        (maxMusic * 0.9).toInt(),
                        0
                    )
                }
            } catch (e: Exception) {
                Log.w(tag, "Impossible de maxer le volume", e)
            }
        }

        fun setSpeakerOn() {
            try {
                // 1. Modes audio classiques
                try {
                    audioManager.mode = AudioManager.MODE_IN_CALL
                } catch (_: Exception) {}

                try {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                } catch (_: Exception) {}

                // 2. Ancienne API (toujours utile sur Samsung)
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true

                // 3. API moderne (Android 12+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val devices = audioManager.availableCommunicationDevices
                    val speaker = devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speaker != null) {
                        val ok = audioManager.setCommunicationDevice(speaker)
                        Log.i(tag, "setCommunicationDevice(SPEAKER) = $ok")
                    } else {
                        Log.w(tag, "Aucun BUILTIN_SPEAKER trouvé")
                    }
                }

                setMaxCallVolume()

                Log.i(
                    tag,
                    "HP forcé → mode=${audioManager.mode} " +
                            "isSpeaker=${audioManager.isSpeakerphoneOn}"
                )
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

        // ── Retries agressifs (surtout les 8 premières secondes) ───────────────
        scope.launch {
            // Phase intensive
            repeat(16) {          // 16 × 500 ms = 8 s
                delay(500)
                setSpeakerOn()
            }
            // Phase de maintien
            repeat(12) {          // encore 12 s
                delay(1000)
                setSpeakerOn()
            }
        }

        // ── Écoute de l'état téléphonique ──────────────────────────────────────
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
                        when (state) {
                            TelephonyManager.CALL_STATE_OFFHOOK -> {
                                Log.i(tag, "OFFHOOK → forçage HP + retour PTI")
                                setSpeakerOn()
                                // Petite attente puis on force encore
                                scope.launch {
                                    delay(300)
                                    setSpeakerOn()
                                    delay(700)
                                    setSpeakerOn()
                                }
                                bringPtiToForeground()
                            }
                            TelephonyManager.CALL_STATE_IDLE -> {
                                try {
                                    telephonyManager.unregisterTelephonyCallback(this)
                                } catch (_: Exception) {}
                                restoreAudio()
                                Log.i(tag, "Appel terminé → audio restauré")
                            }
                        }
                    }
                }
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        when (state) {
                            TelephonyManager.CALL_STATE_OFFHOOK -> {
                                Log.i(tag, "OFFHOOK → forçage HP + retour PTI")
                                setSpeakerOn()
                                scope.launch {
                                    delay(300)
                                    setSpeakerOn()
                                    delay(700)
                                    setSpeakerOn()
                                }
                                bringPtiToForeground()
                            }
                            TelephonyManager.CALL_STATE_IDLE -> {
                                try {
                                    @Suppress("DEPRECATION")
                                    telephonyManager.listen(this, PhoneStateListener.LISTEN_NONE)
                                } catch (_: Exception) {}
                                restoreAudio()
                            }
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

