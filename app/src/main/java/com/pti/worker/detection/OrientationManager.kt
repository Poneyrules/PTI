package com.pti.worker.detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.pti.worker.data.db.entities.SettingsEntity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class OrientationManager(
    context: Context,
    private val onOrientationAbnormal: () -> Unit
) : SensorEventListener {

    private val tag = "OrientationManager"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private var settings = SettingsEntity()
    private var isRunning = false
    private var abnormalStartTimestamp: Long? = null
    private var lastPitch = 0f
    private var lastRoll = 0f

    fun updateSettings(newSettings: SettingsEntity) { settings = newSettings }

    fun start() {
        if (isRunning || !settings.orientationDetectionEnabled) return
        val sensor = gravitySensor ?: accelerometer
        if (sensor == null) {
            Log.w(tag, "Aucun capteur d'orientation disponible")
            return
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        isRunning = true
        abnormalStartTimestamp = null
        Log.i(tag, "Surveillance d'orientation démarrée")
    }

    fun stop() {
        if (!isRunning) return
        sensorManager.unregisterListener(this)
        isRunning = false
        abnormalStartTimestamp = null
        Log.i(tag, "Surveillance d'orientation arrêtée")
    }

    fun isSensorAvailable(): Boolean = accelerometer != null || gravitySensor != null

    fun getCurrentOrientation(): Pair<Float, Float> = lastPitch to lastRoll

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isRunning) return
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        val pitch = Math.toDegrees(atan2(y.toDouble(), z.toDouble())).toFloat()
        val roll = Math.toDegrees(atan2(-x.toDouble(), sqrt((y*y + z*z).toDouble()))).toFloat()
        lastPitch = pitch
        lastRoll = roll

        val isAbnormal = abs(pitch) > settings.orientationThresholdDegrees ||
                abs(roll) > settings.orientationThresholdDegrees
        val now = System.currentTimeMillis()

        if (isAbnormal) {
            if (abnormalStartTimestamp == null) {
                abnormalStartTimestamp = now
            } else {
                val duration = now - (abnormalStartTimestamp ?: now)
                if (duration >= settings.orientationAbnormalDurationMs) {
                    Log.i(tag, "PERTE DE VERTICALITÉ CONFIRMÉE")
                    abnormalStartTimestamp = null
                    onOrientationAbnormal()
                }
            }
        } else {
            abnormalStartTimestamp = null
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
