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
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPreAlertOrAlert = state == PtiState.PRE_ALERT || state == PtiState.ALERT
        val channelId = if (isPreAlertOrAlert) {
            Constants.ALERT_NOTIFICATION_CHANNEL_ID
        } else {
            Constants.NOTIFICATION_CHANNEL_ID
        }

        val title = when (state) {
            PtiState.ACTIVE -> "PTI actif"
            PtiState.PRE_ALERT -> "Pré-alerte en cours"
            PtiState.ALERT -> "ALERTE PTI"
            PtiState.ARMING -> "Activation PTI…"
            else -> "PTI"
        }
        val text = when (state) {
            PtiState.ACTIVE -> "Surveillance en cours"
            PtiState.PRE_ALERT -> "Appuyez sur JE SUIS OK pour acquitter"
            PtiState.ALERT -> "Alerte déclenchée — appuyez pour acquitter"
            else -> "Service de protection"
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(state == PtiState.ACTIVE || state == PtiState.ARMING)
            .setCategory(
                if (isPreAlertOrAlert) NotificationCompat.CATEGORY_ALARM
                else NotificationCompat.CATEGORY_SERVICE
            )
            .setPriority(
                if (isPreAlertOrAlert) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_LOW
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Bouton d'acquittement visible dès le déclenchement de la pré-alerte
        // (perte de verticalité, immobilité, chute) et pendant l'alerte
        if (isPreAlertOrAlert) {
            val cancelIntent = PendingIntent.getService(
                this,
                1,
                Intent(this, PtiForegroundService::class.java).apply {
                    action = Constants.ACTION_CANCEL_ALERT
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val actionLabel = if (state == PtiState.PRE_ALERT) {
                "JE SUIS OK"
            } else {
                "ACQUITTER"
            }
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                actionLabel,
                cancelIntent
            )
            // Heads-up + alarme pour que le bouton soit immédiatement accessible
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.setFullScreenIntent(openAppIntent, true)
            }
        }

        return builder.build()
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
