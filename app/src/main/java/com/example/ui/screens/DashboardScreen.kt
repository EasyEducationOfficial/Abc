package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.MessageEntity
import com.example.data.model.SupportedAppRegistry
import com.example.ui.components.EmptyState
import com.example.ui.components.MessageCard
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RubyError
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToMessages: () -> Unit,
    onNavigateToMediaVault: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToDetail: (Long) -> Unit
) {
    val isServiceConnected by viewModel.isServiceConnected.collectAsState()
    val isMediaObserverActive by viewModel.isMediaObserverActive.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val totalMediaCount by viewModel.totalMediaCount.collectAsState()
    val recentMessages by viewModel.recentMessages.collectAsState()

    val isPlayingAudio by viewModel.isPlayingAudio.collectAsState()
    val currentAudioPath by viewModel.currentPlayingPath.collectAsState()
    val audioProgress by viewModel.audioProgressMs.collectAsState()
    val audioDuration by viewModel.audioDurationMs.collectAsState()

    var selectedAppPackage by remember { mutableStateOf<String?>(null) }

    val filteredMessages = remember(recentMessages, selectedAppPackage) {
        if (selectedAppPackage == null) recentMessages
        else recentMessages.filter { it.sourcePackage == selectedAppPackage }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "MsgVault",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Private deleted message & media preserver",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToDiagnostics,
                        modifier = Modifier.testTag("dashboard_diagnostics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Diagnostics",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onNavigateToPermissions,
                        modifier = Modifier.testTag("dashboard_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings & Permissions"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Service Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isServiceConnected) {
                            EmeraldSuccess.copy(alpha = 0.12f)
                        } else {
                            RubyError.copy(alpha = 0.12f)
                        }
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isServiceConnected) EmeraldSuccess.copy(alpha = 0.3f) else RubyError.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = if (isServiceConnected) EmeraldSuccess else RubyError,
                                modifier = Modifier.size(12.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isServiceConnected) "Capture Engine Active" else "Capture Engine Not Running",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isServiceConnected) EmeraldSuccess else RubyError
                                )
                                Text(
                                    text = if (isServiceConnected) {
                                        "Listening for WhatsApp, Messenger, Instagram, imo & more"
                                    } else {
                                        "Grant Notification Access to enable capture"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isServiceConnected) {
                            Button(
                                onClick = onNavigateToPermissions,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RubyError),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Quick Stats Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Saved Messages",
                        value = totalCount.toString(),
                        icon = Icons.Default.ChatBubbleOutline,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToMessages() }
                    )
                    StatCard(
                        title = "Media Files",
                        value = totalMediaCount.toString(),
                        icon = Icons.Default.PhotoLibrary,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToMediaVault() }
                    )
                }
            }

            // Quick Feature Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionChipButton(
                        text = "Diagnostics",
                        icon = Icons.Default.Analytics,
                        onClick = onNavigateToDiagnostics,
                        modifier = Modifier.weight(1f)
                    )
                    ActionChipButton(
                        text = "Media Vault",
                        icon = Icons.Default.PermMedia,
                        onClick = onNavigateToMediaVault,
                        modifier = Modifier.weight(1f)
                    )
                    ActionChipButton(
                        text = "Supported Apps",
                        icon = Icons.Default.Apps,
                        onClick = onNavigateToApps,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Platform Filter Chips
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filter by Platform",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (selectedAppPackage != null) {
                            TextButton(
                                onClick = { selectedAppPackage = null },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Show All", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedAppPackage == null,
                                onClick = { selectedAppPackage = null },
                                label = { Text("All Platforms") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }

                        items(SupportedAppRegistry.ALL_SUPPORTED_APPS) { app ->
                            FilterChip(
                                selected = selectedAppPackage == app.packageName,
                                onClick = {
                                    selectedAppPackage = if (selectedAppPackage == app.packageName) null else app.packageName
                                },
                                label = { Text(app.displayName) }
                            )
                        }
                    }
                }
            }

            // Header for Recent Messages
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Captured Stream",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onNavigateToMessages) {
                        Text("View All ($totalCount)")
                    }
                }
            }

            // Empty State or List
            if (filteredMessages.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Inbox,
                        title = "No captured messages yet",
                        description = "Whenever an incoming message arrives from WhatsApp, Messenger, Instagram, imo, or other platforms, it will instantly be preserved here.",
                        actionLabel = "Run Diagnostics & Test",
                        onActionClick = onNavigateToDiagnostics
                    )
                }
            } else {
                items(filteredMessages, key = { it.id }) { msg ->
                    MessageCard(
                        message = msg,
                        onClick = { onNavigateToDetail(msg.id) },
                        onFavoriteToggle = { viewModel.toggleFavorite(msg) },
                        isPlayingAudio = isPlayingAudio && currentAudioPath == msg.mediaFilePath,
                        audioProgressMs = if (currentAudioPath == msg.mediaFilePath) audioProgress else 0,
                        audioDurationMs = if (currentAudioPath == msg.mediaFilePath) audioDuration else 0,
                        onPlayAudio = { path -> viewModel.playAudio(path) },
                        onPauseAudio = { viewModel.pauseAudio() },
                        onSeekAudio = { pos -> viewModel.seekAudio(pos) }
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ActionChipButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
