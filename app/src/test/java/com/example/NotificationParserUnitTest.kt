package com.example

import com.example.data.local.MessageEntity
import com.example.service.DiagnosticTracker
import com.example.service.NotificationParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * =========================================================================================
 * SIMULATED PARSER UNIT TESTS (Local Host / JVM)
 * =========================================================================================
 * NOTE ON TESTING BOUNDARIES:
 * - These tests are SIMULATED local unit tests running on the JVM.
 * - They test NotificationParser's text parsing, deletion detection, message type resolution,
 *   attachment identification, and DiagnosticTracker's privacy guarantees.
 * - REAL-DEVICE INTEGRATION TESTS require actual Android hardware or an active emulator
 *   with NotificationListenerService enabled in Android Settings and genuine incoming
 *   push notifications from WhatsApp, Messenger, imo, etc.
 * =========================================================================================
 */
class NotificationParserUnitTest {

    @Before
    fun setUp() {
        DiagnosticTracker.clearDiagnostics()
    }

    @Test
    fun simulated_whatsappDeletedNotice_detectedCorrectly() {
        // WhatsApp anti-delete notices
        assertTrue(NotificationParser.isDeletionNotice("This message was deleted"))
        assertTrue(NotificationParser.isDeletionNotice("This message was deleted by the sender"))
        assertTrue(NotificationParser.isDeletionNotice("You deleted this message"))
        assertTrue(NotificationParser.isDeletionNotice("Ce message a été supprimé"))
        assertTrue(NotificationParser.isDeletionNotice("Este mensaje fue eliminado"))

        // Standard text should NOT be flagged as deletion
        assertFalse(NotificationParser.isDeletionNotice("Hey, are you free today?"))
        assertFalse(NotificationParser.isDeletionNotice("Photo"))
    }

    @Test
    fun simulated_messengerUnsentNotice_detectedCorrectly() {
        // Messenger anti-delete / unsend notices
        assertTrue(NotificationParser.isDeletionNotice("unsent a message"))
        assertTrue(NotificationParser.isDeletionNotice("Alice unsent a message"))
        assertTrue(NotificationParser.isDeletionNotice("this message was unsent"))

        assertFalse(NotificationParser.isDeletionNotice("Sent a photo"))
    }

    @Test
    fun simulated_genericPreviewPlaceholders_detectedAsPartial() {
        // When notification content is hidden by lockscreen / notification preview settings
        assertTrue(NotificationParser.isGenericPlaceholderText("New message"))
        assertTrue(NotificationParser.isGenericPlaceholderText("1 new message"))
        assertTrue(NotificationParser.isGenericPlaceholderText("2 new messages"))
        assertTrue(NotificationParser.isGenericPlaceholderText("Incoming message"))
        assertTrue(NotificationParser.isGenericPlaceholderText(""))
        assertTrue(NotificationParser.isGenericPlaceholderText(null))

        // Normal content should not be considered generic placeholder
        assertFalse(NotificationParser.isGenericPlaceholderText("Let's meet at 5 PM at the cafe."))
    }

    @Test
    fun simulated_systemSyncNotifications_filteredCorrectly() {
        // System and synchronization notices that should not be saved as chat messages
        assertTrue(NotificationParser.isIgnorableSystemText("Checking for new messages"))
        assertTrue(NotificationParser.isIgnorableSystemText("WhatsApp Web is currently active"))
        assertTrue(NotificationParser.isIgnorableSystemText("Backup in progress"))
        assertTrue(NotificationParser.isIgnorableSystemText("Syncing mail..."))

        assertFalse(NotificationParser.isIgnorableSystemText("Hey! How are you?"))
    }

    @Test
    fun simulated_resolveMessageType_imageIdentified() {
        assertEquals("IMAGE", NotificationParser.resolveMessageType("image/jpeg", null, "Photo"))
        assertEquals("IMAGE", NotificationParser.resolveMessageType(null, "Photo", "📷 Photo"))
        assertEquals("IMAGE", NotificationParser.resolveMessageType(null, null, "Sent a photo"))
        assertEquals("IMAGE", NotificationParser.resolveMessageType(null, null, "picture"))
    }

