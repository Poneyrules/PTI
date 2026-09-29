package com.pti.worker

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
                        SettingsScreen(viewModel = settingsViewModel, onBack = { navController.popBackStack() })
                    }
                    composable("contacts") {
                        ContactsScreen(viewModel = contactsViewModel, onBack = { navController.popBackStack() })
                    }
                    composable("events") {
                        EventLogScreen(viewModel = mainViewModel, onBack = { navController.popBackStack() })
                    }
                    composable("diagnostic") {
                        DiagnosticScreen(viewModel = mainViewModel, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.checkPermissions()
    }
}
