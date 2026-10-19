package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.MessageEntity
import com.example.data.model.ColumnInfo
import com.example.data.model.DatabaseStats
import com.example.ui.components.EmptyState
import com.example.ui.components.formatFullDateTime
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoLight
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseInspectorScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val messages by viewModel.filteredMessages.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalSavedCount.collectAsStateWithLifecycle()
    val stats by viewModel.databaseStats.collectAsStateWithLifecycle()
    val isLoading by viewModel.isInspectorLoading.collectAsStateWithLifecycle()

    var inspectorFilter by remember { mutableStateOf("") }
    var selectedRawMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var showDeleteEntryConfirm by remember { mutableStateOf<MessageEntity?>(null) }
    var showVacuumDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadDatabaseStats(context)
    }

    val displayedMessages = remember(messages, inspectorFilter) {
        if (inspectorFilter.isBlank()) {
            messages
        } else {
            val q = inspectorFilter.trim().lowercase()
            messages.filter {
                it.id.toString() == q ||
                it.sourcePackage.lowercase().contains(q) ||
                (it.senderName?.lowercase()?.contains(q) == true) ||
                it.messageText.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Database Inspector", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("Room SQLite Support Engine", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("inspector_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isExporting = true
                            viewModel.exportDatabaseToJson(context) { file ->
                                isExporting = false
                                exportedFile = file
                                if (file != null) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Backup exported: ${file.name}")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag("inspector_export_action_btn")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export JSON Backup")
                    }
                    IconButton(
                        onClick = { viewModel.loadDatabaseStats(context) },
                        modifier = Modifier.testTag("inspector_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Stats")
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Backup Export Result Banner (if just exported)
            if (exportedFile != null) {
                item {
                    val file = exportedFile!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = EmeraldSuccess.copy(alpha = 0.12f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Database Export Succeeded!", fontWeight = FontWeight.Bold, color = EmeraldSuccess, style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("File: ${file.name}", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Text("Size: ${formatFileSize(file.length())} • ${totalCount} records", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.shareExportedFile(context, file) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("share_exported_json_btn")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share / Save Backup", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { exportedFile = null },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Dismiss", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Database Overview & Stats Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inspector_overview_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
                            Text(
                                text = "SQLITE STORAGE METRICS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = EmeraldSuccess.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Integrity: ${stats?.integrityCheck ?: "OK"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EmeraldSuccess,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        StatRow("Database File", stats?.dbPath ?: context.getDatabasePath("msgvault.db").name)
                        StatRow("File Size on Disk", formatFileSize(stats?.fileSizeBytes ?: 0L))
                        StatRow("Total Stored Rows", "$totalCount messages in SQLite table")
                        StatRow("Page Metrics", "Page Size: ${stats?.pageSize ?: 4096} B • Pages: ${stats?.pageCount ?: 0}")

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Quick Inspector Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isExporting = true
                                    viewModel.exportDatabaseToJson(context) { file ->
                                        isExporting = false
                                        exportedFile = file
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inspector_export_json_btn")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export JSON", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { showVacuumDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inspector_vacuum_btn")
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("VACUUM", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Schema Table Definition (from PRAGMA table_info)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "TABLE SCHEMA: messages (${stats?.columns?.size ?: 15} Columns)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val columns = stats?.columns ?: emptyList()
                        if (columns.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    columns.forEach { col ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${col.cid}. ${col.name}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                fontWeight = if (col.primaryKey) FontWeight.Bold else FontWeight.Medium,
                                                color = if (col.primaryKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.width(180.dp)
                                            )
                                            Text(
                                                text = col.type,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.width(90.dp)
                                            )
                                            Text(
                                                text = if (col.primaryKey) "PRIMARY KEY" else if (col.notNull) "NOT NULL" else "NULLABLE",
                                                fontSize = 10.sp,
                                                color = if (col.primaryKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Search / Filter inside Inspector
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stored Records (${displayedMessages.size} entries)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (messages.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearAllDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear Table", fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = inspectorFilter,
                    onValueChange = { inspectorFilter = it },
                    placeholder = { Text("Filter entries by ID, package, sender, text…", fontSize = 13.sp) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (inspectorFilter.isNotEmpty()) {
                            IconButton(onClick = { inspectorFilter = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inspector_filter_input")
                )
            }

            // Raw Entry Cards
            if (displayedMessages.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Storage,
                        title = "No Entries Found",
                        description = if (inspectorFilter.isNotEmpty()) "No stored rows match \"$inspectorFilter\"" else "The messages table in Room SQLite is currently empty."
                    )
                }
            } else {
                items(displayedMessages, key = { it.id }) { msg ->
                    InspectorMessageRow(
                        message = msg,
                        onInspectRaw = { selectedRawMessage = msg },
                        onDeleteEntry = { showDeleteEntryConfirm = msg }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Raw JSON Details Modal
    if (selectedRawMessage != null) {
        val msg = selectedRawMessage!!
        AlertDialog(
            onDismissRequest = { selectedRawMessage = null },
            title = { Text("Row Record #${msg.id}", fontWeight = FontWeight.Bold) },
            text = {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        InspectorField("ID", msg.id.toString())
                        InspectorField("sourcePackage", msg.sourcePackage)
                        InspectorField("sourceAppName", msg.sourceAppName)
                        InspectorField("senderName", msg.senderName ?: "null")
                        InspectorField("conversationTitle", msg.conversationTitle ?: "null")
                        InspectorField("messageText", msg.messageText)
                        InspectorField("receivedAt", "${msg.receivedAt} (${formatFullDateTime(msg.receivedAt)})")
                        InspectorField("capturedAt", "${msg.capturedAt} (${formatFullDateTime(msg.capturedAt)})")
                        InspectorField("notificationKey", msg.notificationKey ?: "null")
                        InspectorField("isGroupMessage", msg.isGroupMessage.toString())
                        InspectorField("isFavorite", msg.isFavorite.toString())
                        InspectorField("isRead", msg.isRead.toString())
                        InspectorField("contentAvailable", msg.contentAvailable.toString())
                        InspectorField("isDeletedBySender", msg.isDeletedBySender.toString())
                        InspectorField("deletedNoticeTimestamp", msg.deletedNoticeTimestamp?.let { "$it (${formatFullDateTime(it)})" } ?: "null")
                        InspectorField("attachmentDescription", msg.attachmentDescription ?: "null")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedRawMessage = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Manual Deletion Confirmation Dialog
    if (showDeleteEntryConfirm != null) {
        val entry = showDeleteEntryConfirm!!
        AlertDialog(
            onDismissRequest = { showDeleteEntryConfirm = null },
            title = { Text("Delete Entry #${entry.id}?") },
            text = { Text("Manually delete message entry #${entry.id} (${entry.sourceAppName} - \"${entry.messageText.take(40)}\") from the Room database?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMessage(entry.id)
                        showDeleteEntryConfirm = null
                        viewModel.loadDatabaseStats(context)
                        scope.launch {
                            snackbarHostState.showSnackbar("Entry #${entry.id} deleted from database")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Entry")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteEntryConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Vacuum Confirmation Dialog
    if (showVacuumDialog) {
        AlertDialog(
            onDismissRequest = { showVacuumDialog = false },
            title = { Text("Run SQLite VACUUM?") },
            text = { Text("VACUUM cleans up the database file, defragments pages, and frees unused space from deleted message entries.") },
            confirmButton = {
                Button(
                    onClick = {
                        showVacuumDialog = false
                        viewModel.vacuumDatabase(context) {
                            scope.launch {
                                snackbarHostState.showSnackbar("SQLite VACUUM completed successfully")
                            }
                        }
                    }
                ) {
                    Text("Run VACUUM")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVacuumDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear All Dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Rows?") },
            text = { Text("This will execute DELETE FROM messages, wiping all saved entries.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllMessages()
                        showClearAllDialog = false
                        viewModel.loadDatabaseStats(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Table")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InspectorMessageRow(
    message: MessageEntity,
    onInspectRaw: () -> Unit,
    onDeleteEntry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inspector_row_${message.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "#${message.id}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (message.isDeletedBySender) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "DELETED BY SENDER",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (!message.contentAvailable) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "HIDDEN CONTENT",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message.sourceAppName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onInspectRaw,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("inspect_raw_btn_${message.id}")
                    ) {
                        Icon(Icons.Default.Code, contentDescription = "Raw Values", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDeleteEntry,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("delete_entry_btn_${message.id}")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Row", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Sender: ${message.senderName ?: "null"} • Pkg: ${message.sourcePackage}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = message.messageText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun InspectorField(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(text = value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(java.util.Locale.US, "%.2f MB", bytes.toDouble() / (1024 * 1024))
    }
}
