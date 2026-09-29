package com.pti.worker.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pti.worker.PtiApplication
import com.pti.worker.core.PtiState
import com.pti.worker.location.PtiLocation
import com.pti.worker.service.PtiForegroundService
import com.pti.worker.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val ptiState: PtiState = PtiState.DISABLED,
    val lastLocation: PtiLocation? = null,
    val isGpsAvailable: Boolean = false,
    val isNetworkAvailable: Boolean = true,
    val timeSinceLastActivityMs: Long = 0L,
    val showSosConfirmation: Boolean = false,
    val showWorkerNameDialog: Boolean = false,
    val workerName: String = "",
    val missingPermissions: List<String> = emptyList(),
    val permissionBlocked: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PtiApplication
    private val ptiManager = app.ptiManager
    private val locationManager = app.locationManager

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val events = app.eventRepository.getRecentEvents(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Orientation / calibrage (exposes live values)
    private val _orientationPitch = MutableStateFlow(0f)
    val orientationPitch = _orientationPitch.asStateFlow()
    private val _orientationRoll = MutableStateFlow(0f)
    val orientationRoll = _orientationRoll.asStateFlow()
    private val _orientationDeviation = MutableStateFlow(0f)
    val orientationDeviation = _orientationDeviation.asStateFlow()
    private val _orientationThreshold = MutableStateFlow(45f)
    val orientationThreshold = _orientationThreshold.asStateFlow()
    private val _isOrientationCalibrated = MutableStateFlow(false)
    val isOrientationCalibrated = _isOrientationCalibrated.asStateFlow()

    init {
        observeState()
        checkPermissions()
        loadWorkerName()
    }

    private fun loadWorkerName() {
        viewModelScope.launch {
            val name = app.settingsManager.getSettings().workerName.orEmpty()
            _uiState.value = _uiState.value.copy(workerName = name)
        }
    }

    private fun observeState() {
        // Observe la MÊME instance que le Service
        viewModelScope.launch {
            ptiManager.stateMachine.state.collect { state ->
                Log.i("MainViewModel", "État PTI → $state")
                _uiState.value = _uiState.value.copy(ptiState = state)
            }
        }
        viewModelScope.launch {
            locationManager.lastLocation.collect { loc ->
                _uiState.value = _uiState.value.copy(lastLocation = loc)
            }
        }
        viewModelScope.launch {
            locationManager.isGpsAvailable.collect { available ->
                _uiState.value = _uiState.value.copy(isGpsAvailable = available)
            }
        }
        viewModelScope.launch {
            ptiManager.isNetworkAvailable.collect { available ->
                _uiState.value = _uiState.value.copy(isNetworkAvailable = available)
            }
        }
        viewModelScope.launch {
            ptiManager.timeSinceLastActivity.collect { time ->
                _uiState.value = _uiState.value.copy(timeSinceLastActivityMs = time)
            }
        }
    }

    fun checkPermissions() {
        val missing = PermissionHelper.missingPermissions(getApplication())
        _uiState.value = _uiState.value.copy(
            missingPermissions = missing,
            permissionBlocked = missing.isNotEmpty()
        )
    }

    /**
     * Demande le nom du travailleur avant d'activer le PTI.
     * Le nom est ensuite inclus dans tous les SMS d'alerte.
     */
    fun activatePti() {
        checkPermissions()
        if (!PermissionHelper.hasAllRequiredPermissions(getApplication())) {
            Log.w("MainViewModel", "Activation bloquée – permissions manquantes: ${_uiState.value.missingPermissions}")
            _uiState.value = _uiState.value.copy(permissionBlocked = true)
            return
        }
        viewModelScope.launch {
            val saved = app.settingsManager.getSettings().workerName.orEmpty()
            _uiState.value = _uiState.value.copy(
                workerName = saved,
                showWorkerNameDialog = true
            )
        }
    }

    fun onWorkerNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(workerName = name)
    }

    fun dismissWorkerNameDialog() {
        _uiState.value = _uiState.value.copy(showWorkerNameDialog = false)
    }

    /** Valide le nom, le sauvegarde, puis démarre le service PTI. */
    fun confirmWorkerNameAndActivate() {
        val name = _uiState.value.workerName.trim()
        if (name.isBlank()) {
            Log.w("MainViewModel", "Nom travailleur vide – activation refusée")
            return
        }
        viewModelScope.launch {
            app.settingsManager.setWorkerName(name)
            _uiState.value = _uiState.value.copy(
                workerName = name,
                showWorkerNameDialog = false
            )
            Log.i("MainViewModel", "Démarrage du service PTI (travailleur=$name)…")
            PtiForegroundService.start(getApplication())
        }
    }

    fun deactivatePti() {
        Log.i("MainViewModel", "Arrêt du service PTI…")
        PtiForegroundService.stop(getApplication())
    }

    fun requestSosConfirmation() {
        _uiState.value = _uiState.value.copy(showSosConfirmation = true)
    }

    fun dismissSosConfirmation() {
        _uiState.value = _uiState.value.copy(showSosConfirmation = false)
    }

    fun confirmSos() {
        _uiState.value = _uiState.value.copy(showSosConfirmation = false)
        PtiForegroundService.sendSos(getApplication())
    }

    fun cancelAlert() {
        // Appel direct sur le manager partagé (fiable, immédiat)
        // + notification au service si actif
        Log.i("MainViewModel", "Acquittement pré-alerte / alerte demandé")
        ptiManager.cancelCurrentAlert()
        try {
            PtiForegroundService.cancelAlert(getApplication())
        } catch (e: Exception) {
            Log.w("MainViewModel", "Service cancel non disponible", e)
        }
    }

    fun clearPermissionBlocked() {
        _uiState.value = _uiState.value.copy(permissionBlocked = false)
    }

    fun startOrientationPreview() {
        try {
            val om = ptiManager.getOrientationManager()
            om.startPreview()
            viewModelScope.launch {
                val settings = app.settingsManager.getSettings()
                _orientationThreshold.value = settings.orientationThresholdDegrees
                _isOrientationCalibrated.value =
                    settings.calibrationPitch != null && settings.calibrationRoll != null
                om.updateSettings(settings)
                launch {
                    om.currentPitch.collect { _orientationPitch.value = it }
                }
                launch {
                    om.currentRoll.collect { _orientationRoll.value = it }
                }
                launch {
                    om.currentDeviation.collect { _orientationDeviation.value = it }
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "startOrientationPreview", e)
        }
    }

    fun stopOrientationPreview() {
        try {
            ptiManager.getOrientationManager().stopPreview()
        } catch (e: Exception) {
            Log.w("MainViewModel", "stopOrientationPreview", e)
        }
    }

    fun calibrateOrientation(): Pair<Float, Float> {
        val result = ptiManager.calibrateOrientation()
        _isOrientationCalibrated.value = true
        return result
    }

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }
}
