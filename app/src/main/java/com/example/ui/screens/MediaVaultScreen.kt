package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.MediaVaultEntity
import com.example.data.model.SupportedAppRegistry
import com.example.service.MediaObserverManager
import com.example.ui.components.EmptyState
import com.example.ui.components.InlineAudioPlayer
import com.example.ui.components.PhotoViewerDialog
import com.example.ui.components.VideoPlayerDialog
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.AmberFavorite
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.viewmodel.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaVaultScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    val context = LocalContext.current
    val allMedia by viewModel.allMedia.collectAsState()
    val isMediaObserverActive by viewModel.isMediaObserverActive.collectAsState()
    val totalMediaPreserved by viewModel.totalMediaPreserved.collectAsState()
    val safUriStr by viewModel.safMediaTreeUri.collectAsState()

    val isPlayingAudio by viewModel.isPlayingAudio.collectAsState()
    val currentAudioPath by viewModel.currentPlayingPath.collectAsState()
    val audioProgress by viewModel.audioProgressMs.collectAsState()
    val audioDuration by viewModel.audioDurationMs.collectAsState()

    var selectedType by remember { mutableStateOf("ALL") }

    var viewingPhotoPath by remember { mutableStateOf<String?>(null) }
    var viewingPhotoTitle by remember { mutableStateOf<String>("") }
    var viewingVideoPath by remember { mutableStateOf<String?>(null) }
    var viewingVideoTitle by remember { mutableStateOf<String>("") }

    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (ignored: Exception) {}
            viewModel.saveSafTreeUri(uri)
        }
    }

    val filteredMedia = remember(allMedia, selectedType) {
        if (selectedType == "ALL") allMedia
        else allMedia.filter { it.mediaType == selectedType }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Media Vault",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${allMedia.size} original media files preserved",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            safFolderLauncher.launch(null)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "Select Media Folder",
                            tint = MaterialTheme.colorScheme.primary
                        )
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
            // Media Observer Status Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = if (isMediaObserverActive) EmeraldSuccess else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isMediaObserverActive) "Storage Media Observer Active" else "Storage Observer Idle",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isMediaObserverActive) EmeraldSuccess else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (!safUriStr.isNullOrBlank()) "SAF Scoped Folder Connected" else "Monitoring standard WhatsApp/Messenger folders",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            MediaObserverManager.startObserving(context)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Rescan", fontSize = 11.sp)
                    }
                }
            }

            // Media Type Filter Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val types = listOf(
                    "ALL" to "All Media",
                    "IMAGE" to "Photos",
                    "AUDIO" to "Voice Notes",
                    "VIDEO" to "Videos",
                    "DOCUMENT" to "Documents"
                )

                items(types) { (key, label) ->
                    FilterChip(
                        selected = selectedType == key,
                        onClick = { selectedType = key },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            if (filteredMedia.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.PermMedia,
                        title = "No saved media files yet",
                        description = "Incoming photos, voice notes, and videos sent to WhatsApp, Messenger, etc. are automatically copied and stored permanently in MsgVault.",
                        actionLabel = "Select WhatsApp Media Folder",
                        onActionClick = { safFolderLauncher.launch(null) }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredMedia, key = { it.id }) { media ->
                        MediaVaultCard(
                            media = media,
                            onClick = {
                                when (media.mediaType) {
                                    "IMAGE" -> {
                                        viewingPhotoPath = media.localFilePath
                                        viewingPhotoTitle = media.originalFileName
                                    }
                                    "VIDEO" -> {
                                        viewingVideoPath = media.localFilePath
                                        viewingVideoTitle = media.originalFileName
                                    }
                                    "AUDIO" -> {
                                        if (isPlayingAudio && currentAudioPath == media.localFilePath) {
                                            viewModel.pauseAudio()
                                        } else {
                                            viewModel.playAudio(media.localFilePath)
                                        }
                                    }
                                }
                            },
                            onFavoriteToggle = { viewModel.toggleFavoriteMedia(media) },
                            isPlayingAudio = isPlayingAudio && currentAudioPath == media.localFilePath,
                            audioProgressMs = if (currentAudioPath == media.localFilePath) audioProgress else 0,
                            audioDurationMs = if (currentAudioPath == media.localFilePath) audioDuration else 0,
                            onPlayAudio = { path: String -> viewModel.playAudio(path) },
                            onPauseAudio = { viewModel.pauseAudio() },
                            onSeekAudio = { pos: Int -> viewModel.seekAudio(pos) }
                        )
                    }
                }
            }
        }
    }

    if (viewingPhotoPath != null) {
        PhotoViewerDialog(
            filePath = viewingPhotoPath!!,
            title = viewingPhotoTitle,
            onDismiss = { viewingPhotoPath = null }
        )
    }

    if (viewingVideoPath != null) {
        VideoPlayerDialog(
            filePath = viewingVideoPath!!,
            title = viewingVideoTitle,
            onDismiss = { viewingVideoPath = null }
        )
    }
}

@Composable
fun MediaVaultCard(
    media: MediaVaultEntity,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    isPlayingAudio: Boolean = false,
    audioProgressMs: Int = 0,
    audioDurationMs: Int = 0,
    onPlayAudio: ((String) -> Unit)? = null,
    onPauseAudio: (() -> Unit)? = null,
    onSeekAudio: ((Int) -> Unit)? = null
) {
    val file = remember(media.localFilePath) { File(media.localFilePath) }
    val brandColor = SupportedAppRegistry.getBrandColor(media.sourcePackage)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("media_card_${media.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Platform, Sender, Favorite, Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = brandColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = media.sourceAppName,
                            color = brandColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (!media.senderName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${media.senderName}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatRelativeTime(media.capturedAt),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (media.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Favorite",
                            tint = if (media.isFavorite) AmberFavorite else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body preview depending on mediaType
            when (media.mediaType) {
                "IMAGE" -> {
                    if (file.exists()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                        ) {
                            AsyncImage(
                                model = file,
                                contentDescription = media.originalFileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                "AUDIO" -> {
                    if (onPlayAudio != null && onPauseAudio != null && onSeekAudio != null) {
                        InlineAudioPlayer(
                            filePath = media.localFilePath,
                            isPlaying = isPlayingAudio,
                            progressMs = audioProgressMs,
                            durationMs = audioDurationMs,
                            onPlay = { onPlayAudio(media.localFilePath) },
                            onPause = onPauseAudio,
                            onSeek = onSeekAudio
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                "VIDEO" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Play Video",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Footer: File name and file size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = media.originalFileName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${media.fileSizeBytes / 1024} KB",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
