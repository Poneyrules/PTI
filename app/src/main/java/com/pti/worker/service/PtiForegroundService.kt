package com.pti.worker.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pti.worker.MainActivity
import com.pti.worker.PtiApplication
import com.pti.worker.core.PtiManager
import com.pti.worker.core.PtiState
import com.pti.worker.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PtiForegroundService : Service() {

    private val tag = "PtiForegroundService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var ptiManager: PtiManager

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "Service créé")
        // Utilise la MÊME instance que l'UI
        ptiManager = (application as PtiApplication).ptiManager

        serviceScope.launch {
            ptiManager.stateMachine.state.collectLatest { state ->
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(tag, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            Constants.ACTION_START_PTI -> {
                startAsForeground()
                ptiManager.activate()
            }
            Constants.ACTION_STOP_PTI -> {
                ptiManager.deactivate()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            Constants.ACTION_SOS -> ptiManager.triggerManualSos()
            Constants.ACTION_CANCEL_ALERT -> ptiManager.cancelCurrentAlert()
            Constants.ACTION_ACKNOWLEDGE -> ptiManager.acknowledgeAlert()
            else -> {
                // Redémarrage système (START_STICKY) sans action → on reste en foreground si actif
                if (ptiManager.stateMachine.isActiveOrHigher()) {
                    startAsForeground()
                }
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildNotification(ptiManager.stateMachine.state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }
        Log.i(tag, "Foreground démarré")
    }

    private fun updateNotification(state: PtiState) {
        val notification = buildNotification(state)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(Constants.NOTIFICATION_ID, notification)
    }

    private fun buildNotification(state: PtiState): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = when (state) {
            PtiState.ACTIVE -> "PTI actif"
            PtiState.PRE_ALERT -> "Pré-alerte en cours"
            PtiState.ALERT -> "ALERTE PTI"
            PtiState.ARMING -> "Activation PTI…"
            else -> "PTI"
        }
        val text = when (state) {
            PtiState.ACTIVE -> "Surveillance en cours"
            PtiState.PRE_ALERT -> "Appuyez pour annuler"
            PtiState.ALERT -> "Alerte déclenchée"
            else -> "Service de protection"
        }
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(
                if (state == PtiState.ALERT || state == PtiState.PRE_ALERT)
                    NotificationCompat.PRIORITY_HIGH
                else
                    NotificationCompat.PRIORITY_LOW
            )
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(tag, "Service détruit")
        // Ne pas release le manager partagé (il vit dans Application)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, PtiForegroundService::class.java).apply {
                action = Constants.ACTION_START_PTI
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, PtiForegroundService::class.java).apply {
                    action = Constants.ACTION_STOP_PTI
                }
            )
        }

        fun sendSos(context: Context) {
            context.startService(
                Intent(context, PtiForegroundService::class.java).apply {
                    action = Constants.ACTION_SOS
                }
            )
        }

        fun cancelAlert(context: Context) {
            context.startService(
                Intent(context, PtiForegroundService::class.java).apply {
                    action = Constants.ACTION_CANCEL_ALERT
                }
            )
        }
    }
}
