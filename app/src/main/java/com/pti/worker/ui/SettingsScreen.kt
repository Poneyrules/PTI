package com.pti.worker.ui

import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pti.worker.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onPickRingtone: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Détections", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            SwitchRow("Détection de chute", settings.fallDetectionEnabled) {
                viewModel.setFallEnabled(it)
            }
            SwitchRow("Détection d'immobilité", settings.immobilityEnabled) {
                viewModel.setImmobilityEnabled(it)
            }
            SwitchRow("Perte de verticalité", settings.orientationDetectionEnabled) {
                viewModel.setOrientationEnabled(it)
            }

            Spacer(Modifier.height(24.dp))
            Text("Temporisations", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Immobilité avant pré-alerte : ${settings.immobilityThresholdMs / 1000}s")
            Slider(
                value = settings.immobilityThresholdMs.toFloat(),
                onValueChange = {
                    viewModel.updateSettings(settings.copy(immobilityThresholdMs = it.toLong()))
                },
                valueRange = 30_000f..600_000f,
                steps = 18
            )
            Text("Durée pré-alerte : ${settings.preAlertDurationMs / 1000}s")
            Slider(
                value = settings.preAlertDurationMs.toFloat(),
                onValueChange = {
                    viewModel.updateSettings(settings.copy(preAlertDurationMs = it.toLong()))
                },
                valueRange = 10_000f..120_000f,
                steps = 10
            )

            Spacer(Modifier.height(24.dp))
            Text("Alarme", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            SwitchRow("Son", settings.soundEnabled) {
                viewModel.updateSettings(settings.copy(soundEnabled = it))
            }
            SwitchRow("Vibration", settings.vibrationEnabled) {
                viewModel.updateSettings(settings.copy(vibrationEnabled = it))
            }

            val ringtoneLabel = settings.alertRingtoneName
                ?: if (settings.alertRingtoneUri != null) "Sonnerie personnalisée"
                else "Sonnerie alarme (défaut)"

            OutlinedButton(
                onClick = onPickRingtone,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Sonnerie d'alerte")
                    Text(ringtoneLabel, style = MaterialTheme.typography.bodyMedium)
                }
            }

            TextButton(
                onClick = {
                    viewModel.updateSettings(
                        settings.copy(alertRingtoneUri = null, alertRingtoneName = null)
                    )
                },
                enabled = settings.alertRingtoneUri != null
            ) {
                Text("Réinitialiser la sonnerie")
            }

            Spacer(Modifier.height(24.dp))
            Text("Système", style = MaterialTheme.typography.titleLarge)
            SwitchRow("Restaurer après redémarrage", settings.restoreAfterBoot) {
                viewModel.updateSettings(settings.copy(restoreAfterBoot = it))
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
