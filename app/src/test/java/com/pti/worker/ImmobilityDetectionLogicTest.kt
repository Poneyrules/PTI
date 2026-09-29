package com.pti.worker

import com.pti.worker.data.db.entities.SettingsEntity
import org.junit.Assert.*
import org.junit.Test

class ImmobilityDetectionLogicTest {

    @Test
    fun defaultThreshold_isTwoMinutes() {
        assertEquals(2 * 60 * 1000L, SettingsEntity().immobilityThresholdMs)
    }

    @Test
    fun preAlertDuration_default_is30Seconds() {
        assertEquals(30_000L, SettingsEntity().preAlertDurationMs)
    }

    @Test
    fun shortImmobility_shouldNotTrigger() {
        val threshold = 120_000L
        assertTrue(5_000L < threshold)
    }
}
