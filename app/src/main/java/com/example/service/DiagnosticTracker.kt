package com.example.service

import com.example.data.model.SupportedAppRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class PackageDiagnosticStatus(
    val packageName: String,
    val appDisplayName: String,
    val notificationReceived: Boolean = false,
    val packageAccepted: Boolean = false,
    val messageExtracted: Boolean = false,
    val databaseInserted: Boolean = false,
    val mediaSaved: Boolean = false,
    val statusReason: String = "No notification events received yet",
    val lastEventTimestamp: Long = 0L,
    val totalEventsReceived: Int = 0,
    val totalMessagesSaved: Int = 0,
    val totalMediaSaved: Int = 0
)

data class DiagnosticLogEntry(
    val id: Long = System.currentTimeMillis(),
    val packageName: String,
    val appDisplayName: String,
    val notificationReceived: Boolean,
    val packageAccepted: Boolean,
    val messageExtracted: Boolean,
    val databaseInserted: Boolean,
    val statusReason: String,
    val timestamp: Long = System.currentTimeMillis()
)

object DiagnosticTracker {

    private val packageMap = ConcurrentHashMap<String, PackageDiagnosticStatus>()
    private val _packageDiagnostics = MutableStateFlow<List<PackageDiagnosticStatus>>(emptyList())
    val packageDiagnostics: StateFlow<List<PackageDiagnosticStatus>> = _packageDiagnostics.asStateFlow()

    private val _recentLogs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val recentLogs: StateFlow<List<DiagnosticLogEntry>> = _recentLogs.asStateFlow()

    init {
        clearDiagnostics()
    }

    private fun updateState() {
        val sorted = packageMap.values.sortedWith(
            compareByDescending<PackageDiagnosticStatus> { it.lastEventTimestamp }
                .thenByDescending { it.totalEventsReceived }
                .thenBy { it.appDisplayName }
        )
        _packageDiagnostics.value = sorted
    }

    fun recordEventReceived(packageName: String, appDisplayName: String) {
        val current = packageMap[packageName] ?: PackageDiagnosticStatus(
            packageName = packageName,
            appDisplayName = appDisplayName
        )
        packageMap[packageName] = current.copy(
            notificationReceived = true,
            totalEventsReceived = current.totalEventsReceived + 1,
            lastEventTimestamp = System.currentTimeMillis(),
            statusReason = "Notification event received from Android OS"
        )
        updateState()
    }

    fun recordFilterRejected(packageName: String, appDisplayName: String, reason: String) {
        val current = packageMap[packageName] ?: PackageDiagnosticStatus(
            packageName = packageName,
            appDisplayName = appDisplayName,
            notificationReceived = true,
            totalEventsReceived = 1,
            lastEventTimestamp = System.currentTimeMillis()
        )
        packageMap[packageName] = current.copy(
            packageAccepted = false,
            messageExtracted = false,
            databaseInserted = false,
            statusReason = "Rejected by filter: $reason",
            lastEventTimestamp = System.currentTimeMillis()
        )
        addLog(
            DiagnosticLogEntry(
                packageName = packageName,
                appDisplayName = appDisplayName,
                notificationReceived = true,
                packageAccepted = false,
                messageExtracted = false,
                databaseInserted = false,
                statusReason = "Filter Rejected: $reason"
            )
        )
        updateState()
    }

    fun recordFilterAccepted(packageName: String, appDisplayName: String) {
        val current = packageMap[packageName] ?: PackageDiagnosticStatus(
            packageName = packageName,
            appDisplayName = appDisplayName,
            notificationReceived = true,
            totalEventsReceived = 1,
            lastEventTimestamp = System.currentTimeMillis()
        )
        packageMap[packageName] = current.copy(
            packageAccepted = true,
            statusReason = "Package accepted by filter, passing to notification parser",
            lastEventTimestamp = System.currentTimeMillis()
        )
        updateState()
    }

    fun recordExtractionFailed(packageName: String, appDisplayName: String, reason: String) {
        val current = packageMap[packageName] ?: PackageDiagnosticStatus(
            packageName = packageName,
            appDisplayName = appDisplayName,
            notificationReceived = true,
            packageAccepted = true,
            lastEventTimestamp = System.currentTimeMillis()
        )
        packageMap[packageName] = current.copy(
            messageExtracted = false,
            databaseInserted = false,
            statusReason = "Extraction failed: $reason",
            lastEventTimestamp = System.currentTimeMillis()
        )
        addLog(
            DiagnosticLogEntry(
                packageName = packageName,
                appDisplayName = appDisplayName,
                notificationReceived = true,
                packageAccepted = true,
                messageExtracted = false,
                databaseInserted = false,
                statusReason = "Extraction Failed: $reason"
            )
        )
        updateState()
    }

