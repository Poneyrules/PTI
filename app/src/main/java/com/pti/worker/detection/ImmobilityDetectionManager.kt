package com.pti.worker.detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.pti.worker.data.db.entities.SettingsEntity
import kotlin.math.abs
import kotlin.math.sqrt

class ImmobilityDetectionManager(
    context: Context,
    private val onImmobilityDetected: () -> Unit
) : SensorEventListener {

    private val tag = "ImmobilityDetection"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var settings = SettingsEntity()
    private var isRunning = false
    private var lastMovementTimestamp = System.currentTimeMillis()
    private var lastMagnitude = 9.81f
    private val movementThresholdBase = 1.2f

    fun updateSettings(newSettings: SettingsEntity) { settings = newSettings }

    fun start() {
        if (isRunning || !settings.immobilityEnabled) return
        if (accelerometer == null) {
            Log.w(tag, "Accéléromètre non disponible")
            return
        }
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        isRunning = true
        lastMovementTimestamp = System.currentTimeMillis()
        Log.i(tag, "Surveillance d'immobilité démarrée")
    }

    fun stop() {
        if (!isRunning) return
        sensorManager.unregisterListener(this)
        isRunning = false
        Log.i(tag, "Surveillance d'immobilité arrêtée")
    }

    fun notifyUserActivity() {
        lastMovementTimestamp = System.currentTimeMillis()
    }

    fun getTimeSinceLastMovement(): Long = System.currentTimeMillis() - lastMovementTimestamp

    fun isSensorAvailable(): Boolean = accelerometer != null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER || !isRunning) return
        val magnitude = sqrt(event.values[0]*event.values[0] + event.values[1]*event.values[1] + event.values[2]*event.values[2])
        val delta = abs(magnitude - lastMagnitude)
        val sensitivityFactor = 0.5f + settings.immobilitySensitivity
        val threshold = movementThresholdBase / sensitivityFactor

        if (delta > threshold) {
            lastMovementTimestamp = System.currentTimeMillis()
        } else {
            val immobileDuration = System.currentTimeMillis() - lastMovementTimestamp
            if (immobileDuration >= settings.immobilityThresholdMs) {
                Log.i(tag, "IMMOBILITÉ DÉTECTÉE (${immobileDuration}ms)")
                lastMovementTimestamp = System.currentTimeMillis()
                onImmobilityDetected()
            }
        }
        lastMagnitude = magnitude
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
