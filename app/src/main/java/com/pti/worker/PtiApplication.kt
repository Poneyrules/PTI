package com.pti.worker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.pti.worker.communication.SmsCommunicationManager
import com.pti.worker.contact.ContactManager
import com.pti.worker.core.PtiManager
import com.pti.worker.data.db.PtiDatabase
import com.pti.worker.data.repository.ContactRepository
import com.pti.worker.data.repository.EventRepository
import com.pti.worker.location.PtiLocationManager
import com.pti.worker.settings.SettingsManager
import com.pti.worker.util.Constants
import com.pti.worker.util.NetworkMonitor

class PtiApplication : Application() {

    val database: PtiDatabase by lazy {
        PtiDatabase.getInstance(this)
    }

    val locationManager: PtiLocationManager by lazy {
        PtiLocationManager(this)
    }

    val networkMonitor: NetworkMonitor by lazy {
        NetworkMonitor(this)
    }

    val eventRepository: EventRepository by lazy {
        EventRepository(database.eventDao())
    }

    val contactRepository: ContactRepository by lazy {
        ContactRepository(database.contactDao())
    }

    val settingsManager: SettingsManager by lazy {
        SettingsManager(database.settingsDao())
    }

    val contactManager: ContactManager by lazy {
        ContactManager(contactRepository)
    }

    /** Instance unique partagée UI ↔ Service */
    val ptiManager: PtiManager by lazy {
        PtiManager(
            context = this,
            settingsManager = settingsManager,
            eventRepository = eventRepository,
            contactManager = contactManager,
            communicationManager = SmsCommunicationManager(this),
            locationManager = locationManager,
            networkMonitor = networkMonitor
        )
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
                Constants.ALERT_NOTIFICATION_CHANNEL_ID,
                Constants.ALERT_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertes SOS et pré-alertes (perte de verticalité, immobilité, chute)"
                enableVibration(true)
                enableLights(true)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }
}
