package com.pti.worker.core

sealed class PtiEvent {
    object Activate : PtiEvent()
    object Deactivate : PtiEvent()
    object ArmingComplete : PtiEvent()
    object ArmingFailed : PtiEvent()
    object ManualSos : PtiEvent()
    object FallDetected : PtiEvent()
    object ImmobilityDetected : PtiEvent()
    object OrientationAbnormal : PtiEvent()
    object PreAlertTimeout : PtiEvent()
    object CancelAlert : PtiEvent()
    object AcknowledgeAlert : PtiEvent()
    data class Error(val reason: String) : PtiEvent()
    object RecoverFromError : PtiEvent()
}
