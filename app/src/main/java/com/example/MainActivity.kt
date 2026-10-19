package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.ui.navigation.Screen
import com.example.ui.screens.*
import com.example.ui.theme.MsgVaultTheme
import com.example.ui.viewmodel.MainViewModel

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MsgVaultTheme {
                val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsStateWithLifecycle()
                val isAppLockEnabled by viewModel.isAppLockEnabled.collectAsStateWithLifecycle()
                val isAppUnlocked by viewModel.isAppUnlocked.collectAsStateWithLifecycle()
                val appLockPin by viewModel.appLockPin.collectAsStateWithLifecycle()

                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val bottomNavItems = listOf(
                    BottomNavItem(Screen.Dashboard.route, "Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_dashboard"),
                    BottomNavItem(Screen.Messages.route, "Messages", Icons.Filled.Forum, Icons.Outlined.Forum, "nav_messages"),
                    BottomNavItem(Screen.Favorites.route, "Favorites", Icons.Filled.Star, Icons.Outlined.StarOutline, "nav_favorites"),
                    BottomNavItem(Screen.Apps.route, "Apps", Icons.Filled.Apps, Icons.Outlined.Apps, "nav_apps"),
                    BottomNavItem(Screen.Security.route, "Security", Icons.Filled.Security, Icons.Outlined.Security, "nav_security")
                )

                val showBottomBar = bottomNavItems.any { it.route == currentRoute }

                // Determine display flow: Lock Screen -> Onboarding Screen -> Main Navigation
                when {
                    isAppLockEnabled && !isAppUnlocked -> {
                        AppLockScreen(
                            viewModel = viewModel,
                            expectedPin = appLockPin,
                            onUnlocked = { viewModel.unlockApp() }
                        )
                    }
                    !isOnboardingCompleted -> {
                        OnboardingScreen(
                            viewModel = viewModel,
                            onComplete = {
                                viewModel.completeOnboarding()
                            }
                        )
                    }
                    else -> {
                        Scaffold(
                            bottomBar = {
                                if (showBottomBar) {
                                    NavigationBar(
                                        modifier = Modifier.testTag("main_bottom_nav"),
                                        tonalElevation = 6.dp
                                    ) {
                                        bottomNavItems.forEach { item ->
                                            val isSelected = currentRoute == item.route
                                            NavigationBarItem(
                                                selected = isSelected,
                                                onClick = {
                                                    navController.navigate(item.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                                        contentDescription = item.label
                                                    )
                                                },
                                                label = { Text(item.label) },
                                                modifier = Modifier.testTag(item.testTag)
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = Screen.Dashboard.route,
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                composable(Screen.Dashboard.route) {
                                    DashboardScreen(
                                        viewModel = viewModel,
                                        onNavigateToMessages = { navController.navigate(Screen.Messages.route) },
                                        onNavigateToMessageDetail = { id -> navController.navigate(Screen.MessageDetail.createRoute(id)) },
                                        onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                                        onNavigateToApps = { navController.navigate(Screen.Apps.route) },
                                        onNavigateToTroubleshooting = { navController.navigate(Screen.Troubleshooting.route) },
                                        onNavigateToDatabaseInspector = { navController.navigate(Screen.DatabaseInspector.route) }
                                    )
                                }

                                composable(Screen.Messages.route) {
                                    MessageHistoryScreen(
                                        viewModel = viewModel,
                                        onNavigateToMessageDetail = { id -> navController.navigate(Screen.MessageDetail.createRoute(id)) },
                                        onNavigateToSearch = { navController.navigate(Screen.Search.route) }
                                    )
                                }

                                composable(Screen.Favorites.route) {
                                    FavoritesScreen(
                                        viewModel = viewModel,
                                        onNavigateToMessageDetail = { id -> navController.navigate(Screen.MessageDetail.createRoute(id)) }
                                    )
                                }

                                composable(Screen.Apps.route) {
                                    MonitoringSettingsScreen(
                                        viewModel = viewModel,
                                        onNavigateToTroubleshooting = { navController.navigate(Screen.Troubleshooting.route) }
                                    )
                                }

                                composable(Screen.Security.route) {
                                    PrivacySecurityScreen(
                                        viewModel = viewModel,
                                        onNavigateToAbout = { navController.navigate(Screen.About.route) },
                                        onNavigateToDatabaseInspector = { navController.navigate(Screen.DatabaseInspector.route) }
                                    )
                                }

                                composable(Screen.DatabaseInspector.route) {
                                    DatabaseInspectorScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }

                                composable(Screen.Search.route) {
                                    SearchScreen(
                                        viewModel = viewModel,
                                        onNavigateToMessageDetail = { id -> navController.navigate(Screen.MessageDetail.createRoute(id)) },
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }

                                composable(Screen.Troubleshooting.route) {
                                    TroubleshootingScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }

                                composable(Screen.About.route) {
                                    AboutScreen(
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }

                                composable(
                                    route = Screen.MessageDetail.route,
                                    arguments = listOf(navArgument("messageId") { type = NavType.LongType })
                                ) { backStackEntry ->
                                    val messageId = backStackEntry.arguments?.getLong("messageId") ?: 0L
                                    MessageDetailScreen(
                                        messageId = messageId,
                                        viewModel = viewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
