package com.pti.worker

import com.pti.worker.core.PtiEvent
import com.pti.worker.core.PtiState
import com.pti.worker.core.PtiStateMachine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PtiStateMachineTest {

    private lateinit var sm: PtiStateMachine

    @Before
    fun setup() { sm = PtiStateMachine() }

    @Test fun initialState_isDisabled() = assertEquals(PtiState.DISABLED, sm.state.value)

    @Test fun activate_fromDisabled_goesToArming() {
        assertTrue(sm.transition(PtiEvent.Activate))
        assertEquals(PtiState.ARMING, sm.state.value)
    }

    @Test fun armingComplete_goesToActive() {
        sm.transition(PtiEvent.Activate)
        assertTrue(sm.transition(PtiEvent.ArmingComplete))
        assertEquals(PtiState.ACTIVE, sm.state.value)
    }

    @Test fun fallDetected_fromActive_goesToPreAlert() {
        sm.forceState(PtiState.ACTIVE)
        assertTrue(sm.transition(PtiEvent.FallDetected))
        assertEquals(PtiState.PRE_ALERT, sm.state.value)
    }

    @Test fun cancelAlert_fromPreAlert_returnsToActive() {
        sm.forceState(PtiState.PRE_ALERT)
        assertTrue(sm.transition(PtiEvent.CancelAlert))
        assertEquals(PtiState.ACTIVE, sm.state.value)
    }

    @Test fun preAlertTimeout_goesToAlert() {
        sm.forceState(PtiState.PRE_ALERT)
        assertTrue(sm.transition(PtiEvent.PreAlertTimeout))
        assertEquals(PtiState.ALERT, sm.state.value)
    }

    @Test fun acknowledge_fromAlert_returnsToActive() {
        sm.forceState(PtiState.ALERT)
        assertTrue(sm.transition(PtiEvent.AcknowledgeAlert))
        assertEquals(PtiState.ACTIVE, sm.state.value)
    }

    @Test fun invalidTransition_isRejected() {
        sm.forceState(PtiState.DISABLED)
        assertFalse(sm.transition(PtiEvent.FallDetected))
        assertEquals(PtiState.DISABLED, sm.state.value)
    }

    @Test fun scenario_manualSos() {
        sm.transition(PtiEvent.Activate)
        sm.transition(PtiEvent.ArmingComplete)
        sm.transition(PtiEvent.ManualSos)
        assertEquals(PtiState.PRE_ALERT, sm.state.value)
        sm.transition(PtiEvent.PreAlertTimeout)
        assertEquals(PtiState.ALERT, sm.state.value)
        sm.transition(PtiEvent.AcknowledgeAlert)
        assertEquals(PtiState.ACTIVE, sm.state.value)
    }
}
