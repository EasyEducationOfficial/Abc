package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.MessageEntity
import com.example.data.model.SupportedAppRegistry
import com.example.ui.components.InlineAudioPlayer
import com.example.ui.components.PhotoViewerDialog
import com.example.ui.components.VideoPlayerDialog
import com.example.ui.components.formatFullDateTime
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.AmberFavorite
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RubyError
import com.example.ui.viewmodel.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    messageId: Long,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val allMessages by viewModel.allMessages.collectAsState()
    val message = remember(allMessages, messageId) {
        allMessages.firstOrNull { it.id == messageId }
    }

    val isPlayingAudio by viewModel.isPlayingAudio.collectAsState()
    val currentAudioPath by viewModel.currentPlayingPath.collectAsState()
    val audioProgress by viewModel.audioProgressMs.collectAsState()
    val audioDuration by viewModel.audioDurationMs.collectAsState()

    var showPhotoViewer by remember { mutableStateOf(false) }
    var showVideoPlayer by remember { mutableStateOf(false) }

    if (message == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Message Detail") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Message not found or deleted.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val appInfo = SupportedAppRegistry.getAppByPackage(message.sourcePackage)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Captured Message", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleFavorite(message) },
                        modifier = Modifier.testTag("detail_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (message.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Favorite",
                            tint = if (message.isFavorite) AmberFavorite else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("MsgVault Text", message.messageText))
                            Toast.makeText(context, "Copied message text to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Anti-Delete / Unsent Notice Banner if deleted
            if (message.isDeletedBySender) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = RubyError.copy(alpha = 0.12f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RubyError.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = RubyError,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Deleted by Sender • Saved by MsgVault",
                                fontWeight = FontWeight.Bold,
                                color = RubyError,
                                fontSize = 13.sp
                            )
                            val timeStr = message.deletedNoticeTimestamp?.let { formatRelativeTime(it) } ?: "recently"
                            Text(
                                text = "Sender tried to delete or unsend this message ($timeStr). Original content preserved below.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Field 1 & 2: Platform and Sender Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val brandColor = SupportedAppRegistry.getBrandColor(message.sourcePackage)
                    Surface(
                        shape = CircleShape,
                        color = brandColor,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = (message.senderName ?: message.sourceAppName).take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = message.senderName ?: "Unknown Sender",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = brandColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = message.sourceAppName,
                                    color = brandColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (message.isGroupMessage) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• Group Chat",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Field 3: Message Text Body
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Message Content",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message.messageText,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 24.sp
                    )
                }
            }

            // Linked Actual Media if saved
            if (message.mediaFilePath != null) {
                val mediaFile = File(message.mediaFilePath)
                if (mediaFile.exists()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Saved Original Media File",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = EmeraldSuccess
                                    )
                                }
                                Text(
                                    text = "${mediaFile.length() / 1024} KB",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            when (message.messageType) {
                                "IMAGE" -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { showPhotoViewer = true }
                                    ) {
                                        AsyncImage(
                                            model = mediaFile,
                                            contentDescription = "Saved Image",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Black.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                text = "Tap to enlarge full resolution",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                "AUDIO" -> {
                                    InlineAudioPlayer(
                                        filePath = message.mediaFilePath,
                                        isPlaying = isPlayingAudio && currentAudioPath == message.mediaFilePath,
                                        progressMs = if (currentAudioPath == message.mediaFilePath) audioProgress else 0,
                                        durationMs = if (currentAudioPath == message.mediaFilePath) audioDuration else 0,
                                        onPlay = { viewModel.playAudio(message.mediaFilePath) },
                                        onPause = { viewModel.pauseAudio() },
                                        onSeek = { pos -> viewModel.seekAudio(pos) }
                                    )
                                }
                                "VIDEO" -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { showVideoPlayer = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircle,
                                            contentDescription = "Play Video",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Text(
                                            text = "Tap to Play Saved Video",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Field 4, 5, 6: Metadata Audit Grid
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Capture Audit & Technical Details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Field 4: Date and time received
                    DetailRow(
                        label = "Date & Time Received",
                        value = formatFullDateTime(message.receivedAt)
                    )

                    // Field 5: Message type
                    DetailRow(
                        label = "Identified Message Type",
                        value = message.messageType
                    )

                    // Field 6: Completeness of capture
                    DetailRow(
                        label = "Capture Completeness",
                        value = if (message.isPartialCapture) "Partial Capture" else "Complete Full Capture",
                        valueColor = if (message.isPartialCapture) AmberFavorite else EmeraldSuccess
                    )

                    if (message.isPartialCapture) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AmberFavorite.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Note: Notification preview was partially hidden or content was limited to title/placeholder by system lock screen settings.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    DetailRow(
                        label = "Source Package",
                        value = message.sourcePackage,
                        isMonospace = true
                    )

                    if (!message.conversationTitle.isNullOrBlank()) {
                        DetailRow(
                            label = "Conversation Title",
                            value = message.conversationTitle
                        )
                    }
                }
            }
        }
    }

    if (showPhotoViewer && message.mediaFilePath != null) {
        PhotoViewerDialog(
            filePath = message.mediaFilePath,
            title = "${message.senderName ?: "Photo"} • ${message.sourceAppName}",
            onDismiss = { showPhotoViewer = false }
        )
    }

    if (showVideoPlayer && message.mediaFilePath != null) {
        VideoPlayerDialog(
            filePath = message.mediaFilePath,
            title = "${message.senderName ?: "Video"} • ${message.sourceAppName}",
            onDismiss = { showVideoPlayer = false }
        )
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    isMonospace: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
        )
    }
}
