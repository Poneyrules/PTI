package com.pti.worker.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pti.worker.BuildConfig
import com.pti.worker.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostic") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            DiagnosticLine("Version application", BuildConfig.VERSION_NAME)
            DiagnosticLine("Version Android", "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            DiagnosticLine("État PTI", uiState.ptiState.name)
            DiagnosticLine("GPS", if (uiState.isGpsAvailable) "Disponible" else "Indisponible")
            DiagnosticLine("Réseau", if (uiState.isNetworkAvailable) "OK" else "KO")
            DiagnosticLine(
                "Dernière position",
                uiState.lastLocation?.let { "%.5f, %.5f".format(it.latitude, it.longitude) } ?: "Aucune"
            )
            DiagnosticLine("Temps depuis activité", viewModel.formatDuration(uiState.timeSinceLastActivityMs))
            DiagnosticLine(
                "Permissions manquantes",
                uiState.missingPermissions.joinToString().ifEmpty { "Aucune" }
            )
        }
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
    HorizontalDivider()
}
