package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.MessageEntity
import com.example.data.model.SupportedAppRegistry
import com.example.ui.components.EmptyState
import com.example.ui.components.MessageCard
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit
) {
    val allMessages by viewModel.allMessages.collectAsState()
    val isPlayingAudio by viewModel.isPlayingAudio.collectAsState()
    val currentAudioPath by viewModel.currentPlayingPath.collectAsState()
    val audioProgress by viewModel.audioProgressMs.collectAsState()
    val audioDuration by viewModel.audioDurationMs.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedAppPackage by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("ALL") } // ALL, PHOTOS, VIDEOS, AUDIO, DELETED, FAVORITES

    val filteredMessages = remember(allMessages, searchQuery, selectedAppPackage, selectedCategory) {
        allMessages.filter { msg ->
            val matchesApp = selectedAppPackage == null || msg.sourcePackage == selectedAppPackage
            val matchesCategory = when (selectedCategory) {
                "PHOTOS" -> msg.messageType == "IMAGE" || msg.attachmentDescription?.contains("Photo") == true
                "VIDEOS" -> msg.messageType == "VIDEO" || msg.attachmentDescription?.contains("Video") == true
                "AUDIO" -> msg.messageType == "AUDIO" || msg.attachmentDescription?.contains("Voice") == true
                "DELETED" -> msg.isDeletedBySender
                "FAVORITES" -> msg.isFavorite
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                (msg.senderName?.contains(searchQuery, ignoreCase = true) == true) ||
                        (msg.messageText.contains(searchQuery, ignoreCase = true)) ||
                        (msg.sourceAppName.contains(searchQuery, ignoreCase = true))
            }

            matchesApp && matchesCategory && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Message Vault",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${filteredMessages.size} messages preserved locally",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search sender or message content...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("search_text_input")
            )

            // Category Filter Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val categories = listOf(
                    "ALL" to "All Messages",
                    "DELETED" to "Unsent / Deleted",
                    "PHOTOS" to "Photos",
                    "AUDIO" to "Voice Notes",
                    "VIDEOS" to "Videos",
                    "FAVORITES" to "Favorites"
                )

                items(categories) { (key, label) ->
                    FilterChip(
                        selected = selectedCategory == key,
                        onClick = { selectedCategory = key },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            // Platform Filter Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedAppPackage == null,
                        onClick = { selectedAppPackage = null },
                        label = { Text("All Platforms", fontSize = 12.sp) }
                    )
                }

                items(SupportedAppRegistry.ALL_SUPPORTED_APPS) { app ->
                    FilterChip(
                        selected = selectedAppPackage == app.packageName,
                        onClick = {
                            selectedAppPackage = if (selectedAppPackage == app.packageName) null else app.packageName
                        },
                        label = { Text(app.displayName, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (filteredMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No matching messages",
                        description = if (searchQuery.isNotBlank()) "No messages match '$searchQuery'" else "No messages found in this filter category."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
}
