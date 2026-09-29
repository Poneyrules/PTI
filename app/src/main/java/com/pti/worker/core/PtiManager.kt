package com.pti.worker.core

import android.content.Context
import android.util.Log
import com.pti.worker.alert.AlertManager
import com.pti.worker.alert.AlertType
import com.pti.worker.communication.CommunicationManager
import com.pti.worker.contact.ContactManager
import com.pti.worker.data.repository.EventRepository
import com.pti.worker.detection.FallDetectionManager
import com.pti.worker.detection.ImmobilityDetectionManager
import com.pti.worker.detection.OrientationManager
import com.pti.worker.location.PtiLocationManager
import com.pti.worker.settings.SettingsManager
import com.pti.worker.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PtiManager(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val eventRepository: EventRepository,
    private val contactManager: ContactManager,
    private val communicationManager: CommunicationManager,
    private val locationManager: PtiLocationManager,
    private val networkMonitor: NetworkMonitor
) {
    private val tag = "PtiManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val stateMachine = PtiStateMachine()

    /** true uniquement quand le mode surveillance PTI est armé (capteurs actifs) */
    @Volatile
    private var ptiArmed = false


    private val alertManager = AlertManager(
        context, communicationManager, contactManager, locationManager,
        eventRepository, settingsManager, scope
    )

    private lateinit var fallDetection: FallDetectionManager
    private lateinit var immobilityDetection: ImmobilityDetectionManager
    private lateinit var orientationDetection: OrientationManager

    private val _timeSinceLastActivity = MutableStateFlow(0L)
    val timeSinceLastActivity: StateFlow<Long> = _timeSinceLastActivity.asStateFlow()

    private val _isNetworkAvailable = MutableStateFlow(true)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    init {
        setupDetections()
        setupAlertCallbacks()
        observeNetwork()
        observeActivityTimer()
    }

    private fun setupDetections() {
        fallDetection = FallDetectionManager(context) {
            onDetectionEvent(PtiEvent.FallDetected, AlertType.FALL)
        }
        immobilityDetection = ImmobilityDetectionManager(context) {
            onDetectionEvent(PtiEvent.ImmobilityDetected, AlertType.IMMOBILITY)
        }
        orientationDetection = OrientationManager(context) {
            onDetectionEvent(PtiEvent.OrientationAbnormal, AlertType.ORIENTATION)
        }
    }

    private fun setupAlertCallbacks() {
        alertManager.onPreAlertTimeout = { stateMachine.transition(PtiEvent.PreAlertTimeout) }
        alertManager.onAlertCancelled = { stateMachine.transition(PtiEvent.CancelAlert) }
    }

    private fun observeNetwork() {
        scope.launch {
            networkMonitor.isOnline.collectLatest { online ->
                _isNetworkAvailable.value = online
                if (!online) eventRepository.logEvent("NETWORK_LOST", "Connexion réseau perdue")
            }
        }
    }

    private fun observeActivityTimer() {
        scope.launch {
            while (true) {
                delay(1000)
                if (stateMachine.isActiveOrHigher()) {
                    _timeSinceLastActivity.value = immobilityDetection.getTimeSinceLastMovement()
                }
            }
        }
    }

    fun activate() {
        if (!stateMachine.transition(PtiEvent.Activate)) return
        scope.launch {
            try {
                val settings = settingsManager.getSettings()
                fallDetection.updateSettings(settings)
                immobilityDetection.updateSettings(settings)
                orientationDetection.updateSettings(settings)
                locationManager.startTracking(settings.locationIntervalMs)
                fallDetection.start()
                immobilityDetection.start()
                orientationDetection.start()
                eventRepository.logEvent("PTI_ACTIVATED", "Mode PTI activé")
                ptiArmed = true
                stateMachine.transition(PtiEvent.ArmingComplete)
                Log.i(tag, "PTI ACTIVE")

            } catch (e: Exception) {
                Log.e(tag, "Échec armement", e)
                eventRepository.logEvent("ERROR", e.message)
                stateMachine.transition(PtiEvent.ArmingFailed)
            }
        }
    }

    fun deactivate() {
        ptiArmed = false
        stateMachine.transition(PtiEvent.Deactivate)
        stopAll()
        scope.launch { eventRepository.logEvent("PTI_DEACTIVATED", "Mode PTI désactivé") }
        Log.i(tag, "PTI DISABLED")
    }


    /**
     * SOS manuel : disponible même si le PTI n'est pas activé.
     * Déclenche immédiatement l'alerte (SMS + son + vibration).
     */
    fun triggerManualSos() {
        val current = stateMachine.state.value
        Log.i(tag, "SOS manuel demandé (état=$current)")

        when (current) {
            PtiState.DISABLED, PtiState.ERROR -> {
                // SOS hors surveillance PTI → alerte directe
                stateMachine.forceState(PtiState.ALERT)
                alertManager.triggerAlert(AlertType.MANUAL_SOS)
            }
            PtiState.ACTIVE, PtiState.PRE_ALERT, PtiState.ARMING -> {
                stateMachine.transition(PtiEvent.ManualSos)
                // Si on était en ACTIVE, ManualSos → PRE_ALERT puis on force l'alerte immédiate
                if (stateMachine.state.value == PtiState.PRE_ALERT) {
                    stateMachine.transition(PtiEvent.PreAlertTimeout)
                }
                alertManager.triggerAlert(AlertType.MANUAL_SOS)
            }
            PtiState.ALERT -> {
                // Déjà en alerte : relance l'envoi
                alertManager.triggerAlert(AlertType.MANUAL_SOS)
            }
            else -> {
                stateMachine.forceState(PtiState.ALERT)
                alertManager.triggerAlert(AlertType.MANUAL_SOS)
            }
        }
    }


    fun cancelCurrentAlert() {
        Log.i(tag, "cancelCurrentAlert (état=${stateMachine.state.value}, armed=$ptiArmed)")
        // Empêche le callback de double-transition
        val previousCallback = alertManager.onAlertCancelled
        alertManager.onAlertCancelled = null
        alertManager.cancelPreAlert()
        alertManager.onAlertCancelled = previousCallback

        if (::immobilityDetection.isInitialized) {
            immobilityDetection.notifyUserActivity()
        }

        when {
            ptiArmed -> {
                // Retour surveillance active (pré-alerte ou alerte acquittée)
                if (stateMachine.state.value == PtiState.PRE_ALERT ||
                    stateMachine.state.value == PtiState.ALERT
                ) {
                    stateMachine.forceState(PtiState.ACTIVE)
                } else {
                    stateMachine.transition(PtiEvent.CancelAlert)
                }
                Log.i(tag, "Pré-alerte/alerte acquittée → ACTIVE")
            }
            else -> {
                stateMachine.forceState(PtiState.DISABLED)
                Log.i(tag, "Alerte hors PTI acquittée → DISABLED")
            }
        }
        scope.launch { eventRepository.logEvent("ALERT_CANCELLED", "Alerte annulée par l'utilisateur") }
    }

    fun acknowledgeAlert() {
        alertManager.acknowledgeAlert()
        if (ptiArmed) {
            stateMachine.transition(PtiEvent.AcknowledgeAlert) // → ACTIVE
        } else {
            stateMachine.forceState(PtiState.DISABLED)
        }
    }


    private fun onDetectionEvent(event: PtiEvent, alertType: AlertType) {
        if (stateMachine.state.value != PtiState.ACTIVE) return
        if (stateMachine.transition(event)) {
            alertManager.startPreAlert(alertType)
        }
    }

    private fun stopAll() {
        fallDetection.stop()
        immobilityDetection.stop()
        orientationDetection.stop()
        locationManager.stopTracking()
        alertManager.cancelPreAlert()
    }

    fun getOrientationManager(): OrientationManager = orientationDetection

    fun calibrateOrientation(): Pair<Float, Float> {
        val result = orientationDetection.calibrateNow()
        scope.launch {
            val s = settingsManager.getSettings()
            settingsManager.updateSettings(
                s.copy(calibrationPitch = result.first, calibrationRoll = result.second)
            )
        }
        return result
    }

    fun release() { stopAll() }
}
