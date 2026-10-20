package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.PackageDiagnosticStatus
import com.example.service.DiagnosticLogEntry
import com.example.ui.components.formatFullDateTime
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoLight
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleshootingScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isConnected by viewModel.isServiceConnected.collectAsStateWithLifecycle()
    val lastCaptured by viewModel.lastCapturedTime.collectAsStateWithLifecycle()
    val sessionCount by viewModel.totalCapturedInSession.collectAsStateWithLifecycle()
    val packageDiagnostics by viewModel.packageDiagnostics.collectAsStateWithLifecycle()
    val recentLogs by viewModel.recentDiagnosticLogs.collectAsStateWithLifecycle()
    var isAccessGranted by remember { mutableStateOf(viewModel.checkNotificationAccess()) }

    var testMessageInput by remember { mutableStateOf("Testing MsgVault capture: will this unsent message stay?") }
    var testNotificationSent by remember { mutableStateOf(false) }
    var diagnosticSearchQuery by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        isAccessGranted = viewModel.checkNotificationAccess()
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics & Health", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("troubleshoot_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // Live Diagnostics Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "LIVE SYSTEM STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        DiagnosticRow(
                            label = "Notification Listener Permission",
                            value = if (isAccessGranted) "Granted" else "Missing / Denied",
                            isSuccess = isAccessGranted
                        )

                        DiagnosticRow(
                            label = "Notification Listener Service",
                            value = if (isConnected) "Active & Bound" else "Disconnected / Standby",
                            isSuccess = isConnected
                        )

                        DiagnosticRow(
                            label = "Session Captured Count",
                            value = "$sessionCount messages",
                            isSuccess = true
                        )

                        DiagnosticRow(
                            label = "Last Captured Time",
                            value = if (lastCaptured > 0) formatFullDateTime(lastCaptured) else "None in this session",
                            isSuccess = lastCaptured > 0
                        )

                        if (!isAccessGranted) {
                            Button(
                                onClick = { viewModel.openNotificationAccessSettings(context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("fix_permission_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Open Android Settings to Enable")
                            }
                        }
                    }
                }
            }

            // Real Test Notification Tool
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = IndigoLight,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Verify Capture With Real Notification",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Send a test notification to your system tray. The NotificationCaptureService will intercept and store it in MsgVault immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = testMessageInput,
                            onValueChange = { testMessageInput = it },
                            label = { Text("Test Message Text") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("test_message_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                viewModel.sendTestNotification(context, testMessageInput)
                                testNotificationSent = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("send_test_notification_action"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dispatch Test Notification")
                        }

                        FilledTonalButton(
                            onClick = {
                                viewModel.simulateDeletionNotification(context)
                                testNotificationSent = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("simulate_deleted_notification_action"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simulate Deletion ('This message was deleted')")
                        }

                        if (testNotificationSent) {
                            Text(
                                text = "Notification dispatched! MsgVault will capture and preserve it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldSuccess,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )

                        OutlinedButton(
                            onClick = { viewModel.seedSampleDemoMessages() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("seed_demo_messages_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Load Realistic Demo Messages (WhatsApp, IG, Telegram)")
                        }
                    }
                }
            }

            // Per-Package Capture Diagnostics Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PER-PACKAGE CAPTURE DIAGNOSTICS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Inspect the 4-stage pipeline: Event Received → Filter Accepted → Message Extracted → Database Insertion. (Private message text is never exposed in logs).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearDiagnostics() },
                                modifier = Modifier.testTag("reset_diagnostics_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset Diagnostics")
                            }
                        }

                        OutlinedTextField(
                            value = diagnosticSearchQuery,
                            onValueChange = { diagnosticSearchQuery = it },
                            placeholder = { Text("Filter package diagnostics…", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Per-Package Diagnostic Cards
            val filteredDiagnostics = packageDiagnostics.filter {
                if (diagnosticSearchQuery.isBlank()) true
                else it.appDisplayName.contains(diagnosticSearchQuery, ignoreCase = true) ||
                        it.packageName.contains(diagnosticSearchQuery, ignoreCase = true)
            }

            items(filteredDiagnostics.size) { index ->
                val diag = filteredDiagnostics[index]
                PackageDiagnosticCard(diag = diag)
            }

            // OEM Battery Optimization & Troubleshooting
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "DEVICE OEM BACKGROUND GUIDE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )

                        GuidanceItem(
                            device = "Samsung (OneUI)",
                            instruction = "Go to Settings → Apps → MsgVault → Battery → Select 'Unrestricted'. Ensure MsgVault is not in 'Sleeping apps'."
                        )
                        GuidanceItem(
                            device = "Xiaomi / Redmi (MIUI / HyperOS)",
                            instruction = "Enable 'Autostart' for MsgVault in app info, and set Battery Saver to 'No restrictions'."
                        )
                        GuidanceItem(
                            device = "OnePlus / Oppo (OxygenOS / ColorOS)",
                            instruction = "Turn off 'Optimize battery usage' for MsgVault in Battery settings."
                        )
                        GuidanceItem(
                            device = "Stock Android / Google Pixel",
                            instruction = "Settings → Apps → MsgVault → App battery usage → Choose 'Unrestricted'."
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, isSuccess: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isSuccess) EmeraldSuccess else MaterialTheme.colorScheme.error)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSuccess) EmeraldSuccess else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun GuidanceItem(device: String, instruction: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = device,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = instruction,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun PackageDiagnosticCard(diag: PackageDiagnosticStatus) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostic_card_${diag.packageName}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (diag.notificationReceived) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = diag.appDisplayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = diag.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (diag.totalMessagesSaved > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EmeraldSuccess.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${diag.totalMessagesSaved} SAVED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSuccess,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 4-stage pipeline checklist:
            // 1. Notification Event Received
            // 2. Package Accepted by Filter
            // 3. Message Text Extracted
            // 4. Database Insertion Succeeded
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PipelineStageBadge(
                    stage = "Received",
                    success = diag.notificationReceived,
                    modifier = Modifier.weight(1f)
                )
                PipelineStageBadge(
                    stage = "Accepted",
                    success = diag.packageAccepted,
                    modifier = Modifier.weight(1f)
                )
                PipelineStageBadge(
                    stage = "Extracted",
                    success = diag.messageExtracted,
                    modifier = Modifier.weight(1f)
                )
                PipelineStageBadge(
                    stage = "Inserted",
                    success = diag.databaseInserted,
                    modifier = Modifier.weight(1f)
                )
            }

            // Diagnostic status reason
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Status / Reason:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = diag.statusReason,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineStageBadge(stage: String, success: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (success) EmeraldSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stage,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (success) "YES" else "NO",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (success) EmeraldSuccess else MaterialTheme.colorScheme.outline
            )
        }
    }
}
