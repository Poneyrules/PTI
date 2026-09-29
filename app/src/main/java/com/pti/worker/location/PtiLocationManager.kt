package com.pti.worker.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pti.worker.util.Constants
import com.pti.worker.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class PtiLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double? = null,
    val speed: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class PtiLocationManager(private val context: Context) {

    private val tag = "PtiLocationManager"
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _lastLocation = MutableStateFlow<PtiLocation?>(null)
    val lastLocation: StateFlow<PtiLocation?> = _lastLocation.asStateFlow()

    private val _isGpsAvailable = MutableStateFlow(false)
    val isGpsAvailable: StateFlow<Boolean> = _isGpsAvailable.asStateFlow()

    private var locationCallback: LocationCallback? = null
    private var isTracking = false

    @SuppressLint("MissingPermission")
    fun startTracking(intervalMs: Long = Constants.Defaults.LOCATION_INTERVAL_MS) {
        if (!PermissionHelper.hasLocationPermission(context)) {
            Log.w(tag, "Permission localisation manquante")
            _isGpsAvailable.value = false
            return
        }
        if (isTracking) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(Constants.Defaults.LOCATION_FASTEST_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val ptiLoc = loc.toPtiLocation()
                    _lastLocation.value = ptiLoc
                    _isGpsAvailable.value = true
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
            isTracking = true
            Log.i(tag, "Tracking démarré")
        } catch (e: SecurityException) {
            Log.e(tag, "SecurityException", e)
            _isGpsAvailable.value = false
        }
    }

    fun stopTracking() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
        isTracking = false
        Log.i(tag, "Tracking arrêté")
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): PtiLocation? {
        if (!PermissionHelper.hasLocationPermission(context)) return _lastLocation.value
        return try {
            val location = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            location?.toPtiLocation()?.also {
                _lastLocation.value = it
                _isGpsAvailable.value = true
            } ?: _lastLocation.value
        } catch (e: Exception) {
            Log.e(tag, "getCurrentLocation failed", e)
            _lastLocation.value
        }
    }

    private fun Location.toPtiLocation() = PtiLocation(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        altitude = if (hasAltitude()) altitude else null,
        speed = if (hasSpeed()) speed else null,
        timestamp = time
    )
}