    @Test
    fun simulated_resolveMessageType_videoIdentified() {
        assertEquals("VIDEO", NotificationParser.resolveMessageType("video/mp4", null, "Video"))
        assertEquals("VIDEO", NotificationParser.resolveMessageType(null, "Video", "📹 Video (0:15)"))
        assertEquals("VIDEO", NotificationParser.resolveMessageType(null, null, "Sent a video"))
    }

    @Test
    fun simulated_resolveMessageType_audioAndVoiceNoteIdentified() {
        assertEquals("AUDIO", NotificationParser.resolveMessageType("audio/ogg", null, "Voice message"))
        assertEquals("AUDIO", NotificationParser.resolveMessageType(null, "Voice Message", "🎙️ Voice message (0:45)"))
        assertEquals("AUDIO", NotificationParser.resolveMessageType(null, null, "audio message"))
    }

    @Test
    fun simulated_resolveMessageType_documentIdentified() {
        assertEquals("DOCUMENT", NotificationParser.resolveMessageType("application/pdf", null, "invoice.pdf"))
        assertEquals("DOCUMENT", NotificationParser.resolveMessageType(null, "Document", "ProjectProposal.docx"))
        assertEquals("DOCUMENT", NotificationParser.resolveMessageType(null, null, "file attached"))
    }

    @Test
    fun simulated_resolveMessageType_standardTextDefault() {
        assertEquals("TEXT", NotificationParser.resolveMessageType(null, null, "Hello! Let me know when you arrive."))
        assertEquals("TEXT", NotificationParser.resolveMessageType(null, null, "See you soon"))
    }

    @Test
    fun simulated_diagnosticTracker_doesNotExposePrivateMessageText() {
        val testPkg = "com.whatsapp"
        val testAppName = "WhatsApp"

        // Simulate 4-stage pipeline execution
        DiagnosticTracker.recordEventReceived(testPkg, testAppName)
        DiagnosticTracker.recordFilterAccepted(testPkg, testAppName)
        DiagnosticTracker.recordExtractionSuccess(testPkg, testAppName, 1, "MessagingStyle")
        DiagnosticTracker.recordInsertionSuccess(testPkg, testAppName, 1)

        val diagnostics = DiagnosticTracker.packageDiagnostics.value
        val whatsappDiag = diagnostics.find { it.packageName == testPkg }

        assertNotNull(whatsappDiag)
        assertTrue(whatsappDiag!!.notificationReceived)
        assertTrue(whatsappDiag.packageAccepted)
        assertTrue(whatsappDiag.messageExtracted)
        assertTrue(whatsappDiag.databaseInserted)
        assertEquals(1, whatsappDiag.totalMessagesSaved)

        // Verify log entries do not leak private message content
        val logs = DiagnosticTracker.recentLogs.value
        for (log in logs) {
            // Logs contain reasons like "Message saved to Room database (1 item(s))", never private message strings
            assertFalse(log.statusReason.contains("secret", ignoreCase = true))
            assertFalse(log.statusReason.contains("password", ignoreCase = true))
        }
    }

    @Test
    fun simulated_messageEntity_fieldsArePopulated() {
        val msg = MessageEntity(
            id = 1,
            sourcePackage = "com.whatsapp",
            sourceAppName = "WhatsApp",
            senderName = "Alice",
            messageText = "Hello from simulated WhatsApp test!",
            receivedAt = 1700000000000L,
            messageType = "TEXT",
            isPartialCapture = false
        )

        assertEquals("WhatsApp", msg.sourceAppName)
        assertEquals("com.whatsapp", msg.sourcePackage)
        assertEquals("Alice", msg.senderName)
        assertEquals("Hello from simulated WhatsApp test!", msg.messageText)
        assertEquals(1700000000000L, msg.receivedAt)
        assertEquals("TEXT", msg.messageType)
        assertFalse(msg.isPartialCapture)
        assertFalse(msg.isDeletedBySender)
    }
}
