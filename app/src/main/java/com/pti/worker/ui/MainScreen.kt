package com.pti.worker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pti.worker.R
import com.pti.worker.core.PtiState
import com.pti.worker.ui.theme.*
import com.pti.worker.ui.viewmodel.MainUiState
import com.pti.worker.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateSettings: () -> Unit,
    onNavigateContacts: () -> Unit,
    onNavigateEvents: () -> Unit,
    onNavigateDiagnostic: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCoverageDialog by remember { mutableStateOf(false) }
    var selectedFloor by remember { mutableStateOf<Int?>(null) }
    var durationExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PTI Travailleur Isolé", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateContacts) {
                        Icon(Icons.Default.Contacts, contentDescription = "Contacts")
                    }
                    IconButton(onClick = onNavigateEvents) {
                        Icon(Icons.Default.List, contentDescription = "Journal")
                    }
                    IconButton(onClick = onNavigateDiagnostic) {
                        Icon(Icons.Default.Info, contentDescription = "Diagnostic")
                    }
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Paramètres")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PtiGrey,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatusCard(uiState)
                Spacer(modifier = Modifier.height(16.dp))
                InfoRow(uiState, viewModel)
                Spacer(modifier = Modifier.height(24.dp))
                MainActionButton(uiState, viewModel)
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { selectedFloor = null; showCoverageDialog = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("HORS COUVERTURE", fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(16.dp))

                // Pendant pré-alerte : gros bouton d'acquittement (sans désactiver le PTI)
                if (uiState.ptiState == PtiState.PRE_ALERT) {
                    Button(
                        onClick = { viewModel.cancelAlert() },
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PtiGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "JE SUIS OK — ANNULER",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Pré-alerte en cours. Appuyez pour confirmer que vous allez bien.",
                        textAlign = TextAlign.Center,
                        color = PtiOrange,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (uiState.ptiState == PtiState.ALERT) {
                    Button(
                        onClick = { viewModel.cancelAlert() },
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PtiOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "ACQUITTER L'ALERTE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // SOS toujours disponible (sauf pendant pré-alerte/alerte pour éviter confusion)
                SosButton(
                    enabled = uiState.ptiState != PtiState.PRE_ALERT && uiState.ptiState != PtiState.ALERT,
                    onClick = { viewModel.requestSosConfirmation() }
                )
            }

            // Logo PROCOMM-MMC en bas à droite
            Image(
                painter = painterResource(id = R.drawable.logo_procomm),
                contentDescription = "PROCOMM-MMC",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp)
                    .width(120.dp)
                    .height(80.dp)
            )
        }
    }

    if (uiState.showSosConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSosConfirmation() },
            title = { Text("Confirmer le SOS ?") },
            text = { Text("Cette action déclenchera une alerte immédiate vers vos contacts d'urgence.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmSos() },
                    colors = ButtonDefaults.buttonColors(containerColor = PtiRed)
                ) { Text("CONFIRMER") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSosConfirmation() }) { Text("Annuler") }
            }
        )
    }

    if (showCoverageDialog) {
        AlertDialog(
            onDismissRequest = { showCoverageDialog = false },
            title = { Text("Mode hors couverture") },
            text = {
                Column {
                    Text(if (selectedFloor == null) "Sélectionnez le niveau" else "Niveau sélectionné : $selectedFloor")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(-1, -2).forEach { floor ->
                            OutlinedButton(onClick = { selectedFloor = floor }) { Text("Niveau $floor") }
                        }
                    }
                    if (selectedFloor != null) {
                        Spacer(Modifier.height(12.dp))
                        Box {
                            OutlinedButton(onClick = { durationExpanded = true }) {
                                Text("Choisir la durée (0–60 min)")
                            }
                            DropdownMenu(
                                expanded = durationExpanded,
                                onDismissRequest = { durationExpanded = false },
                                modifier = Modifier.heightIn(max = 320.dp)
                            ) {
                                (0..60).forEach { minutes ->
                                    DropdownMenuItem(
                                        text = { Text("$minutes min") },
                                        onClick = {
                                            durationExpanded = false
                                            showCoverageDialog = false
                                            viewModel.sendCoverageStatus(selectedFloor!!, minutes)
                                        }
                                    )
                                }
                            }
                        }
                        Text("Le SMS sera envoyé au contact actif de priorité 1.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showCoverageDialog = false }) { Text("Annuler") } }
        )
    }

    uiState.coverageMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearCoverageMessage() },
            title = { Text("Hors couverture") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel.clearCoverageMessage() }) { Text("OK") } }
        )
    }

    // Saisie du nom du travailleur à l'activation du PTI
    if (uiState.showWorkerNameDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissWorkerNameDialog() },
            title = { Text("Identification") },
            text = {
                Column {
                    Text(
                        "Indiquez le nom du travailleur isolé. " +
                            "Il sera inclus dans les SMS d'alerte envoyés aux contacts d'urgence."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.workerName,
                        onValueChange = { viewModel.onWorkerNameChanged(it) },
                        label = { Text("Nom du travailleur") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmWorkerNameAndActivate() },
                    enabled = uiState.workerName.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = PtiGreen)
                ) { Text("ACTIVER LE PTI") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissWorkerNameDialog() }) { Text("Annuler") }
            }
        )
    }

    // Feedback si permissions manquantes
    if (uiState.permissionBlocked) {
        AlertDialog(
            onDismissRequest = { viewModel.clearPermissionBlocked() },
            title = { Text("Permissions requises") },
            text = {
                Text(
                    "Impossible d'activer le PTI. Accordez les permissions suivantes :\n\n" +
                        uiState.missingPermissions.joinToString("\n") {
                            "• " + it.substringAfterLast('.')
                        } +
                        "\n\nAllez dans Paramètres système → Applications → PTI → Permissions."
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.clearPermissionBlocked() }) {
                    Text("OK")
                }
            }
        )
    }
}


