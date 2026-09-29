package com.pti.worker.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PtiStateMachine {

    private val _state = MutableStateFlow(PtiState.DISABLED)
    val state: StateFlow<PtiState> = _state.asStateFlow()

    private val tag = "PtiStateMachine"

    fun transition(event: PtiEvent): Boolean {
        val current = _state.value
        val next = resolveNextState(current, event)
        return if (next != null) {
            Log.i(tag, "Transition: $current --[${event::class.simpleName}]--> $next")
            _state.value = next
            true
        } else {
            Log.w(tag, "Transition invalide: $current --[${event::class.simpleName}]--> ?")
            false
        }
    }

    fun forceState(newState: PtiState) {
        Log.w(tag, "Force state: ${_state.value} --> $newState")
        _state.value = newState
    }

    private fun resolveNextState(current: PtiState, event: PtiEvent): PtiState? {
        return when (current) {
            PtiState.DISABLED -> when (event) {
                is PtiEvent.Activate -> PtiState.ARMING
                is PtiEvent.ManualSos -> PtiState.ALERT
                else -> null
            }

            PtiState.ARMING -> when (event) {
                is PtiEvent.ArmingComplete -> PtiState.ACTIVE
                is PtiEvent.ArmingFailed -> PtiState.ERROR
                is PtiEvent.Deactivate -> PtiState.DISABLED
                is PtiEvent.Error -> PtiState.ERROR
                else -> null
            }
            PtiState.ACTIVE -> when (event) {
                is PtiEvent.Deactivate -> PtiState.DISABLED
                is PtiEvent.ManualSos,
                is PtiEvent.FallDetected,
                is PtiEvent.ImmobilityDetected,
                is PtiEvent.OrientationAbnormal -> PtiState.PRE_ALERT
                is PtiEvent.Error -> PtiState.ERROR
                else -> null
            }
            PtiState.PRE_ALERT -> when (event) {
                is PtiEvent.CancelAlert -> PtiState.ACTIVE
                is PtiEvent.PreAlertTimeout,
                is PtiEvent.ManualSos -> PtiState.ALERT
                is PtiEvent.Deactivate -> PtiState.DISABLED
                is PtiEvent.Error -> PtiState.ERROR
                else -> null
            }
            PtiState.ALERT -> when (event) {
                is PtiEvent.AcknowledgeAlert,
                is PtiEvent.CancelAlert -> PtiState.ACTIVE
                is PtiEvent.Deactivate -> PtiState.DISABLED
                is PtiEvent.Error -> PtiState.ERROR
                else -> null
            }
            PtiState.CANCELLED -> when (event) {
                is PtiEvent.CancelAlert -> PtiState.ACTIVE
                else -> PtiState.ACTIVE
            }
            PtiState.ERROR -> when (event) {
                is PtiEvent.RecoverFromError,
                is PtiEvent.Activate -> PtiState.ARMING
                is PtiEvent.Deactivate -> PtiState.DISABLED
                else -> null
            }
        }
    }

    fun isActiveOrHigher(): Boolean {
        return when (_state.value) {
            PtiState.ACTIVE, PtiState.PRE_ALERT, PtiState.ALERT, PtiState.ARMING -> true
            else -> false
        }
    }

    fun isInAlert(): Boolean = _state.value == PtiState.ALERT || _state.value == PtiState.PRE_ALERT
}
