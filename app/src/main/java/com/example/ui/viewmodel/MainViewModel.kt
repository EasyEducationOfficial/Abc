package com.example.ui.viewmodel

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.data.local.AppDatabase
import com.example.data.local.MediaVaultEntity
import com.example.data.local.MessageEntity
import com.example.data.model.SupportedApp
import com.example.data.model.SupportedAppRegistry
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.RetentionPolicy
import com.example.repository.MessageRepository
import com.example.service.DiagnosticTracker
import com.example.service.MediaObserverManager
import com.example.service.MediaObserverService
import com.example.service.NotificationCaptureService
import com.example.service.PackageDiagnosticStatus
import com.example.service.DiagnosticLogEntry
import com.example.util.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainViewModel(
    private val context: Context,
    private val repository: MessageRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    val allMessages = repository.allMessages.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val recentMessages = repository.getRecentMessages(20).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val favorites = repository.favorites.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val totalCount = repository.totalCount.stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val distinctApps = repository.distinctApps.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Media Vault Flows
    val allMedia = repository.allMedia.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val favoriteMedia = repository.favoriteMedia.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val totalMediaCount = repository.totalMediaCount.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val monitoredPackages = preferences.monitoredPackages.stateIn(viewModelScope, SharingStarted.Lazily, AppPreferences.DEFAULT_MONITORED_PACKAGES)
    val retentionPolicy = preferences.retentionPolicy.stateIn(viewModelScope, SharingStarted.Lazily, RetentionPolicy.FOREVER)
    val isAppLockEnabled = preferences.isAppLockEnabled.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val appLockPin = preferences.appLockPin.stateIn(viewModelScope, SharingStarted.Lazily, null)
    val isBiometricsEnabled = preferences.isBiometricsEnabled.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val isOnboardingCompleted = preferences.isOnboardingCompleted.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val isPermissionWizardCompleted = preferences.isPermissionWizardCompleted.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val isGenericAppsMonitoringEnabled = preferences.isGenericAppsMonitoringEnabled.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val isAllNotificationsMonitoringEnabled = preferences.isAllNotificationsMonitoringEnabled.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val safMediaTreeUri = preferences.safMediaTreeUri.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val isServiceConnected: StateFlow<Boolean> = NotificationCaptureService.isServiceConnected
    val lastCapturedTime: StateFlow<Long> = NotificationCaptureService.lastCapturedTime
    val totalCapturedInSession: StateFlow<Int> = NotificationCaptureService.totalCapturedInSession
    val packageDiagnostics: StateFlow<List<PackageDiagnosticStatus>> = DiagnosticTracker.packageDiagnostics
    val recentDiagnosticLogs: StateFlow<List<DiagnosticLogEntry>> = DiagnosticTracker.recentLogs

    val isMediaObserverActive: StateFlow<Boolean> = MediaObserverManager.isObserving
    val totalMediaPreserved: StateFlow<Int> = MediaObserverManager.totalMediaPreserved
    val lastPreservedMedia: StateFlow<MediaVaultEntity?> = MediaObserverManager.lastPreservedMedia

    // Live permission states
    private val _isNotificationAccessGranted = MutableStateFlow(PermissionManager.checkNotificationAccess(context))
    val isNotificationAccessGranted: StateFlow<Boolean> = _isNotificationAccessGranted.asStateFlow()

    private val _isNotificationPermissionGranted = MutableStateFlow(PermissionManager.checkNotificationPermission(context))
    val isNotificationPermissionGranted: StateFlow<Boolean> = _isNotificationPermissionGranted.asStateFlow()

    private val _isMediaPermissionGranted = MutableStateFlow(PermissionManager.checkMediaPermissions(context))
    val isMediaPermissionGranted: StateFlow<Boolean> = _isMediaPermissionGranted.asStateFlow()

    private val _isBatteryExemptionGranted = MutableStateFlow(PermissionManager.checkBatteryOptimizationExemption(context))
    val isBatteryExemptionGranted: StateFlow<Boolean> = _isBatteryExemptionGranted.asStateFlow()

    // In-App Audio Player State
    private var mediaPlayer: MediaPlayer? = null
    private val _currentPlayingPath = MutableStateFlow<String?>(null)
    val currentPlayingPath: StateFlow<String?> = _currentPlayingPath.asStateFlow()

    private val _isPlayingAudio = MutableStateFlow(false)
    val isPlayingAudio: StateFlow<Boolean> = _isPlayingAudio.asStateFlow()

    private val _audioProgressMs = MutableStateFlow(0)
    val audioProgressMs: StateFlow<Int> = _audioProgressMs.asStateFlow()

    private val _audioDurationMs = MutableStateFlow(0)
    val audioDurationMs: StateFlow<Int> = _audioDurationMs.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    _audioProgressMs.value = player.currentPosition
                    handler.postDelayed(this, 100)
                }
            }
        }
    }

    init {
        refreshPermissionStatuses()
    }

    fun refreshPermissionStatuses() {
        _isNotificationAccessGranted.value = PermissionManager.checkNotificationAccess(context)
        _isNotificationPermissionGranted.value = PermissionManager.checkNotificationPermission(context)
        _isMediaPermissionGranted.value = PermissionManager.checkMediaPermissions(context)
        _isBatteryExemptionGranted.value = PermissionManager.checkBatteryOptimizationExemption(context)
    }

    fun openNotificationAccessSettings(ctx: Context) {
        PermissionManager.openNotificationListenerSettings(ctx)
    }

    fun openNotificationPermissionSettings(ctx: Context) {
        PermissionManager.openAppNotificationSettings(ctx)
    }

    fun openAppDetailsSettings(ctx: Context) {
        PermissionManager.openAppDetailsSettings(ctx)
    }

    fun requestBatteryOptimization(ctx: Context) {
        PermissionManager.requestBatteryOptimizationExemption(ctx)
    }

    fun saveSafTreeUri(uri: Uri?) {
        viewModelScope.launch {
            preferences.setSafMediaTreeUri(uri?.toString())
            if (uri != null) {
                MediaObserverManager.scanSafDocumentTree(context, uri)
            }
        }
    }

    fun completePermissionWizard() {
        viewModelScope.launch {
            preferences.setPermissionWizardCompleted(true)
            preferences.setOnboardingCompleted(true)
            MediaObserverService.startService(context)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
        }
    }

    fun toggleAppMonitoring(packageName: String, enable: Boolean) {
        viewModelScope.launch {
            preferences.toggleAppMonitoring(packageName, enable)
        }
    }

    fun setAllAppsMonitoring(enable: Boolean) {
        viewModelScope.launch {
            preferences.setAllAppsMonitoring(enable)
        }
    }

    fun setRetentionPolicy(policy: RetentionPolicy) {
        viewModelScope.launch {
            preferences.setRetentionPolicy(policy)
        }
    }

    fun toggleFavorite(message: MessageEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(message)
        }
    }

    fun toggleFavoriteMedia(media: MediaVaultEntity) {
        viewModelScope.launch {
            repository.toggleFavoriteMedia(media)
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            repository.deleteMessage(id)
        }
    }

    fun deleteMedia(media: MediaVaultEntity) {
        viewModelScope.launch {
            repository.deleteMedia(media)
        }
    }

    fun clearAllMessages() {
        viewModelScope.launch {
            repository.clearAllMessages()
        }
    }

    fun clearAllMedia() {
        viewModelScope.launch {
            repository.clearAllMedia()
        }
    }

    fun clearDiagnostics() {
        DiagnosticTracker.clearDiagnostics()
    }

    fun getMessageFlowById(id: Long) = repository.getMessageById(id)

    // Audio Playback Controls
    fun playAudio(filePath: String) {
        if (_currentPlayingPath.value == filePath && mediaPlayer != null) {
            mediaPlayer?.start()
            _isPlayingAudio.value = true
            handler.post(updateProgressRunnable)
            return
        }

        stopAudio()
        try {
            val player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    _isPlayingAudio.value = false
                    _audioProgressMs.value = 0
                }
            }
            mediaPlayer = player
            player.start()
            _currentPlayingPath.value = filePath
            _isPlayingAudio.value = true
            _audioDurationMs.value = player.duration
            handler.post(updateProgressRunnable)
        } catch (e: Exception) {
            stopAudio()
        }
    }

    fun pauseAudio() {
        mediaPlayer?.pause()
        _isPlayingAudio.value = false
        handler.removeCallbacks(updateProgressRunnable)
    }

    fun seekAudio(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        _audioProgressMs.value = positionMs
    }

    fun stopAudio() {
        handler.removeCallbacks(updateProgressRunnable)
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (ignored: Exception) {}
        mediaPlayer = null
        _isPlayingAudio.value = false
        _currentPlayingPath.value = null
        _audioProgressMs.value = 0
    }

    // Real Notification Dispatch for Verification
    fun sendTestNotification(ctx: Context, messageText: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(ctx, "msgvault_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Test Sender")
            .setContentText(messageText)
            .setSubText("MsgVault Verification")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(1001, notification)
    }

    fun simulateDeletionNotification(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(ctx, "msgvault_alerts")
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle("Test Sender")
            .setContentText("This message was deleted")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(1002, notification)
    }

    fun sendSimulatedTestNotification(packageName: String, appName: String) {
        sendTestNotification(context, "Test notification from $appName (MsgVault Verification)")
    }

    fun seedSampleDemoMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()

            // Create sample media files in internal storage
            val sampleImage = File(MediaObserverManager.getInternalMediaDir(context, "IMAGE"), "demo_sunset.jpg")
            if (!sampleImage.exists()) {
                FileOutputStream(sampleImage).use { out ->
                    out.write("DEMO_IMAGE_DATA".toByteArray())
                }
            }

            val sampleAudio = File(MediaObserverManager.getInternalMediaDir(context, "AUDIO"), "demo_voice.opus")
            if (!sampleAudio.exists()) {
                FileOutputStream(sampleAudio).use { out ->
                    out.write("DEMO_AUDIO_DATA".toByteArray())
                }
            }

            val demoMessages = listOf(
                MessageEntity(
                    sourcePackage = "com.whatsapp",
                    sourceAppName = "WhatsApp",
                    senderName = "Alex Rivera",
                    messageText = "Hey! Did you check out the photos from the team outing?",
                    receivedAt = now - 180_000L,
                    mediaFilePath = sampleImage.absolutePath,
                    mediaMimeType = "image/jpeg",
                    messageType = "IMAGE",
                    attachmentDescription = "Photo"
                ),
                MessageEntity(
                    sourcePackage = "com.whatsapp",
                    sourceAppName = "WhatsApp",
                    senderName = "Alex Rivera",
                    messageText = "🎙️ Voice message (0:14)",
                    receivedAt = now - 120_000L,
                    mediaFilePath = sampleAudio.absolutePath,
                    mediaMimeType = "audio/opus",
                    messageType = "AUDIO",
                    attachmentDescription = "Voice Message"
                ),
                MessageEntity(
                    sourcePackage = "com.whatsapp",
                    sourceAppName = "WhatsApp",
                    senderName = "Alex Rivera",
                    messageText = "I made a reservation for 7:30 PM tomorrow.",
                    receivedAt = now - 60_000L,
                    isDeletedBySender = true,
                    deletedNoticeTimestamp = now - 10_000L,
                    messageType = "TEXT"
                ),
                MessageEntity(
                    sourcePackage = "com.facebook.orca",
                    sourceAppName = "Messenger",
                    senderName = "Sarah Jenkins",
                    messageText = "Can you send the financial report spreadsheet?",
                    receivedAt = now - 300_000L,
                    messageType = "DOCUMENT",
                    attachmentDescription = "Document"
                ),
                MessageEntity(
                    sourcePackage = "com.instagram.android",
                    sourceAppName = "Instagram",
                    senderName = "design_studio_nyc",
                    messageText = "Loved your portfolio! Let's collaborate on the upcoming project.",
                    receivedAt = now - 500_000L,
                    messageType = "TEXT"
                ),
                MessageEntity(
                    sourcePackage = "com.imo.android.imoim",
                    sourceAppName = "imo",
                    senderName = "Uncle Tariq",
                    messageText = "Call me back when you get a chance.",
                    receivedAt = now - 700_000L,
                    messageType = "TEXT"
                ),
                MessageEntity(
                    sourcePackage = "org.telegram.messenger",
                    sourceAppName = "Telegram",
                    senderName = "Crypto Signals Club",
                    messageText = "Market update: BTC breaks key resistance level.",
                    receivedAt = now - 900_000L,
                    messageType = "TEXT"
                )
            )

            for (msg in demoMessages) {
                repository.insertMessage(msg)
            }

            // Also seed into media vault
            repository.insertMedia(
                MediaVaultEntity(
                    originalFileName = "demo_sunset.jpg",
                    localFilePath = sampleImage.absolutePath,
                    mediaType = "IMAGE",
                    mimeType = "image/jpeg",
                    fileSizeBytes = 425_000L,
                    sourcePackage = "com.whatsapp",
                    sourceAppName = "WhatsApp",
                    senderName = "Alex Rivera",
                    capturedAt = now - 180_000L
                )
            )

            repository.insertMedia(
                MediaVaultEntity(
                    originalFileName = "demo_voice.opus",
                    localFilePath = sampleAudio.absolutePath,
                    mediaType = "AUDIO",
                    mimeType = "audio/opus",
                    fileSizeBytes = 38_000L,
                    sourcePackage = "com.whatsapp",
                    sourceAppName = "WhatsApp",
                    senderName = "Alex Rivera",
                    capturedAt = now - 120_000L,
                    durationMs = 14_000L
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}
