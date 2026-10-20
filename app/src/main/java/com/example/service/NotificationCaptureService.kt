package com.example.service

import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.local.AppDatabase
import com.example.data.model.SupportedAppRegistry
import com.example.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationCaptureService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _lastCapturedTime = MutableStateFlow<Long>(0L)
        val lastCapturedTime: StateFlow<Long> = _lastCapturedTime.asStateFlow()

        private val _totalCapturedInSession = MutableStateFlow(0)
        val totalCapturedInSession: StateFlow<Int> = _totalCapturedInSession.asStateFlow()

        fun isNotificationAccessGranted(context: Context): Boolean {
            return try {
                val enabledPackages = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
                if (enabledPackages.contains(context.packageName)) {
                    return true
                }
                val flat = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                )
                flat?.contains(context.packageName) == true
            } catch (e: Exception) {
                val flat = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                )
                flat?.contains(context.packageName) == true
            }
        }

        fun ensureServiceBound(context: Context) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                try {
                    val component = android.content.ComponentName(context, NotificationCaptureService::class.java)
                    requestRebind(component)
                } catch (e: Exception) {
                    try {
                        val pm = context.packageManager
                        val component = android.content.ComponentName(context, NotificationCaptureService::class.java)
                        pm.setComponentEnabledSetting(
                            component,
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        )
                        pm.setComponentEnabledSetting(
                            component,
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        )
                    } catch (ignored: Exception) {}
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isServiceConnected.value = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isServiceConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        serviceScope.launch {
            val displayName = SupportedAppRegistry.getDisplayName(
                packageName = pkg,
                context = applicationContext,
                fallback = try {
                    val ai = packageManager.getApplicationInfo(pkg, 0)
                    packageManager.getApplicationLabel(ai).toString()
                } catch (e: Exception) {
                    pkg
                }
            )

            // Step 1: Record that a notification event was received from Android OS
            DiagnosticTracker.recordEventReceived(pkg, displayName)

            try {
                val prefs = AppPreferences(applicationContext)
                val monitored = prefs.monitoredPackages.first()
                val isGenericEnabled = prefs.isGenericAppsMonitoringEnabled.first()
                val isAllEnabled = prefs.isAllNotificationsMonitoringEnabled.first()

                val isSystemPackage = pkg == "android" ||
                        pkg == "com.android.systemui" ||
                        pkg.startsWith("com.google.android.googlequicksearchbox")

                val notification = sbn.notification
                val extras = notification?.extras
                val isExplicitlyMonitored = monitored.contains(pkg)

                // Generic messaging detection: check category, template, messages bundle, or conversation fields
                val hasMessagingCategory = notification != null && (
                    notification.category == android.app.Notification.CATEGORY_MESSAGE ||
                    notification.category == android.app.Notification.CATEGORY_SOCIAL
                )
                val hasMessagingExtras = extras != null && (
                    extras.containsKey(android.app.Notification.EXTRA_MESSAGES) ||
                    extras.containsKey("android.messages") ||
                    extras.containsKey(android.app.Notification.EXTRA_CONVERSATION_TITLE) ||
                    extras.containsKey("android.messagingStyleUser") ||
                    extras.getString(android.app.Notification.EXTRA_TEMPLATE)?.contains("MessagingStyle") == true ||
                    extras.containsKey(android.app.Notification.EXTRA_TEXT_LINES)
                )
                val isKnownMessagingApp = SupportedAppRegistry.ALL_SUPPORTED_APPS.any { it.packageName == pkg }

                val shouldProcess: Boolean
                val filterReason: String
                when {
                    isSystemPackage -> {
                        shouldProcess = false
                        filterReason = "System package filtered"
                    }
                    isAllEnabled -> {
                        shouldProcess = true
                        filterReason = "Capture all notifications enabled"
                    }
                    isExplicitlyMonitored || isKnownMessagingApp -> {
                        shouldProcess = true
                        filterReason = if (isExplicitlyMonitored) "Monitored package accepted" else "Supported messaging platform accepted"
                    }
                    isGenericEnabled && (hasMessagingCategory || hasMessagingExtras) -> {
                        shouldProcess = true
                        filterReason = "Generic messaging engine matched"
                    }
                    else -> {
                        shouldProcess = false
                        filterReason = "Package unmonitored: not in monitored list"
                    }
                }

                if (!shouldProcess) {
                    DiagnosticTracker.recordFilterRejected(pkg, displayName, filterReason)
                    return@launch
                }

                // Step 2: Record that package was accepted by filter
                DiagnosticTracker.recordFilterAccepted(pkg, displayName)

                // Step 3: Message extraction via NotificationParser
                val parseResult = NotificationParser.parseWithDiagnostics(sbn, displayName)
                val parsedMessages = parseResult.messages

                if (parsedMessages.isEmpty()) {
                    DiagnosticTracker.recordExtractionFailed(
                        pkg,
                        displayName,
                        parseResult.failureReason ?: "No message text or content extracted"
                    )
                    return@launch
                }

                DiagnosticTracker.recordExtractionSuccess(
                    pkg,
                    displayName,
                    parsedMessages.size,
                    parseResult.extractionMethod
                )

                // Step 4: Deduplication and Database insertion
                val db = AppDatabase.getInstance(applicationContext)
                val dao = db.messageDao()
                val now = System.currentTimeMillis()

                for (msg in parsedMessages) {
                    // Check if this notification is an anti-delete / unsend notice from the sender
                    val isDeletionNotice = msg.isDeletedBySender || NotificationParser.isDeletionNotice(msg.messageText)

                    if (isDeletionNotice) {
                        // Look for the most recent message in the database from this sender / package
                        val targetMsg = dao.findLatestMessageForSender(msg.sourcePackage, msg.senderName)
                        if (targetMsg != null) {
                            // Mark original message as deleted by sender, preserving the original captured text!
                            dao.markDeletedBySender(targetMsg.id, now)
                            _lastCapturedTime.value = now
                            DiagnosticTracker.recordInsertionSkipped(
                                pkg,
                                displayName,
                                "Marked previous message as deleted by sender (original text preserved)"
                            )
                        } else {
                            // If MsgVault was not running when the original arrived, save an audit record
                            dao.insertMessage(
                                msg.copy(
                                    messageText = "Message was deleted by sender before MsgVault could capture it.",
                                    contentAvailable = false,
                                    isDeletedBySender = true,
                                    deletedNoticeTimestamp = now
                                )
                            )
                            _lastCapturedTime.value = now
                            _totalCapturedInSession.value += 1
                            DiagnosticTracker.recordInsertionSuccess(pkg, displayName, 1)
                        }
                        continue
                    }

                    // Precise duplicate detection:
                    // 1. Check exact identical notification event from Android OS (prevents duplicate re-posts on screen on/volume change)
                    val exactDuplicate = dao.findExactNotificationDuplicate(
                        sourcePackage = msg.sourcePackage,
                        notificationKey = msg.notificationKey,
                        messageText = msg.messageText,
                        receivedAt = msg.receivedAt
                    )

                    if (exactDuplicate != null) {
                        DiagnosticTracker.recordInsertionSkipped(
                            pkg,
                            displayName,
                            "Duplicate notification event ignored (already saved)"
                        )
                        continue
                    }

                    // 2. Check 300ms jitter window for identical text from the same sender
                    val jitterDuplicate = dao.findRecentDuplicate(
                        sourcePackage = msg.sourcePackage,
                        senderName = msg.senderName,
                        messageText = msg.messageText,
                        minTimestamp = now - 300L
                    )

                    if (jitterDuplicate != null) {
                        DiagnosticTracker.recordInsertionSkipped(
                            pkg,
                            displayName,
                            "Jitter duplicate ignored (received within 300ms)"
                        )
                        continue
                    }

                    val newId = dao.insertMessage(msg)
                    if (newId > 0) {
                        _lastCapturedTime.value = now
                        _totalCapturedInSession.value += 1
                        DiagnosticTracker.recordInsertionSuccess(pkg, displayName, 1)
                    } else {
                        DiagnosticTracker.recordInsertionFailed(
                            pkg,
                            displayName,
                            "Database insertion returned non-positive row ID"
                        )
                    }
                }
            } catch (e: Exception) {
                DiagnosticTracker.recordInsertionFailed(
                    pkg,
                    displayName,
                    "Error in capture service: ${e.javaClass.simpleName}"
                )
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // MANDATE: The app must not delete stored messages when onNotificationRemoved() is called.
        // Notification removal must never automatically remove the corresponding saved message.
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceConnected.value = false
        serviceScope.cancel()
    }
}
