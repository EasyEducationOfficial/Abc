package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.AppDatabase
import com.example.data.preferences.AppPreferences
import com.example.repository.MessageRepository
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

sealed class AppDestination {
    object Dashboard : AppDestination()
    object Messages : AppDestination()
    object MediaVault : AppDestination()
    object Troubleshooting : AppDestination()
    object Apps : AppDestination()
    object PermissionWizard : AppDestination()
    data class MessageDetail(val messageId: Long) : AppDestination()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getInstance(applicationContext)
        val repo = MessageRepository(db)
        val prefs = AppPreferences(applicationContext)

        setContent {
            MyApplicationTheme {
                val viewModel = remember {
                    MainViewModel(applicationContext, repo, prefs)
                }

                val isWizardCompleted by viewModel.isPermissionWizardCompleted.collectAsState()
                var currentDestination by remember {
                    mutableStateOf<AppDestination>(
                        if (isWizardCompleted) AppDestination.Dashboard else AppDestination.PermissionWizard
                    )
                }

                // If wizard finishes and user was on wizard, go to dashboard
                LaunchedEffect(isWizardCompleted) {
                    if (isWizardCompleted && currentDestination is AppDestination.PermissionWizard) {
                        currentDestination = AppDestination.Dashboard
                    }
                }

                // Back handling
                if (currentDestination !is AppDestination.Dashboard && currentDestination !is AppDestination.PermissionWizard) {
                    BackHandler {
                        currentDestination = AppDestination.Dashboard
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        // Show bottom nav on top-level screens
                        if (currentDestination in listOf(
                                AppDestination.Dashboard,
                                AppDestination.Messages,
                                AppDestination.MediaVault,
                                AppDestination.Troubleshooting
                            )
                        ) {
                            NavigationBar(
                                tonalElevation = 8.dp,
                                modifier = Modifier.testTag("main_bottom_nav")
                            ) {
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                                    label = { Text("Dashboard") },
                                    selected = currentDestination is AppDestination.Dashboard,
                                    onClick = { currentDestination = AppDestination.Dashboard },
                                    modifier = Modifier.testTag("nav_dashboard")
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Messages") },
                                    label = { Text("Messages") },
                                    selected = currentDestination is AppDestination.Messages,
                                    onClick = { currentDestination = AppDestination.Messages },
                                    modifier = Modifier.testTag("nav_messages")
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.PermMedia, contentDescription = "Media Vault") },
                                    label = { Text("Media Vault") },
                                    selected = currentDestination is AppDestination.MediaVault,
                                    onClick = { currentDestination = AppDestination.MediaVault },
                                    modifier = Modifier.testTag("nav_media_vault")
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Analytics, contentDescription = "Diagnostics") },
                                    label = { Text("Diagnostics") },
                                    selected = currentDestination is AppDestination.Troubleshooting,
                                    onClick = { currentDestination = AppDestination.Troubleshooting },
                                    modifier = Modifier.testTag("nav_diagnostics")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (val dest = currentDestination) {
                            is AppDestination.PermissionWizard -> {
                                PermissionWizardScreen(
                                    viewModel = viewModel,
                                    onComplete = {
                                        currentDestination = AppDestination.Dashboard
                                    }
                                )
                            }
                            is AppDestination.Dashboard -> {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToMessages = { currentDestination = AppDestination.Messages },
                                    onNavigateToMediaVault = { currentDestination = AppDestination.MediaVault },
                                    onNavigateToDiagnostics = { currentDestination = AppDestination.Troubleshooting },
                                    onNavigateToApps = { currentDestination = AppDestination.Apps },
                                    onNavigateToPermissions = { currentDestination = AppDestination.PermissionWizard },
                                    onNavigateToDetail = { id -> currentDestination = AppDestination.MessageDetail(id) }
                                )
                            }
                            is AppDestination.Messages -> {
                                MessagesScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentDestination = AppDestination.Dashboard },
                                    onNavigateToDetail = { id -> currentDestination = AppDestination.MessageDetail(id) }
                                )
                            }
                            is AppDestination.MediaVault -> {
                                MediaVaultScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentDestination = AppDestination.Dashboard },
                                    onNavigateToPermissions = { currentDestination = AppDestination.PermissionWizard }
                                )
                            }
                            is AppDestination.Troubleshooting -> {
                                TroubleshootingScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentDestination = AppDestination.Dashboard },
                                    onNavigateToPermissions = { currentDestination = AppDestination.PermissionWizard }
                                )
                            }
                            is AppDestination.Apps -> {
                                AppsConfigScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentDestination = AppDestination.Dashboard }
                                )
                            }
                            is AppDestination.MessageDetail -> {
                                MessageDetailScreen(
                                    messageId = dest.messageId,
                                    viewModel = viewModel,
                                    onNavigateBack = { currentDestination = AppDestination.Dashboard }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
