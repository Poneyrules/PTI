package com.pti.worker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pti.worker.PtiApplication
import com.pti.worker.service.PtiForegroundService
import com.pti.worker.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        Log.i("BootReceiver", "Boot détecté")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = (context.applicationContext as PtiApplication).database
                val settings = SettingsManager(db.settingsDao()).getSettings()
                if (settings.restoreAfterBoot) {
                    PtiForegroundService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
