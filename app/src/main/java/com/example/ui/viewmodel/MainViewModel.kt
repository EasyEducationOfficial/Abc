package com.example.ui.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MsgVaultApplication
import com.example.R
import com.example.data.local.MessageEntity
import com.example.data.model.SupportedApp
import com.example.data.model.SupportedAppRegistry
import com.example.data.preferences.RetentionPolicy
import com.example.service.NotificationCaptureService
import com.example.service.DiagnosticTracker
import com.example.service.PackageDiagnosticStatus
import com.example.service.DiagnosticLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class TimeFilter(val label: String) {
    ALL("All Time"),
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    WEEK("Last 7 Days")
}

enum class SortOrder(val label: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MsgVaultApplication
    private val repository = app.messageRepository
    private val preferences = app.appPreferences

    val totalSavedCount: StateFlow<Int> = repository.totalCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val capturedTodayCount: StateFlow<Int> = repository.countToday
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val recentMessages: StateFlow<List<MessageEntity>> = repository.recentMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteMessages: StateFlow<List<MessageEntity>> = repository.favoriteMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monitoredPackages: StateFlow<Set<String>> = preferences.monitoredPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val retentionPolicy: StateFlow<RetentionPolicy> = preferences.retentionPolicy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RetentionPolicy.FOREVER)

    val isAppLockEnabled: StateFlow<Boolean> = preferences.isAppLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appLockPin: StateFlow<String?> = preferences.appLockPin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isBiometricsEnabled: StateFlow<Boolean> = preferences.isBiometricsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isOnboardingCompleted: StateFlow<Boolean> = preferences.isOnboardingCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isGenericAppsMonitoringEnabled: StateFlow<Boolean> = preferences.isGenericAppsMonitoringEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isAllNotificationsMonitoringEnabled: StateFlow<Boolean> = preferences.isAllNotificationsMonitoringEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // In-memory unlocked state for the current session
    private val _isAppUnlocked = MutableStateFlow(false)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    // Filtering and sorting state for Messages screen
    val selectedFilterApp = MutableStateFlow<String?>(null)
    val selectedTimeFilter = MutableStateFlow(TimeFilter.ALL)
    val sortOrder = MutableStateFlow(SortOrder.NEWEST_FIRST)

    // Selection mode for batch delete
    private val _selectedMessageIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMessageIds: StateFlow<Set<Long>> = _selectedMessageIds.asStateFlow()

    val isMultiSelectMode: StateFlow<Boolean> = combine(_selectedMessageIds) { ids ->
        ids[0].isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Filter query for top search bar in message list view
    val historySearchQuery = MutableStateFlow("")

    // Filtered messages list
    val filteredMessages: StateFlow<List<MessageEntity>> = combine(
        repository.allMessages,
        selectedFilterApp,
        selectedTimeFilter,
        sortOrder,
        historySearchQuery
    ) { all, appPkg, timeFilter, sort, query ->
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        val yesterdayStart = todayStart - 24L * 60 * 60 * 1000
        val weekStart = todayStart - 7L * 24 * 60 * 60 * 1000

        var list = all

        // Filter by top search bar (sender name, notification content text, conversation title)
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                (it.senderName?.lowercase()?.contains(q) == true) ||
                it.messageText.lowercase().contains(q) ||
                (it.conversationTitle?.lowercase()?.contains(q) == true) ||
                it.sourceAppName.lowercase().contains(q)
            }
        }

        if (appPkg != null) {
            list = list.filter { it.sourcePackage == appPkg }
        }

        list = when (timeFilter) {
            TimeFilter.ALL -> list
            TimeFilter.TODAY -> list.filter { it.receivedAt >= todayStart }
            TimeFilter.YESTERDAY -> list.filter { it.receivedAt in yesterdayStart until todayStart }
            TimeFilter.WEEK -> list.filter { it.receivedAt >= weekStart }
        }

        if (sort == SortOrder.OLDEST_FIRST) {
            list.sortedBy { it.receivedAt }
        } else {
            list.sortedByDescending { it.receivedAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search state
    val searchQuery = MutableStateFlow("")
    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<MessageEntity>> = searchQuery
        .debounce(250)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                repository.searchMessages(query.trim())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Service diagnostics
    val isServiceConnected: StateFlow<Boolean> = NotificationCaptureService.isServiceConnected
    val lastCapturedTime: StateFlow<Long> = NotificationCaptureService.lastCapturedTime
    val totalCapturedInSession: StateFlow<Int> = NotificationCaptureService.totalCapturedInSession

    // Per-package capture pipeline diagnostics
    val packageDiagnostics: StateFlow<List<PackageDiagnosticStatus>> = DiagnosticTracker.packageDiagnostics
    val recentDiagnosticLogs: StateFlow<List<DiagnosticLogEntry>> = DiagnosticTracker.recentLogs

    fun clearDiagnostics() {
        DiagnosticTracker.clearDiagnostics()
    }

    fun checkNotificationAccess(): Boolean {
        return NotificationCaptureService.isNotificationAccessGranted(getApplication())
    }

    fun openNotificationAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun toggleFavorite(message: MessageEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(message.id, message.isFavorite)
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
            _selectedMessageIds.value = _selectedMessageIds.value - id
        }
    }

    fun deleteSelectedMessages() {
        val ids = _selectedMessageIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteByIds(ids)
            _selectedMessageIds.value = emptySet()
        }
    }

    fun clearAllMessages() {
        viewModelScope.launch {
            repository.deleteAll()
            _selectedMessageIds.value = emptySet()
        }
    }

    fun toggleMessageSelection(id: Long) {
        val current = _selectedMessageIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedMessageIds.value = current
    }

    fun selectAll(messages: List<MessageEntity>) {
        _selectedMessageIds.value = messages.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedMessageIds.value = emptySet()
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

    fun toggleGenericAppsMonitoring(enable: Boolean) {
        viewModelScope.launch {
            preferences.setGenericAppsMonitoring(enable)
        }
    }

    fun toggleAllNotificationsMonitoring(enable: Boolean) {
        viewModelScope.launch {
            preferences.setAllNotificationsMonitoring(enable)
        }
    }

    fun setRetentionPolicy(policy: RetentionPolicy) {
        viewModelScope.launch {
            preferences.setRetentionPolicy(policy)
            repository.applyRetentionPolicy(policy)
        }
    }

    fun setAppLock(enabled: Boolean, pin: String?) {
        viewModelScope.launch {
            preferences.setAppLock(enabled, pin)
            if (enabled) {
                _isAppUnlocked.value = true
            }
        }
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setBiometricsEnabled(enabled)
        }
    }

    fun unlockApp() {
        _isAppUnlocked.value = true
    }

    fun lockApp() {
        _isAppUnlocked.value = false
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
        }
    }

    fun getMessageFlowById(id: Long) = repository.getMessageById(id)

    fun sendTestNotification(context: Context, customText: String? = null) {
        val channelId = "msgvault_test_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "MsgVault Verification",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for verifying notification message capture"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val testMessage = customText ?: "MsgVault test: This message will remain saved even if deleted!"
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Alex Vance (Demo)")
            .setContentText(testMessage)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(testMessage)
                    .setBigContentTitle("Alex Vance (Demo)")
                    .setSummaryText("Test Chat")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationCaptureService.ensureServiceBound(context)
            notificationManager.notify(7788, notification)
        } catch (e: Exception) {
            // Gracefully handle permission denials on Android 13+
        }
    }

    fun simulateDeletionNotification(context: Context) {
        val channelId = "msgvault_test_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Alex Vance (Demo)")
            .setContentText("This message was deleted")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("This message was deleted")
                    .setBigContentTitle("Alex Vance (Demo)")
                    .setSummaryText("Test Chat")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationCaptureService.ensureServiceBound(context)
            notificationManager.notify(7788, notification)
        } catch (e: Exception) {
            // Gracefully handle permission denials
        }
    }

    fun seedSampleDemoMessages() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val samples = listOf(
                MessageEntity(
                    sourcePackage = "com.whatsapp",
                    sourceAppName = "WhatsApp",
                    senderName = "Sarah Jenkins",
                    conversationTitle = "Project Sync",
                    messageText = "Hey! Don't forget about our sync at 3 PM today. (Sender unsent this message)",
                    receivedAt = now - 1000L * 60 * 15,
                    capturedAt = now - 1000L * 60 * 15,
                    isGroupMessage = true,
                    isFavorite = true
                ),
                MessageEntity(
                    sourcePackage = "org.telegram.messenger",
                    sourceAppName = "Telegram",
                    senderName = "David Miller",
                    conversationTitle = null,
                    messageText = "Here are the secret access credentials: Vault_982_AuthKey. Keep this safe!",
                    receivedAt = now - 1000L * 60 * 45,
                    capturedAt = now - 1000L * 60 * 45,
                    isGroupMessage = false,
                    isFavorite = false
                ),
                MessageEntity(
                    sourcePackage = "com.instagram.android",
                    sourceAppName = "Instagram",
                    senderName = "maya_photo",
                    conversationTitle = null,
                    messageText = "I sent you the unreleased photo shoot draft! [Photo attachment]",
                    receivedAt = now - 1000L * 60 * 120,
                    capturedAt = now - 1000L * 60 * 120,
                    isGroupMessage = false,
                    attachmentDescription = "Photo",
                    isFavorite = false
                ),
                MessageEntity(
                    sourcePackage = "com.facebook.orca",
                    sourceAppName = "Messenger",
                    senderName = "Family Group",
                    conversationTitle = "Weekend Dinner",
                    messageText = "Grandma said she's bringing the homemade dessert! Can't wait to see everyone.",
                    receivedAt = now - 1000L * 60 * 60 * 5,
                    capturedAt = now - 1000L * 60 * 60 * 5,
                    isGroupMessage = true,
                    isFavorite = true
                )
            )
            for (s in samples) {
                repository.insertMessage(s)
            }
        }
    }

    // Database Inspector State
    val databaseStats = MutableStateFlow<com.example.data.model.DatabaseStats?>(null)
    val isInspectorLoading = MutableStateFlow(false)

    fun loadDatabaseStats(context: Context) {
        viewModelScope.launch {
            isInspectorLoading.value = true
            databaseStats.value = repository.getDatabaseStats(context)
            isInspectorLoading.value = false
        }
    }

    fun vacuumDatabase(context: Context, onComplete: () -> Unit) {
        viewModelScope.launch {
            isInspectorLoading.value = true
            repository.vacuumDatabase(context)
            databaseStats.value = repository.getDatabaseStats(context)
            isInspectorLoading.value = false
            onComplete()
        }
    }

    fun exportDatabaseToJson(context: Context, onResult: (java.io.File?) -> Unit) {
        viewModelScope.launch {
            try {
                val file = repository.exportMessagesToJson(context)
                onResult(file)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun shareExportedFile(context: Context, file: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "MsgVault Database Backup")
                putExtra(android.content.Intent.EXTRA_TEXT, "MsgVault local JSON backup: ${file.name}")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = android.content.Intent.createChooser(intent, "Share MsgVault Backup")
            chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, "MsgVault Backup")
                putExtra(android.content.Intent.EXTRA_TEXT, file.readText())
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = android.content.Intent.createChooser(intent, "Share MsgVault Backup Text")
            chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }
}
