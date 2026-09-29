package com.pti.worker.util

object Constants {

    const val NOTIFICATION_CHANNEL_ID = "pti_service_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Service PTI"
    const val ALERT_NOTIFICATION_CHANNEL_ID = "pti_alert_channel"
    const val ALERT_NOTIFICATION_CHANNEL_NAME = "Alertes PTI"
    const val NOTIFICATION_ID = 1001
    const val ALERT_NOTIFICATION_ID = 1002
    const val PRE_ALERT_NOTIFICATION_ID = 1003

    const val ACTION_START_PTI = "com.pti.worker.ACTION_START_PTI"
    const val ACTION_STOP_PTI = "com.pti.worker.ACTION_STOP_PTI"
    const val ACTION_SOS = "com.pti.worker.ACTION_SOS"
    const val ACTION_CANCEL_ALERT = "com.pti.worker.ACTION_CANCEL_ALERT"
    const val ACTION_ACKNOWLEDGE = "com.pti.worker.ACTION_ACKNOWLEDGE"

    object Defaults {
        const val IMMODILITY_THRESHOLD_MS = 2 * 60 * 1000L
        const val PRE_ALERT_DURATION_MS = 30 * 1000L
        const val ORIENTATION_ABNORMAL_DURATION_MS = 60 * 1000L
        const val FALL_ACCELERATION_THRESHOLD = 25.0f
        const val FALL_IMPACT_THRESHOLD = 40.0f
        const val FALL_POST_IMPACT_STILLNESS_MS = 2000L
        const val FALL_ORIENTATION_CHANGE_THRESHOLD = 45.0f
        const val LOCATION_INTERVAL_MS = 30_000L
        const val LOCATION_FASTEST_INTERVAL_MS = 10_000L
        const val LOCATION_MAX_WAIT_MS = 60_000L
        const val SOS_CANCEL_WINDOW_MS = 15_000L
        const val SENSOR_SAMPLE_PERIOD_US = 50_000
    }

    object Prefs {
        const val DATASTORE_NAME = "pti_settings"
        const val KEY_PTI_WAS_ACTIVE = "pti_was_active"
        const val KEY_RESTORE_AFTER_BOOT = "restore_after_boot"
    }

    const val DATABASE_NAME = "pti_database"
    const val DATABASE_VERSION = 1
}
