package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.EmptyState
import com.example.ui.components.MessageCard
import com.example.ui.components.StatTile
import com.example.ui.components.StatusCard
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoLight
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToMessages: () -> Unit,
    onNavigateToMessageDetail: (Long) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToTroubleshooting: () -> Unit,
    onNavigateToDatabaseInspector: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val totalCount by viewModel.totalSavedCount.collectAsStateWithLifecycle()
    val todayCount by viewModel.capturedTodayCount.collectAsStateWithLifecycle()
    val monitoredApps by viewModel.monitoredPackages.collectAsStateWithLifecycle()
    val recentMessages by viewModel.recentMessages.collectAsStateWithLifecycle()
    val isServiceConnected by viewModel.isServiceConnected.collectAsStateWithLifecycle()

    var isAccessGranted by remember { mutableStateOf(viewModel.checkNotificationAccess()) }

    // Re-check permission whenever dashboard appears
    DisposableEffect(Unit) {
        isAccessGranted = viewModel.checkNotificationAccess()
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "MsgVault",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Local Message Preserver",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSearch,
                        modifier = Modifier.testTag("dashboard_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Messages"
                        )
                    }
                    IconButton(
                        onClick = onNavigateToTroubleshooting,
                        modifier = Modifier.testTag("dashboard_troubleshoot_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Diagnostics & Troubleshooting"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero banner image
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .testTag("dashboard_hero_card"),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_vault_hero),
                            contentDescription = "MsgVault Hero Banner",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // Gradient overlay for readability
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)
                        ) {}
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Deleted Message Protection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                            Text(
                                text = "Saved copies are preserved even if unsent or deleted",
                                style = MaterialTheme.typography.bodySmall,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Permission Status Card
            item {
                StatusCard(
                    isAccessGranted = isAccessGranted,
                    isServiceConnected = isServiceConnected,
                    onGrantAccessClick = {
                        viewModel.openNotificationAccessSettings(context)
                    }
                )
            }

            // Metric Stat Tiles
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatTile(
                        title = "Saved Messages",
                        value = totalCount.toString(),
                        icon = Icons.Default.AllInbox,
                        iconTint = IndigoLight,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        title = "Captured Today",
                        value = todayCount.toString(),
                        icon = Icons.Default.Today,
                        iconTint = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        title = "Monitored Apps",
                        value = monitoredApps.size.toString(),
                        icon = Icons.Default.Apps,
                        iconTint = EmeraldSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Actions Bar
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { viewModel.sendTestNotification(context) },
                            modifier = Modifier.testTag("send_test_notification_btn")
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Capture", fontSize = 13.sp)
                        }
                        VerticalDivider(modifier = Modifier.height(24.dp))
                        TextButton(
                            onClick = onNavigateToApps,
                            modifier = Modifier.testTag("manage_apps_btn")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Filter Apps", fontSize = 13.sp)
                        }
                        VerticalDivider(modifier = Modifier.height(24.dp))
                        TextButton(
                            onClick = onNavigateToDatabaseInspector,
                            modifier = Modifier.testTag("dashboard_db_inspector_btn")
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Inspector", fontSize = 13.sp)
                        }
                    }
                }
            }

            // Section Header: Recent Messages
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Messages",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (recentMessages.isNotEmpty()) {
                        TextButton(
                            onClick = onNavigateToMessages,
                            modifier = Modifier.testTag("view_all_messages_btn")
                        ) {
                            Text("View All (${totalCount})")
                        }
                    }
                }
            }

            // Recent Messages List or Empty State
            if (recentMessages.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        title = "No Messages Captured Yet",
                        description = "MsgVault will automatically preserve incoming notifications from WhatsApp, Telegram, Messenger, and other supported apps.",
                        actionLabel = "Send Test Notification",
                        onActionClick = {
                            viewModel.sendTestNotification(context)
                        }
                    )
                }
            } else {
                items(recentMessages, key = { it.id }) { msg ->
                    MessageCard(
                        message = msg,
                        onClick = { onNavigateToMessageDetail(msg.id) },
                        onFavoriteToggle = { viewModel.toggleFavorite(msg) }
                    )
                }
            }

            // Bottom spacer for scroll comfort
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
