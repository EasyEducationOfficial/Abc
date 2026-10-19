package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SupportedAppRegistry
import com.example.ui.components.EmptyState
import com.example.ui.components.MessageCard
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SortOrder
import com.example.ui.viewmodel.TimeFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageHistoryScreen(
    viewModel: MainViewModel,
    onNavigateToMessageDetail: (Long) -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.filteredMessages.collectAsStateWithLifecycle()
    val selectedApp by viewModel.selectedFilterApp.collectAsStateWithLifecycle()
    val selectedTime by viewModel.selectedTimeFilter.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedMessageIds.collectAsStateWithLifecycle()
    val isMultiSelect by viewModel.isMultiSelectMode.collectAsStateWithLifecycle()
    val historySearchQuery by viewModel.historySearchQuery.collectAsStateWithLifecycle()

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showBatchDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isMultiSelect) {
                        Text(
                            text = "${selectedIds.size} Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Message History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    if (isMultiSelect) {
                        IconButton(
                            onClick = {
                                if (selectedIds.size == messages.size) {
                                    viewModel.clearSelection()
                                } else {
                                    viewModel.selectAll(messages)
                                }
                            },
                            modifier = Modifier.testTag("select_all_button")
                        ) {
                            Icon(
                                imageVector = if (selectedIds.size == messages.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = "Select or Deselect All"
                            )
                        }
                        IconButton(
                            onClick = { showBatchDeleteConfirmDialog = true },
                            modifier = Modifier.testTag("delete_selected_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.testTag("close_selection_button")
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel Selection")
                        }
                    } else {
                        IconButton(
                            onClick = onNavigateToSearch,
                            modifier = Modifier.testTag("history_search_button")
                        ) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(
                            onClick = {
                                viewModel.sortOrder.value = if (sortOrder == SortOrder.NEWEST_FIRST) {
                                    SortOrder.OLDEST_FIRST
                                } else {
                                    SortOrder.NEWEST_FIRST
                                }
                            },
                            modifier = Modifier.testTag("sort_order_button")
                        ) {
                            Icon(
                                imageVector = if (sortOrder == SortOrder.NEWEST_FIRST) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = "Toggle Sort"
                            )
                        }
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(imageVector = Icons.Outlined.DeleteSweep, contentDescription = "Clear All History")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Search Bar for filtering by sender name or notification content text
            OutlinedTextField(
                value = historySearchQuery,
                onValueChange = { viewModel.historySearchQuery.value = it },
                placeholder = { Text("Filter by sender name or notification text…", fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (historySearchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.historySearchQuery.value = "" },
                            modifier = Modifier.testTag("clear_history_filter_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search filter")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("history_search_bar")
            )

            // App Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedApp == null,
                    onClick = { viewModel.selectedFilterApp.value = null },
                    label = { Text("All Apps") },
                    modifier = Modifier.testTag("filter_chip_all_apps")
                )

                SupportedAppRegistry.ALL_SUPPORTED_APPS.forEach { app ->
                    FilterChip(
                        selected = selectedApp == app.packageName,
                        onClick = {
                            viewModel.selectedFilterApp.value = if (selectedApp == app.packageName) null else app.packageName
                        },
                        label = { Text(app.displayName) },
                        modifier = Modifier.testTag("filter_chip_${app.shortLabel.lowercase()}")
                    )
                }
            }

            // Time Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimeFilter.values().forEach { filter ->
                    FilterChip(
                        selected = selectedTime == filter,
                        onClick = { viewModel.selectedTimeFilter.value = filter },
                        label = { Text(filter.label, fontSize = 12.sp) },
                        modifier = Modifier.testTag("time_chip_${filter.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Messages List or Empty State
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = "No Messages Found",
                        description = if (selectedApp != null || selectedTime != TimeFilter.ALL) {
                            "No saved messages match your current filters. Try changing or clearing filters."
                        } else {
                            "MsgVault has not captured any messages yet. Ensure notification access is active."
                        },
                        actionLabel = if (selectedApp != null || selectedTime != TimeFilter.ALL) "Reset Filters" else null,
                        onActionClick = {
                            viewModel.selectedFilterApp.value = null
                            viewModel.selectedTimeFilter.value = TimeFilter.ALL
                        }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isSelected = selectedIds.contains(msg.id)
                        MessageCard(
                            message = msg,
                            onClick = {
                                if (isMultiSelect) {
                                    viewModel.toggleMessageSelection(msg.id)
                                } else {
                                    onNavigateToMessageDetail(msg.id)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleMessageSelection(msg.id)
                            },
                            onFavoriteToggle = { viewModel.toggleFavorite(msg) },
                            isSelected = isSelected,
                            isMultiSelectMode = isMultiSelect
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Clearing All
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear All History?") },
            text = { Text("This will permanently remove all saved message copies from your local device database. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllMessages()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation Dialog for Deleting Selected
    if (showBatchDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirmDialog = false },
            title = { Text("Delete Selected Messages?") },
            text = { Text("Are you sure you want to delete ${selectedIds.size} selected message(s)?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedMessages()
                        showBatchDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
