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

    init {
        observeState()
        checkPermissions()
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

    fun activatePti() {
        checkPermissions()
        if (!PermissionHelper.hasAllRequiredPermissions(getApplication())) {
            Log.w("MainViewModel", "Activation bloquée – permissions manquantes: ${_uiState.value.missingPermissions}")
            _uiState.value = _uiState.value.copy(permissionBlocked = true)
            return
        }
        Log.i("MainViewModel", "Démarrage du service PTI…")
        // Démarre le Foreground Service (qui appellera ptiManager.activate())
        PtiForegroundService.start(getApplication())
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
        PtiForegroundService.cancelAlert(getApplication())
    }

    fun clearPermissionBlocked() {
        _uiState.value = _uiState.value.copy(permissionBlocked = false)
    }

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }
}
