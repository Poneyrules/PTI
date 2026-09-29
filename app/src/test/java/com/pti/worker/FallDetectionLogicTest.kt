package com.pti.worker

import com.pti.worker.data.db.entities.SettingsEntity
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class FallDetectionLogicTest {

    @Test
    fun freeFall_threshold_isRespected() {
        val sens = 0.6f
        val freeFallThreshold = 5.5f - (sens * 2.0f)
        assertTrue(3.0f < freeFallThreshold)
        assertTrue(9.81f > freeFallThreshold)
    }

    @Test
    fun impact_threshold_scalesWithSensitivity() {
        val lowSens = SettingsEntity(fallSensitivity = 0.2f, fallImpactThreshold = 40f)
        val highSens = SettingsEntity(fallSensitivity = 1.0f, fallImpactThreshold = 40f)
        val lowThreshold = lowSens.fallImpactThreshold * (0.6f + 0.8f * lowSens.fallSensitivity)
        val highThreshold = highSens.fallImpactThreshold * (0.6f + 0.8f * highSens.fallSensitivity)
        assertTrue(highThreshold > lowThreshold)
    }

    @Test
    fun magnitude_calculation() {
        val mag = sqrt(3f * 3f + 4f * 4f + 0f)
        assertEquals(5f, mag, 0.001f)
    }
}
