package com.pti.worker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pti.worker.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SipSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val sipState by viewModel.sipConnectionState.collectAsState()
    val importStatus by viewModel.sipImportStatus.collectAsState()
    var enabled by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var contactsUrl by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val contactsUrlBringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(settings) {
        enabled = settings.sipEnabled
        username = settings.sipUsername
        password = settings.sipPassword
        domain = settings.sipDomain
        server = settings.sipServer
        contactsUrl = settings.sipContactsUrl
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Communication SIP") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
            }
        )
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Compte SIP Linphone", style = MaterialTheme.typography.titleLarge)
            Text("Une connexion Internet (Wi-Fi ou données mobiles) reste nécessaire. Le SIP ne fonctionne pas sans réseau IP.")
            val sipStateColor = when (sipState.status) {
                com.pti.worker.communication.SipConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                com.pti.worker.communication.SipConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.tertiary
                com.pti.worker.communication.SipConnectionStatus.FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "●  ${sipState.message}",
                    color = sipStateColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(14.dp)
                )
            }
            Text("L'état passe à Connecté lorsque le serveur a accepté l'enregistrement du compte.", style = MaterialTheme.typography.bodySmall)
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Utiliser SIP pour les appels d'alerte", modifier = Modifier.weight(1f).padding(top = 12.dp))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            OutlinedTextField(username, { username = it }, label = { Text("Identifiant SIP") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mot de passe SIP") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Masquer le mot de passe" else "Afficher le mot de passe"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(domain, { domain = it }, label = { Text("Domaine SIP (ex. sip.exemple.fr)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(server, { server = it }, label = { Text("Serveur / proxy SIP (ex. sip:sip.exemple.fr:5060)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(8.dp))
            Text("Répertoire distant", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = contactsUrl,
                onValueChange = { contactsUrl = it },
                label = { Text("Adresse du JSON des contacts") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(contactsUrlBringIntoView)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            scope.launch {
                                delay(250)
                                contactsUrlBringIntoView.bringIntoView()
                            }
                        }
                    }
            )
            Text("Formats acceptés : une liste JSON ou { \"contacts\": [...] }. Champs par contact : name et sipUri, sip, uri, phone ou phoneNumber.", style = MaterialTheme.typography.bodySmall)
            Text("Une adresse HTTP locale est acceptée pour un répertoire sur le réseau local ; privilégiez HTTPS hors réseau de confiance.", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { viewModel.importSipContacts(contactsUrl) },
                enabled = contactsUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Importer les contacts") }
            importStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.updateSettings(settings.copy(
                        sipEnabled = enabled,
                        sipUsername = username.trim(),
                        sipPassword = password,
                        sipDomain = domain.trim(),
                        sipServer = server.trim(),
                        sipContactsUrl = contactsUrl.trim()
                    ))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Enregistrer") }
        }
    }
}