    fun recordExtractionSuccess(packageName: String, appDisplayName: String, count: Int, method: String) {
        val current = packageMap[packageName] ?: PackageDiagnosticStatus(
            packageName = packageName,
            appDisplayName = appDisplayName,
            notificationReceived = true,
            packageAccepted = true,
            lastEventTimestamp = System.currentTimeMillis()
        )
        packageMap[packageName] = current.copy(
            messageExtracted = true,
            statusReason = "Successfully extracted $count message(s) via $method",
            lastEventTimestamp = System.currentTimeMillis()
        )
        updateState()
    }

    fun recordInsertionSuccess(packageName: String, appDisplayName: String, count: Int) {
        val current = packageMap[packageName] ?: PackageDiagnosticStatus(
            packageName = packageName,
            appDisplayName = appDisplayName,
            notificationReceived = true,
            packageAccepted = true,
            messageExtracted = true,
            lastEventTimestamp = System.currentTimeMillis()
        )
        packageMap[packageName] = current.copy(
            databaseInserted = true,
            totalMessagesSaved = current.totalMessagesSaved + count,
            statusReason = "Database insertion succeeded (Saved $count record(s))",
            lastEventTimestamp = System.currentTimeMillis()
        )
        addLog(
            DiagnosticLogEntry(
                packageName = packageName,
                appDisplayName = appDisplayName,
                notificationReceived = true,
                packageAccepted = true,
                messageExtracted = true,
                databaseInserted = true,
                statusReason = "Database insertion succeeded ($count message(s))"
            )
        )
        updateState()
    }

    fun recordInsertionSkipped(packageName: String, appDisplayName: String, reason: String) {
        val current = packageMap[packageName] ?: PackageDiagnosticStatus(
            packageName = packageName,
            appDisplayName = appDisplayName,
            notificationReceived = true,
            packageAccepted = true,
            messageExtracted = true,
            lastEventTimestamp = System.currentTimeMillis()
        )
        packageMap[packageName] = current.copy(
            databaseInserted = current.databaseInserted,
            statusReason = reason,
            lastEventTimestamp = System.currentTimeMillis()
        )
        addLog(
            DiagnosticLogEntry(
                packageName = packageName,
                appDisplayName = appDisplayName,
                notificationReceived = true,
                packageAccepted = true,
                messageExtracted = true,
                databaseInserted = current.databaseInserted,
                statusReason = reason
            )
        )
        updateState()
    }

    fun recordInsertionFailed(packageName: String, appDisplayName: String, reason: String) {
        val current = packageMap[packageName] ?: PackageDiagnosticStatus(
            packageName = packageName,
            appDisplayName = appDisplayName,
            notificationReceived = true,
            packageAccepted = true,
            messageExtracted = true,
            lastEventTimestamp = System.currentTimeMillis()
        )
        packageMap[packageName] = current.copy(
            databaseInserted = false,
            statusReason = "Insertion error: $reason",
            lastEventTimestamp = System.currentTimeMillis()
        )
        addLog(
            DiagnosticLogEntry(
                packageName = packageName,
                appDisplayName = appDisplayName,
                notificationReceived = true,
                packageAccepted = true,
                messageExtracted = true,
                databaseInserted = false,
                statusReason = "Database Insertion Failed: $reason"
            )
        )
        updateState()
    }

    fun recordMediaSaved(packageName: String, appDisplayName: String, type: String) {
        val current = packageMap[packageName] ?: PackageDiagnosticStatus(
            packageName = packageName,
            appDisplayName = appDisplayName
        )
        packageMap[packageName] = current.copy(
            mediaSaved = true,
            totalMediaSaved = current.totalMediaSaved + 1,
            statusReason = "Successfully copied and preserved $type in MediaVault",
            lastEventTimestamp = System.currentTimeMillis()
        )
        updateState()
    }

    private fun addLog(entry: DiagnosticLogEntry) {
        val currentList = _recentLogs.value.toMutableList()
        currentList.add(0, entry)
        if (currentList.size > 50) {
            currentList.removeAt(currentList.lastIndex)
        }
        _recentLogs.value = currentList
    }

    fun clearDiagnostics() {
        packageMap.clear()
        SupportedAppRegistry.ALL_SUPPORTED_APPS.forEach { app ->
            packageMap[app.packageName] = PackageDiagnosticStatus(
                packageName = app.packageName,
                appDisplayName = app.displayName,
                notificationReceived = false,
                packageAccepted = false,
                messageExtracted = false,
                databaseInserted = false,
                statusReason = "Awaiting first notification event from Android system"
            )
        }
        _recentLogs.value = emptyList()
        updateState()
    }
}
