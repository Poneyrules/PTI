package com.pti.worker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pti.worker.ui.theme.PtiGreen
import com.pti.worker.ui.theme.PtiGrey
import com.pti.worker.ui.theme.PtiOrange
import com.pti.worker.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val pitch by viewModel.orientationPitch.collectAsState()
    val roll by viewModel.orientationRoll.collectAsState()
    val deviation by viewModel.orientationDeviation.collectAsState()
    val threshold by viewModel.orientationThreshold.collectAsState()
    val calibrated by viewModel.isOrientationCalibrated.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.startOrientationPreview()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopOrientationPreview() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibrage verticalité") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Placez le téléphone dans sa position de travail habituelle " +
                    "(ex. poche, brassard, support), puis calibrez.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = PtiGrey),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Orientation actuelle", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("Pitch : ${"%.1f".format(pitch)}°", color = Color.White, fontSize = 20.sp)
                    Text("Roll : ${"%.1f".format(roll)}°", color = Color.White, fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Écart vs calibrage : ${"%.1f".format(deviation)}°",
                        color = if (deviation > threshold) PtiOrange else PtiGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text("Seuil d'alerte : ${"%.0f".format(threshold)}°", color = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                if (calibrated) "✓ Verticalité calibrée"
                else "⚠ Non calibré (référence = 0° / 0°)",
                color = if (calibrated) PtiGreen else PtiOrange,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val (p, r) = viewModel.calibrateOrientation()
                    message = "Calibré : pitch=${"%.1f".format(p)}° roll=${"%.1f".format(r)}°"
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PtiGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CALIBRER CETTE POSITION", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = PtiGreen, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Après calibrage, une alerte est déclenchée si l'écart dépasse le seuil " +
                    "pendant la durée configurée (Paramètres).",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
