package com.pti.worker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.pti.worker.data.db.PtiDatabase
import com.pti.worker.util.Constants

class PtiApplication : Application() {

    val database: PtiDatabase by lazy {
        PtiDatabase.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification permanente du service PTI"
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                "pti_alert_channel",
                "Alertes PTI",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertes SOS et pré-alertes"
                enableVibration(true)
                enableLights(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }
}
