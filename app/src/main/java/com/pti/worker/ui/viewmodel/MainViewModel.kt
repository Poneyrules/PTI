package com.pti.worker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pti.worker.PtiApplication
import com.pti.worker.communication.SmsCommunicationManager
import com.pti.worker.contact.ContactManager
import com.pti.worker.core.PtiManager
import com.pti.worker.core.PtiState
import com.pti.worker.data.repository.ContactRepository
import com.pti.worker.data.repository.EventRepository
import com.pti.worker.location.PtiLocation
import com.pti.worker.location.PtiLocationManager
import com.pti.worker.service.PtiForegroundService
import com.pti.worker.settings.SettingsManager
import com.pti.worker.util.NetworkMonitor
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
    val missingPermissions: List<String> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PtiApplication
    private val db = app.database
    private val eventRepo = EventRepository(db.eventDao())
    private val contactRepo = ContactRepository(db.contactDao())
    private val settingsManager = SettingsManager(db.settingsDao())
    private val contactManager = ContactManager(contactRepo)
    private val locationManager = PtiLocationManager(application)
    private val networkMonitor = NetworkMonitor(application)
    private val communicationManager = SmsCommunicationManager(application)

    private val ptiManager = PtiManager(
        context = application,
        settingsManager = settingsManager,
        eventRepository = eventRepo,
        contactManager = contactManager,
        communicationManager = communicationManager,
        locationManager = locationManager,
        networkMonitor = networkMonitor
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val events = eventRepo.getRecentEvents(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        observeState()
        checkPermissions()
    }

    private fun observeState() {
        viewModelScope.launch {
            ptiManager.stateMachine.state.collect { state ->
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
        _uiState.value = _uiState.value.copy(missingPermissions = missing)
    }

    fun activatePti() {
        if (!PermissionHelper.hasAllRequiredPermissions(getApplication())) {
            checkPermissions()
            return
        }
        PtiForegroundService.start(getApplication())
    }

    fun deactivatePti() {
        PtiForegroundService.stop(getApplication())
        ptiManager.deactivate()
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

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }
}