@Composable
private fun StatusCard(uiState: MainUiState) {
    val (text, color) = when (uiState.ptiState) {
        PtiState.DISABLED -> "PTI DÉSACTIVÉ" to PtiGrey
        PtiState.ARMING -> "ACTIVATION…" to PtiOrange
        PtiState.ACTIVE -> "PTI ACTIVÉ" to PtiGreen
        PtiState.PRE_ALERT -> "PRÉ-ALERTE" to PtiOrange
        PtiState.ALERT -> "ALERTE EN COURS" to PtiRed
        PtiState.ERROR -> "ERREUR" to PtiRedDark
        else -> uiState.ptiState.name to PtiGrey
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun InfoRow(uiState: MainUiState, viewModel: MainViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatusChip(if (uiState.isGpsAvailable) "GPS OK" else "GPS KO", uiState.isGpsAvailable)
            StatusChip(if (uiState.isNetworkAvailable) "RÉSEAU OK" else "RÉSEAU KO", uiState.isNetworkAvailable)
        }
        Spacer(modifier = Modifier.height(8.dp))
        val loc = uiState.lastLocation
        Text(
            text = if (loc != null) "Position : %.5f, %.5f (±%.0fm)".format(loc.latitude, loc.longitude, loc.accuracy)
            else "Position : inconnue",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        if (uiState.workerName.isNotBlank()) {
            Text(
                text = "Travailleur : ${uiState.workerName}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = "Dernière activité : ${viewModel.formatDuration(uiState.timeSinceLastActivityMs)}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatusChip(label: String, ok: Boolean) {
    Surface(color = if (ok) PtiGreen else PtiRed, shape = RoundedCornerShape(20.dp)) {
        Text(text = label, color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun MainActionButton(uiState: MainUiState, viewModel: MainViewModel) {
    val isActive = uiState.ptiState != PtiState.DISABLED && uiState.ptiState != PtiState.ERROR
    Button(
        onClick = { if (isActive) viewModel.deactivatePti() else viewModel.activatePti() },
        modifier = Modifier.fillMaxWidth().height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (isActive) PtiGrey else PtiGreen),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = if (isActive) "DÉSACTIVER LE PTI" else "ACTIVER LE PTI",
            fontSize = 20.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SosButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(180.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PtiRed, disabledContainerColor = PtiDisabled),
        shape = RoundedCornerShape(90.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Text(text = "SOS", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}
