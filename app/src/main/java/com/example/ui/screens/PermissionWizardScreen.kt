package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RubyError
import com.example.ui.viewmodel.MainViewModel
import com.example.util.PermissionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionWizardScreen(
    viewModel: MainViewModel,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isNotificationAccess by viewModel.isNotificationAccessGranted.collectAsState()
    val isNotificationPermission by viewModel.isNotificationPermissionGranted.collectAsState()
    val isMediaPermission by viewModel.isMediaPermissionGranted.collectAsState()
    val isBatteryExempt by viewModel.isBatteryExemptionGranted.collectAsState()
    val safUriStr by viewModel.safMediaTreeUri.collectAsState()

    // Refresh permission statuses on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Permission launchers
    val postNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissionStatuses()
    }

    val mediaPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        viewModel.refreshPermissionStatuses()
    }

    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (ignored: Exception) {}
            viewModel.saveSafTreeUri(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MsgVault Setup Wizard",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Configure permissions for reliable message & media capture",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    val allCriticalGranted = isNotificationAccess && isMediaPermission

                    if (!allCriticalGranted) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Notification access & Media access are required to save incoming messages and photos.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.completePermissionWizard()
                            onComplete()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("wizard_complete_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (allCriticalGranted) EmeraldSuccess else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (allCriticalGranted) Icons.Default.CheckCircle else Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (allCriticalGranted) "Setup Complete • Enter MsgVault" else "Continue to MsgVault",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "100% Private & Device-Local",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "MsgVault runs entirely on your phone. Captured messages and media never leave your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 1. Notification Access Permission (Crucial)
            item {
                PermissionCard(
                    title = "1. Notification Listener Access",
                    description = "Required to capture incoming text and unsend notices from WhatsApp, Messenger, Instagram, imo, Telegram, etc., as soon as they arrive.",
                    isGranted = isNotificationAccess,
                    isRequired = true,
                    icon = Icons.Default.NotificationsActive,
                    actionText = if (isNotificationAccess) "Access Granted" else "Enable in Android Settings",
                    onAction = { viewModel.openNotificationAccessSettings(context) },
                    testTag = "perm_notification_access"
                )
            }

            // 2. Storage & Media Permissions (Crucial for MediaVault)
            item {
                PermissionCard(
                    title = "2. Device Media & File Access",
                    description = "Enables MsgVault to read incoming photos, videos, voice notes, and documents so they can be saved locally before deletion.",
                    isGranted = isMediaPermission,
                    isRequired = true,
                    icon = Icons.Default.PermMedia,
                    actionText = if (isMediaPermission) "Media Access Granted" else "Grant Media Permissions",
                    onAction = {
                        val perms = PermissionManager.getRequiredMediaPermissionsList()
                        mediaPermissionsLauncher.launch(perms)
                    },
                    testTag = "perm_media_access"
                )
            }

            // 3. Storage Access Framework (SAF) Folder Monitoring (Advanced Media Capture)
            item {
                val hasSaf = !safUriStr.isNullOrBlank()
                PermissionCard(
                    title = "3. Storage Access Framework (SAF) Folder Access",
                    description = "Select your WhatsApp or messaging media folder (e.g. 'Android/media/com.whatsapp') to enable direct real-time photo, video, and voice note retention on Scoped Storage.",
                    isGranted = hasSaf,
                    isRequired = false,
                    badgeText = if (hasSaf) "Folder Connected" else "Recommended",
                    icon = Icons.Default.FolderSpecial,
                    actionText = if (hasSaf) "Change Folder" else "Select Media Folder",
                    onAction = {
                        safFolderLauncher.launch(null)
                    },
                    testTag = "perm_saf_folder"
                )
            }

            // 4. Notifications Permission (Android 13+)
            item {
                PermissionCard(
                    title = "4. System Notifications Permission",
                    description = "Allows MsgVault to post status alerts when an unsent/deleted message or photo was intercepted in the background.",
                    isGranted = isNotificationPermission,
                    isRequired = false,
                    badgeText = if (isNotificationPermission) "Allowed" else "Optional",
                    icon = Icons.Default.Campaign,
                    actionText = if (isNotificationPermission) "Permission Granted" else "Allow Notifications",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            postNotificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.openNotificationPermissionSettings(context)
                        }
                    },
                    testTag = "perm_post_notifications"
                )
            }

            // 5. Battery Optimization Exemption
            item {
                PermissionCard(
                    title = "5. Battery Optimization Exemption",
                    description = "Prevents Android OS from pausing the capture engine or media observer when your screen is turned off or battery saver is active.",
                    isGranted = isBatteryExempt,
                    isRequired = false,
                    badgeText = if (isBatteryExempt) "Unrestricted" else "Recommended",
                    icon = Icons.Default.BatteryChargingFull,
                    actionText = if (isBatteryExempt) "Optimization Exempt" else "Exempt from Battery Saver",
                    onAction = { viewModel.requestBatteryOptimization(context) },
                    testTag = "perm_battery_exempt"
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    isRequired: Boolean,
    icon: ImageVector,
    actionText: String,
    onAction: () -> Unit,
    testTag: String,
    badgeText: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = if (isGranted) {
            androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.5f))
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isGranted) EmeraldSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isGranted) EmeraldSuccess else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (isRequired) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "Required",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isGranted) EmeraldSuccess.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isGranted) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isGranted) (badgeText ?: "Granted") else (badgeText ?: "Not Granted"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isGranted) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isGranted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = actionText,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    }
}
