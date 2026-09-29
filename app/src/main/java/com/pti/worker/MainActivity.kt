package com.pti.worker

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pti.worker.ui.*
import com.pti.worker.ui.theme.PtiTheme
import com.pti.worker.ui.viewmodel.ContactsViewModel
import com.pti.worker.ui.viewmodel.MainViewModel
import com.pti.worker.ui.viewmodel.SettingsViewModel
import com.pti.worker.util.PermissionHelper

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val contactsViewModel: ContactsViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { mainViewModel.checkPermissions() }

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val current = settingsViewModel.settings.value
            if (uri != null) {
                val ringtone = RingtoneManager.getRingtone(this, uri)
                val name = try {
                    ringtone?.getTitle(this) ?: "Sonnerie personnalisée"
                } catch (e: Exception) {
                    "Sonnerie personnalisée"
                }
                settingsViewModel.updateSettings(
                    current.copy(
                        alertRingtoneUri = uri.toString(),
                        alertRingtoneName = name
                    )
                )
            } else {
                settingsViewModel.updateSettings(
                    current.copy(alertRingtoneUri = null, alertRingtoneName = null)
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val missing = PermissionHelper.missingPermissions(this)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }

        setContent {
            PtiTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            viewModel = mainViewModel,
                            onNavigateSettings = { navController.navigate("settings") },
                            onNavigateContacts = { navController.navigate("contacts") },
                            onNavigateEvents = { navController.navigate("events") },
                            onNavigateDiagnostic = { navController.navigate("diagnostic") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onBack = { navController.popBackStack() },
                            onPickRingtone = { openRingtonePicker() }
                        )
                    }
                    composable("contacts") {
                        ContactsScreen(
                            viewModel = contactsViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("events") {
                        EventLogScreen(
                            viewModel = mainViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("diagnostic") {
                        DiagnosticScreen(
                            viewModel = mainViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    private fun openRingtonePicker() {
        val currentUri = settingsViewModel.settings.value.alertRingtoneUri
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sonnerie d'alerte PTI")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            if (currentUri != null) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentUri))
            } else {
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                )
            }
        }
        ringtonePickerLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.checkPermissions()
    }
}
