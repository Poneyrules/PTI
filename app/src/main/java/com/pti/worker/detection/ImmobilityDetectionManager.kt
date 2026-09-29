package com.pti.worker.detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.pti.worker.data.db.entities.SettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Surveillance d'immobilité robuste.
 * - Filtre le bruit (accélération linéaire + moyenne mobile)
 * - Timer dédié 1s pour le timeout (indépendant des events capteur)
 */
class ImmobilityDetectionManager(
    context: Context,
    private val onImmobilityDetected: () -> Unit
) : SensorEventListener {

    private val tag = "ImmobilityDetection"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var settings = SettingsEntity()
    private var isRunning = false

    @Volatile
    private var lastMovementTimestamp = System.currentTimeMillis()

    @Volatile
    private var cooldownUntil = 0L

    private var filteredMagnitude = 0f
    private val gravityEstimate = FloatArray(3)
    private var gravityReady = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchdogJob: Job? = null

    fun updateSettings(newSettings: SettingsEntity) {
        settings = newSettings
        Log.i(tag, "Settings: threshold=${settings.immobilityThresholdMs}ms, enabled=${settings.immobilityEnabled}")
    }

    fun start() {
        if (isRunning) return
        if (!settings.immobilityEnabled) {
            Log.w(tag, "Immobilité désactivée dans les paramètres")
            return
        }
        if (accelerometer == null) {
            Log.w(tag, "Accéléromètre non disponible")
            return
        }

        lastMovementTimestamp = System.currentTimeMillis()
        cooldownUntil = 0L
        gravityReady = false
        filteredMagnitude = 0f

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        isRunning = true

        watchdogJob = scope.launch {
            while (isActive) {
                delay(1000)
                checkImmobilityTimeout()
            }
        }

        Log.i(tag, "Surveillance d'immobilité démarrée (seuil=${settings.immobilityThresholdMs / 1000}s)")
    }

    fun stop() {
        if (!isRunning) return
        sensorManager.unregisterListener(this)
        watchdogJob?.cancel()
        watchdogJob = null
        isRunning = false
        Log.i(tag, "Surveillance d'immobilité arrêtée")
    }

    fun notifyUserActivity() {
        lastMovementTimestamp = System.currentTimeMillis()
        Log.d(tag, "Activité utilisateur – timer réinitialisé")
    }

    fun getTimeSinceLastMovement(): Long = System.currentTimeMillis() - lastMovementTimestamp

    fun isSensorAvailable(): Boolean = accelerometer != null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER || !isRunning) return

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        if (!gravityReady) {
            gravityEstimate[0] = ax
            gravityEstimate[1] = ay
            gravityEstimate[2] = az
            gravityReady = true
        } else {
            val alpha = 0.9f
            gravityEstimate[0] = alpha * gravityEstimate[0] + (1 - alpha) * ax
            gravityEstimate[1] = alpha * gravityEstimate[1] + (1 - alpha) * ay
            gravityEstimate[2] = alpha * gravityEstimate[2] + (1 - alpha) * az
        }

        val lx = ax - gravityEstimate[0]
        val ly = ay - gravityEstimate[1]
        val lz = az - gravityEstimate[2]
        val linearMag = sqrt(lx * lx + ly * ly + lz * lz)

        filteredMagnitude = 0.7f * filteredMagnitude + 0.3f * linearMag

        val baseThreshold = 0.8f
        val threshold = baseThreshold * (1.4f - settings.immobilitySensitivity.coerceIn(0.1f, 1.0f))

        if (filteredMagnitude > threshold) {
            lastMovementTimestamp = System.currentTimeMillis()
        }
    }

    private fun checkImmobilityTimeout() {
        if (!isRunning || !settings.immobilityEnabled) return
        val now = System.currentTimeMillis()
        if (now < cooldownUntil) return

        val immobileDuration = now - lastMovementTimestamp
        if (immobileDuration >= settings.immobilityThresholdMs) {
            Log.i(tag, "IMMOBILITÉ DÉTECTÉE après ${immobileDuration / 1000}s")
            cooldownUntil = now + settings.immobilityThresholdMs
            lastMovementTimestamp = now
            onImmobilityDetected()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
