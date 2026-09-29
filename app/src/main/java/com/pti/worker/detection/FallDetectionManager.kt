package com.pti.worker.detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.pti.worker.data.db.entities.SettingsEntity
import com.pti.worker.util.Constants
import kotlin.math.sqrt

class FallDetectionManager(
    context: Context,
    private val onFallDetected: () -> Unit
) : SensorEventListener {

    private val tag = "FallDetection"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val linearAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private var settings = SettingsEntity()
    private var isRunning = false

    private enum class Phase { IDLE, FREE_FALL, IMPACT, POST_IMPACT }
    private var phase = Phase.IDLE
    private var freeFallStartMs = 0L
    private var impactTimeMs = 0L
    private var postImpactStillStartMs = 0L
    private val gravity = FloatArray(3)
    private var gravityReady = false
    private var orientationChangeDetected = false
    private var lastFallTriggerMs = 0L
    private val minTimeBetweenFallsMs = 15_000L

    fun updateSettings(newSettings: SettingsEntity) { settings = newSettings }

    fun start() {
        if (isRunning || !settings.fallDetectionEnabled) return
        if (accelerometer == null) {
            Log.w(tag, "Accéléromètre absent")
            return
        }
        val samplingUs = Constants.Defaults.SENSOR_SAMPLE_PERIOD_US
        sensorManager.registerListener(this, accelerometer, samplingUs)
        linearAccel?.let { sensorManager.registerListener(this, it, samplingUs) }
        gravitySensor?.let { sensorManager.registerListener(this, it, samplingUs) }
        gyroscope?.let { sensorManager.registerListener(this, it, samplingUs) }
        isRunning = true
        resetAlgorithm()
        Log.i(tag, "Détection de chute démarrée")
    }

    fun stop() {
        if (!isRunning) return
        sensorManager.unregisterListener(this)
        isRunning = false
        resetAlgorithm()
        Log.i(tag, "Détection de chute arrêtée")
    }

    fun isSensorAvailable(): Boolean = accelerometer != null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isRunning) return
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                gravity[0] = event.values[0]; gravity[1] = event.values[1]; gravity[2] = event.values[2]
                gravityReady = true
            }
            Sensor.TYPE_GYROSCOPE -> {
                val mag = sqrt(event.values[0]*event.values[0] + event.values[1]*event.values[1] + event.values[2]*event.values[2])
                if (mag > 3.5f) orientationChangeDetected = true
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val mag = sqrt(event.values[0]*event.values[0] + event.values[1]*event.values[1] + event.values[2]*event.values[2])
                advanceAlgorithm(mag, null)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (linearAccel == null) processAccelerometer(event)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun processAccelerometer(event: SensorEvent) {
        val ax = event.values[0]; val ay = event.values[1]; val az = event.values[2]
        val totalMag = sqrt(ax*ax + ay*ay + az*az)
        if (!gravityReady) {
            gravity[0] = ax; gravity[1] = ay; gravity[2] = az; gravityReady = true
        } else {
            val alpha = 0.9f
            gravity[0] = alpha*gravity[0] + (1-alpha)*ax
            gravity[1] = alpha*gravity[1] + (1-alpha)*ay
            gravity[2] = alpha*gravity[2] + (1-alpha)*az
        }
        val lx = ax - gravity[0]; val ly = ay - gravity[1]; val lz = az - gravity[2]
        val linearMag = sqrt(lx*lx + ly*ly + lz*lz)
        advanceAlgorithm(linearMag, totalMag)
    }

    private fun advanceAlgorithm(linearMagnitude: Float, totalMagnitude: Float?) {
        val now = System.currentTimeMillis()
        val sens = settings.fallSensitivity.coerceIn(0.2f, 1.0f)
        val freeFallThreshold = 5.5f - (sens * 2.0f)
        val impactThreshold = settings.fallImpactThreshold * (0.6f + 0.8f * sens)
        val stillThreshold = 2.8f - (sens * 1.0f)

        when (phase) {
            Phase.IDLE -> {
                val mag = totalMagnitude ?: (linearMagnitude + 9.81f)
                if (mag < freeFallThreshold) {
                    phase = Phase.FREE_FALL
                    freeFallStartMs = now
                    orientationChangeDetected = false
                }
            }
            Phase.FREE_FALL -> {
                if (now - freeFallStartMs > 1500) { resetAlgorithm(); return }
                if (linearMagnitude > impactThreshold) {
                    phase = Phase.IMPACT
                    impactTimeMs = now
                    postImpactStillStartMs = 0L
                }
            }
            Phase.IMPACT -> {
                if (now - impactTimeMs > 300) {
                    phase = Phase.POST_IMPACT
                    postImpactStillStartMs = now
                }
            }
            Phase.POST_IMPACT -> {
                if (linearMagnitude > stillThreshold) { resetAlgorithm(); return }
                if (now - postImpactStillStartMs >= settings.fallPostImpactStillnessMs) {
                    if (now - lastFallTriggerMs > minTimeBetweenFallsMs) {
                        lastFallTriggerMs = now
                        resetAlgorithm()
                        Log.i(tag, "CHUTE CONFIRMÉE")
                        onFallDetected()
                    } else {
                        resetAlgorithm()
                    }
                }
            }
        }
    }

    private fun resetAlgorithm() {
        phase = Phase.IDLE
        freeFallStartMs = 0L
        impactTimeMs = 0L
        postImpactStillStartMs = 0L
        orientationChangeDetected = false
    }
}
