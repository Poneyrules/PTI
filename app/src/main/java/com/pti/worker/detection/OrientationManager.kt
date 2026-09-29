package com.pti.worker.detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.pti.worker.data.db.entities.SettingsEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Détection de perte de verticalité par rapport à une position calibrée.
 *
 * - baselinePitch / baselineRoll = orientation "normale" (calibrage)
 * - Seuil = écart angulaire max acceptable (réglable)
 * - Déclenche si l'écart dépasse le seuil pendant orientationAbnormalDurationMs
 */
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

    private var baselinePitch = 0f
    private var baselineRoll = 0f
    private var hasCalibration = false

    private var abnormalStartTimestamp: Long? = null
    private var lastTriggerMs = 0L
    private val minTimeBetweenTriggersMs = 30_000L

    private val _currentPitch = MutableStateFlow(0f)
    val currentPitch: StateFlow<Float> = _currentPitch.asStateFlow()

    private val _currentRoll = MutableStateFlow(0f)
    val currentRoll: StateFlow<Float> = _currentRoll.asStateFlow()

    private val _currentDeviation = MutableStateFlow(0f)
    val currentDeviation: StateFlow<Float> = _currentDeviation.asStateFlow()

    fun updateSettings(newSettings: SettingsEntity) {
        settings = newSettings
        // Restaure le calibrage depuis les settings si présent
        if (newSettings.calibrationPitch != null && newSettings.calibrationRoll != null) {
            baselinePitch = newSettings.calibrationPitch
            baselineRoll = newSettings.calibrationRoll
            hasCalibration = true
            Log.i(tag, "Calibrage restauré: pitch=$baselinePitch roll=$baselineRoll")
        }
    }

    fun start() {
        if (isRunning || !settings.orientationDetectionEnabled) return
        val sensor = gravitySensor ?: accelerometer
        if (sensor == null) {
            Log.w(tag, "Aucun capteur d'orientation disponible")
            return
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        isRunning = true
        abnormalStartTimestamp = null
        Log.i(tag, "Surveillance orientation démarrée (seuil=${settings.orientationThresholdDegrees}°)")
    }

    fun stop() {
        if (!isRunning) return
        sensorManager.unregisterListener(this)
        isRunning = false
        abnormalStartTimestamp = null
        Log.i(tag, "Surveillance orientation arrêtée")
    }

    /**
     * Lecture capteur sans détection d'alerte (pour écran de calibrage).
     */
    fun startPreview() {
        val sensor = gravitySensor ?: accelerometer ?: return
        if (isRunning) return // déjà en mode surveillance
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        Log.i(tag, "Preview orientation démarré")
    }

    fun stopPreview() {
        if (isRunning) return // ne pas couper la surveillance active
        sensorManager.unregisterListener(this)
        Log.i(tag, "Preview orientation arrêté")
    }

    fun isSensorAvailable(): Boolean = accelerometer != null || gravitySensor != null

    /** Calibre la position actuelle comme verticale de référence */
    fun calibrateNow(): Pair<Float, Float> {
        baselinePitch = _currentPitch.value
        baselineRoll = _currentRoll.value
        hasCalibration = true
        abnormalStartTimestamp = null
        Log.i(tag, "Calibrage effectué: pitch=$baselinePitch roll=$baselineRoll")
        return baselinePitch to baselineRoll
    }

    fun getCalibration(): Pair<Float, Float>? =
        if (hasCalibration) baselinePitch to baselineRoll else null

    fun getCurrentOrientation(): Pair<Float, Float> =
        _currentPitch.value to _currentRoll.value

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Pitch / Roll standards
        val pitch = Math.toDegrees(atan2(y.toDouble(), z.toDouble())).toFloat()
        val roll = Math.toDegrees(
            atan2(-x.toDouble(), sqrt((y * y + z * z).toDouble()))
        ).toFloat()

        _currentPitch.value = pitch
        _currentRoll.value = roll

        if (!isRunning || !settings.orientationDetectionEnabled) return

        // Écart par rapport au calibrage (ou 0,0 si pas calibré = téléphone à plat / vertical selon usage)
        val refPitch = if (hasCalibration) baselinePitch else 0f
        val refRoll = if (hasCalibration) baselineRoll else 0f

        val devPitch = abs(normalizeAngle(pitch - refPitch))
        val devRoll = abs(normalizeAngle(roll - refRoll))
        val deviation = maxOf(devPitch, devRoll)
        _currentDeviation.value = deviation

        val threshold = settings.orientationThresholdDegrees
        val isAbnormal = deviation > threshold
        val now = System.currentTimeMillis()

        if (isAbnormal) {
            if (abnormalStartTimestamp == null) {
                abnormalStartTimestamp = now
                Log.d(tag, "Orientation anormale (écart=${"%.1f".format(deviation)}° > ${threshold}°)")
            } else {
                val duration = now - (abnormalStartTimestamp ?: now)
                if (duration >= settings.orientationAbnormalDurationMs) {
                    if (now - lastTriggerMs > minTimeBetweenTriggersMs) {
                        lastTriggerMs = now
                        abnormalStartTimestamp = null
                        Log.i(tag, "PERTE DE VERTICALITÉ (écart=${"%.1f".format(deviation)}° pendant ${duration}ms)")
                        onOrientationAbnormal()
                    }
                }
            }
        } else {
            abnormalStartTimestamp = null
        }
    }

    /** Normalise un angle dans [-180, 180] */
    private fun normalizeAngle(angle: Float): Float {
        var a = angle
        while (a > 180f) a -= 360f
        while (a < -180f) a += 360f
        return a
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
