package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.MessageDao
import com.example.data.local.MessageEntity
import com.example.data.preferences.RetentionPolicy
import com.example.repository.MessageRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MsgVaultRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MessageDao
    private lateinit var repository: MessageRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.messageDao()
        repository = MessageRepository(dao, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `save and retrieve message notification`() = runBlocking {
        val message = MessageEntity(
            sourcePackage = "com.whatsapp",
            sourceAppName = "WhatsApp",
            senderName = "Alice",
            messageText = "Hey, don't delete this message!",
            receivedAt = System.currentTimeMillis()
        )

        val id = repository.insertMessage(message)
        assertTrue(id > 0)

        val allMessages = repository.allMessages.first()
        assertEquals(1, allMessages.size)
        assertEquals("Alice", allMessages[0].senderName)
        assertEquals("Hey, don't delete this message!", allMessages[0].messageText)
    }

    @Test
    fun `search messages by sender and message text`() = runBlocking {
        val msg1 = MessageEntity(
            sourcePackage = "org.telegram.messenger",
            sourceAppName = "Telegram",
            senderName = "Bob Secret",
            messageText = "Confidential meeting code: 7891",
            receivedAt = System.currentTimeMillis()
        )
        val msg2 = MessageEntity(
            sourcePackage = "com.facebook.orca",
            sourceAppName = "Messenger",
            senderName = "Charlie",
            messageText = "Normal casual hello",
            receivedAt = System.currentTimeMillis()
        )

        repository.insertMessage(msg1)
        repository.insertMessage(msg2)

        val searchByText = repository.searchMessages("7891").first()
        assertEquals(1, searchByText.size)
        assertEquals("Bob Secret", searchByText[0].senderName)

        val searchBySender = repository.searchMessages("Charlie").first()
        assertEquals(1, searchBySender.size)
        assertEquals("Normal casual hello", searchBySender[0].messageText)
    }

    @Test
    fun `duplicate detection identifies recent identical notification`() = runBlocking {
        val now = System.currentTimeMillis()
        val original = MessageEntity(
            sourcePackage = "com.whatsapp",
            sourceAppName = "WhatsApp",
            senderName = "Alice",
            messageText = "Duplicated text alert",
            receivedAt = now,
            capturedAt = now
        )
        dao.insertMessage(original)

        val duplicateFound = dao.findRecentDuplicate(
            sourcePackage = "com.whatsapp",
            senderName = "Alice",
            messageText = "Duplicated text alert",
            minTimestamp = now - 5000L
        )

        assertNotNull(duplicateFound)
        assertEquals("Duplicated text alert", duplicateFound?.messageText)

        // Same text from a different sender should NOT be discarded as a duplicate
        val differentSender = dao.findRecentDuplicate(
            sourcePackage = "com.whatsapp",
            senderName = "Bob",
            messageText = "Duplicated text alert",
            minTimestamp = now - 5000L
        )
        assertNull(differentSender)

        val differentApp = dao.findRecentDuplicate(
            sourcePackage = "org.telegram.messenger",
            senderName = "Alice",
            messageText = "Duplicated text alert",
            minTimestamp = now - 5000L
        )
        assertNull(differentApp)
    }

    @Test
    fun `toggle favorite updates message status`() = runBlocking {
        val msg = MessageEntity(
            sourcePackage = "com.whatsapp",
            sourceAppName = "WhatsApp",
            messageText = "Star this note",
            isFavorite = false
        )
        val id = repository.insertMessage(msg)

        repository.toggleFavorite(id, current = false)
        val updated = repository.getMessageById(id).first()
        assertNotNull(updated)
        assertTrue(updated!!.isFavorite)

        val favorites = repository.favoriteMessages.first()
        assertEquals(1, favorites.size)
    }

    @Test
    fun `delete individual and multiple messages`() = runBlocking {
        val id1 = repository.insertMessage(MessageEntity(sourcePackage = "app1", sourceAppName = "App 1", messageText = "Msg 1"))
        val id2 = repository.insertMessage(MessageEntity(sourcePackage = "app2", sourceAppName = "App 2", messageText = "Msg 2"))
        val id3 = repository.insertMessage(MessageEntity(sourcePackage = "app3", sourceAppName = "App 3", messageText = "Msg 3"))

        assertEquals(3, repository.totalCount.first())

        repository.deleteById(id1)
        assertEquals(2, repository.totalCount.first())

        repository.deleteByIds(listOf(id2, id3))
        assertEquals(0, repository.totalCount.first())
    }

    @Test
    fun `retention policy purges expired messages`() = runBlocking {
        val now = System.currentTimeMillis()
        val oldMillis = now - (10L * 24 * 60 * 60 * 1000) // 10 days ago
        val recentMillis = now - (2L * 24 * 60 * 60 * 1000) // 2 days ago

        val oldMsg = MessageEntity(sourcePackage = "app1", sourceAppName = "App 1", messageText = "Old", receivedAt = oldMillis)
        val recentMsg = MessageEntity(sourcePackage = "app2", sourceAppName = "App 2", messageText = "Recent", receivedAt = recentMillis)

        dao.insertMessage(oldMsg)
        dao.insertMessage(recentMsg)

        val purged = repository.applyRetentionPolicy(RetentionPolicy.DAYS_7)
        assertEquals(1, purged)

        val remaining = repository.allMessages.first()
        assertEquals(1, remaining.size)
        assertEquals("Recent", remaining[0].messageText)
    }

    @Test
    fun `export messages serializes database to valid JSON file`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val msg1 = MessageEntity(
            sourcePackage = "com.whatsapp",
            sourceAppName = "WhatsApp",
            senderName = "Alice",
            messageText = "Secret meetup plan",
            receivedAt = System.currentTimeMillis()
        )
        val msg2 = MessageEntity(
            sourcePackage = "org.telegram.messenger",
            sourceAppName = "Telegram",
            senderName = "Bob",
            messageText = "Financial document draft",
            receivedAt = System.currentTimeMillis()
        )
        repository.insertMessage(msg1)
        repository.insertMessage(msg2)

        val jsonFile = repository.exportMessagesToJson(context)
        assertTrue(jsonFile.exists())
        assertTrue(jsonFile.length() > 0)

        val content = jsonFile.readText()
        val jsonObject = org.json.JSONObject(content)
        assertEquals("MsgVault", jsonObject.getString("appName"))
        assertEquals(2, jsonObject.getInt("totalMessages"))

        val messagesArray = jsonObject.getJSONArray("messages")
        assertEquals(2, messagesArray.length())
    }

    @Test
    fun `database inspector queries table schema and metrics`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository.insertMessage(
            MessageEntity(
                sourcePackage = "com.whatsapp",
                sourceAppName = "WhatsApp",
                messageText = "Inspectable row"
            )
        )

        val stats = repository.getDatabaseStats(context)
        assertTrue(stats.columns.isNotEmpty())
        assertTrue(stats.columns.any { it.name == "id" && it.primaryKey })
        assertTrue(stats.columns.any { it.name == "messageText" })
        assertTrue(stats.columns.any { it.name == "isDeletedBySender" })
        assertTrue(stats.columns.any { it.name == "deletedNoticeTimestamp" })
        assertEquals(1, stats.totalRecords)
    }

    @Test
    fun `anti-delete preservation preserves original message text when sender unsends`() = runBlocking {
        val originalText = "Top secret conversation details that sender later tries to unsend"
        val originalMsg = MessageEntity(
            sourcePackage = "com.whatsapp",
            sourceAppName = "WhatsApp",
            senderName = "Bob Smith",
            messageText = originalText,
            receivedAt = System.currentTimeMillis() - 5000
        )
        val id = repository.insertMessage(originalMsg)

        // Verify inserted message
        val latestMsg = dao.findLatestMessageForSender("com.whatsapp", "Bob Smith")
        assertNotNull(latestMsg)
        assertEquals(id, latestMsg!!.id)
        assertFalse(latestMsg.isDeletedBySender)
        assertEquals(originalText, latestMsg.messageText)

        // Sender unsends message: WhatsApp issues "This message was deleted"
        val deletionNoticeText = "This message was deleted"
        assertTrue(com.example.service.NotificationParser.isDeletionNotice(deletionNoticeText))

        // When anti-delete engine handles deletion notice:
        val deletionTime = System.currentTimeMillis()
        dao.markDeletedBySender(latestMsg.id, deletionTime)

        // Query database again
        val preservedMsg = repository.getMessageById(latestMsg.id).first()
        assertNotNull(preservedMsg)
        assertTrue(preservedMsg!!.isDeletedBySender)
        assertEquals(deletionTime, preservedMsg.deletedNoticeTimestamp)
        // CRITICAL CHECK: Original message text MUST REMAIN INTACT!
        assertEquals(originalText, preservedMsg.messageText)
    }

    @Test
    fun `SupportedAppRegistry includes imo and all required social platforms`() {
        val supportedPackages = com.example.data.model.SupportedAppRegistry.ALL_SUPPORTED_APPS.map { it.packageName }
        
        // Assert all required apps are present
        assertTrue("WhatsApp required", supportedPackages.contains("com.whatsapp"))
        assertTrue("WhatsApp Business required", supportedPackages.contains("com.whatsapp.w4b"))
        assertTrue("Messenger required", supportedPackages.contains("com.facebook.orca"))
        assertTrue("Instagram required", supportedPackages.contains("com.instagram.android"))
        assertTrue("imo required", supportedPackages.contains("com.imo.android.imoim"))
        assertTrue("Telegram required", supportedPackages.contains("org.telegram.messenger"))
        assertTrue("Snapchat required", supportedPackages.contains("com.snapchat.android"))
        assertTrue("Discord required", supportedPackages.contains("com.discord"))
        assertTrue("Signal required", supportedPackages.contains("org.thoughtcrime.securesms"))
        assertTrue("Viber required", supportedPackages.contains("com.viber.voip"))
        assertTrue("LINE required", supportedPackages.contains("jp.naver.line.android"))
        assertTrue("Skype required", supportedPackages.contains("com.skype.raider"))
        assertTrue("Google Messages required", supportedPackages.contains("com.google.android.apps.messaging"))
        assertTrue("X required", supportedPackages.contains("com.twitter.android"))
    }

    @Test
    fun `notification parser distinguishes deletion notices and placeholders`() {
        // Various deletion phrases across languages and apps
        assertTrue(com.example.service.NotificationParser.isDeletionNotice("This message was deleted"))
        assertTrue(com.example.service.NotificationParser.isDeletionNotice("this message was deleted by the sender"))
        assertTrue(com.example.service.NotificationParser.isDeletionNotice("Message unsent"))
        assertTrue(com.example.service.NotificationParser.isDeletionNotice("Ce message a été supprimé"))
        assertTrue(com.example.service.NotificationParser.isDeletionNotice("Este mensaje fue eliminado"))

        // Regular messages should not be flagged as deletion notices
        assertFalse(com.example.service.NotificationParser.isDeletionNotice("Hey what are you doing?"))
        assertFalse(com.example.service.NotificationParser.isDeletionNotice("Deleted the files from the computer"))

        // Generic placeholders
        assertTrue(com.example.service.NotificationParser.isGenericPlaceholderText("New message"))
        assertTrue(com.example.service.NotificationParser.isGenericPlaceholderText("3 new messages"))
        assertFalse(com.example.service.NotificationParser.isGenericPlaceholderText("See you tomorrow"))
    }
}

