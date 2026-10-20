package com.example

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.example.service.DiagnosticTracker
import com.example.service.NotificationParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

/**
 * Comprehensive parser unit tests using representative simulated payloads
 * for all supported social media and messaging platforms.
 *
 * NOTE: These tests use simulated StatusBarNotification and Bundle extras to test the parser logic.
 * Real on-device integration requires real hardware and NotificationListenerService binding.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationParserRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        DiagnosticTracker.clearDiagnostics()
    }

    private fun createMockSbn(
        packageName: String,
        notification: Notification,
        key: String = "${packageName}_key_101",
        postTime: Long = 1700000000000L
    ): StatusBarNotification {
        return StatusBarNotification(
            packageName,
            packageName,
            101,
            "tag_1",
            1000,
            1000,
            0,
            notification,
            android.os.Process.myUserHandle(),
            postTime
        )
    }

    @Test
    fun `WhatsApp 1-on-1 notification with MessagingStyle and Person sender`() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Alice")
            putCharSequence(Notification.EXTRA_TEXT, "Hey! How are you doing?")
            putString(Notification.EXTRA_TEMPLATE, "android.app.Notification\$MessagingStyle")
            putBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)

            val msgBundle = Bundle().apply {
                putCharSequence("text", "Hey! How are you doing?")
                putLong("time", 1700000001000L)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val person = Person.Builder().setName("Alice").build()
                    putParcelable("sender_person", person)
                } else {
                    putCharSequence("sender", "Alice")
                }
            }
            putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf<Parcelable>(msgBundle))
        }

        val notification = Notification().apply {
            this.extras = extras
            category = Notification.CATEGORY_MESSAGE
        }

        val sbn = createMockSbn("com.whatsapp", notification)
        val result = NotificationParser.parseWithDiagnostics(sbn, "WhatsApp")

        assertEquals("MessagingStyle", result.extractionMethod)
        assertEquals(1, result.messages.size)
        val message = result.messages[0]
        assertEquals("com.whatsapp", message.sourcePackage)
        assertEquals("WhatsApp", message.sourceAppName)
        assertEquals("Alice", message.senderName)
        assertEquals("Hey! How are you doing?", message.messageText)
        assertFalse(message.isGroupMessage)
        assertTrue(message.contentAvailable)
    }

    @Test
    fun `WhatsApp Business group conversation with multiple bundled messages`() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Product Launch Group")
            putCharSequence(Notification.EXTRA_CONVERSATION_TITLE, "Product Launch Group")
            putBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, true)
            putString(Notification.EXTRA_TEMPLATE, "android.app.Notification\$MessagingStyle")

            val msg1 = Bundle().apply {
                putCharSequence("text", "Has the shipment arrived?")
                putLong("time", 1700000002000L)
                putCharSequence("sender", "David Supplier")
            }
            val msg2 = Bundle().apply {
                putCharSequence("text", "Yes, warehouse confirmed delivery.")
                putLong("time", 1700000003000L)
                putCharSequence("sender", "Sarah Logistics")
            }
            putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf<Parcelable>(msg1, msg2))
        }

        val notification = Notification().apply {
            this.extras = extras
            category = Notification.CATEGORY_MESSAGE
        }

        val sbn = createMockSbn("com.whatsapp.w4b", notification)
        val result = NotificationParser.parseWithDiagnostics(sbn, "WhatsApp Business")

        assertEquals("MessagingStyle", result.extractionMethod)
        assertEquals(2, result.messages.size)

        assertEquals("David Supplier", result.messages[0].senderName)
        assertEquals("Has the shipment arrived?", result.messages[0].messageText)
        assertEquals("Product Launch Group", result.messages[0].conversationTitle)
        assertTrue(result.messages[0].isGroupMessage)

        assertEquals("Sarah Logistics", result.messages[1].senderName)
        assertEquals("Yes, warehouse confirmed delivery.", result.messages[1].messageText)
    }

    @Test
    fun `Facebook Messenger notification with Person sender extraction`() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Messenger Chat")
            putCharSequence(Notification.EXTRA_TEXT, "Are we still meeting at 5?")
            putString(Notification.EXTRA_TEMPLATE, "android.app.Notification\$MessagingStyle")

            val msgBundle = Bundle().apply {
                putCharSequence("text", "Are we still meeting at 5?")
                putLong("time", 1700000004000L)
                val personBundle = Bundle().apply {
                    putCharSequence("name", "Michael Scott")
                }
                putBundle("sender_person", personBundle)
            }
            putParcelableArrayList(Notification.EXTRA_MESSAGES, arrayListOf<Parcelable>(msgBundle))
        }

        val notification = Notification().apply {
            this.extras = extras
            category = Notification.CATEGORY_MESSAGE
        }

        val sbn = createMockSbn("com.facebook.orca", notification)
        val result = NotificationParser.parseWithDiagnostics(sbn, "Messenger")

        assertEquals("MessagingStyle", result.extractionMethod)
        assertEquals(1, result.messages.size)
        assertEquals("Michael Scott", result.messages[0].senderName)
        assertEquals("Are we still meeting at 5?", result.messages[0].messageText)
    }

    @Test
    fun `Instagram Direct Message extraction from BigTextStyle`() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "johndoe_photos")
            putCharSequence(Notification.EXTRA_TEXT, "Loved your recent story! Let's collaborate.")
            putCharSequence(Notification.EXTRA_BIG_TEXT, "Loved your recent story! Let's collaborate on the upcoming project.")
            putString(Notification.EXTRA_SUMMARY_TEXT, "Direct Message")
        }

        val notification = Notification().apply {
            this.extras = extras
        }

        val sbn = createMockSbn("com.instagram.android", notification)
        val result = NotificationParser.parseWithDiagnostics(sbn, "Instagram")

        assertEquals("BigTextStyle", result.extractionMethod)
        assertEquals(1, result.messages.size)
        assertEquals("johndoe_photos", result.messages[0].senderName)
        assertEquals("Loved your recent story! Let's collaborate on the upcoming project.", result.messages[0].messageText)
    }

    @Test
    fun `imo notification extraction`() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Uncle Tariq")
            putCharSequence(Notification.EXTRA_TEXT, "Please call me back when free.")
            putCharSequence(Notification.EXTRA_BIG_TEXT, "Please call me back when free.")
        }

        val notification = Notification().apply {
            this.extras = extras
            category = Notification.CATEGORY_SOCIAL
        }

        val sbn = createMockSbn("com.imo.android.imoim", notification)
        val result = NotificationParser.parseWithDiagnostics(sbn, "imo")

        assertEquals(1, result.messages.size)
        assertEquals("Uncle Tariq", result.messages[0].senderName)
        assertEquals("Please call me back when free.", result.messages[0].messageText)
        assertEquals("com.imo.android.imoim", result.messages[0].sourcePackage)
    }

    @Test
    fun `Telegram notification parsing and ongoing sync filtering`() {
        // 1. Valid chat message notification
        val chatExtras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Crypto Signals Club")
            putCharSequence(Notification.EXTRA_CONVERSATION_TITLE, "Crypto Signals Club")
            putString(Notification.EXTRA_TEMPLATE, "android.app.Notification\$MessagingStyle")

            val msg = Bundle().apply {
                putCharSequence("text", "New market alert published!")
                putLong("time", 1700000005000L)
                putCharSequence("sender", "Admin Vlad")
            }
            putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf<Parcelable>(msg))
        }

        val chatNotification = Notification().apply {
            extras = chatExtras
        }
        val chatSbn = createMockSbn("org.telegram.messenger", chatNotification)
        val chatResult = NotificationParser.parseWithDiagnostics(chatSbn, "Telegram")

        assertEquals(1, chatResult.messages.size)
        assertEquals("Admin Vlad", chatResult.messages[0].senderName)
        assertEquals("New market alert published!", chatResult.messages[0].messageText)

        // 2. Ongoing background service notification (should be filtered out)
        val bgExtras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Telegram")
            putCharSequence(Notification.EXTRA_TEXT, "Telegram is running in the background")
        }
        val bgNotification = Notification().apply {
            extras = bgExtras
            flags = flags or Notification.FLAG_ONGOING_EVENT
            category = Notification.CATEGORY_SERVICE
        }
        val bgSbn = createMockSbn("org.telegram.messenger", bgNotification)
        val bgResult = NotificationParser.parseWithDiagnostics(bgSbn, "Telegram")

        assertEquals(0, bgResult.messages.size)
        assertEquals("OngoingFilter", bgResult.extractionMethod)
    }

    @Test
    fun `Signal messaging extraction`() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Elena Rostova")
            putCharSequence(Notification.EXTRA_TEXT, "Safety verification code: 4920")
            putString(Notification.EXTRA_TEMPLATE, "android.app.Notification\$MessagingStyle")

            val msg = Bundle().apply {
                putCharSequence("text", "Safety verification code: 4920")
                putLong("time", 1700000006000L)
                putCharSequence("sender", "Elena Rostova")
            }
            putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf<Parcelable>(msg))
        }

        val notification = Notification().apply {
            this.extras = extras
            category = Notification.CATEGORY_MESSAGE
        }

        val sbn = createMockSbn("org.thoughtcrime.securesms", notification)
        val result = NotificationParser.parseWithDiagnostics(sbn, "Signal")

        assertEquals(1, result.messages.size)
        assertEquals("Elena Rostova", result.messages[0].senderName)
        assertEquals("Safety verification code: 4920", result.messages[0].messageText)
    }

    @Test
    fun `Discord channel mention message extraction`() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Discord")
            putCharSequence(Notification.EXTRA_CONVERSATION_TITLE, "#dev-chat")
            putCharSequence(Notification.EXTRA_TEXT, "Marcus: Server deployment is complete!")
            putCharSequence(Notification.EXTRA_BIG_TEXT, "Marcus: Server deployment is complete!")
        }

        val notification = Notification().apply {
            this.extras = extras
        }

        val sbn = createMockSbn("com.discord", notification)
        val result = NotificationParser.parseWithDiagnostics(sbn, "Discord")

        assertEquals(1, result.messages.size)
        assertEquals("Marcus", result.messages[0].senderName)
        assertEquals("Server deployment is complete!", result.messages[0].messageText)
    }

    @Test
    fun `Snapchat notification extraction`() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Jessica")
            putCharSequence(Notification.EXTRA_TEXT, "Check out this snap!")
        }

        val notification = Notification().apply {
            this.extras = extras
        }

        val sbn = createMockSbn("com.snapchat.android", notification)
        val result = NotificationParser.parseWithDiagnostics(sbn, "Snapchat")

        assertEquals(1, result.messages.size)
        assertEquals("Jessica", result.messages[0].senderName)
        assertEquals("Check out this snap!", result.messages[0].messageText)
    }

    @Test
    fun `Viber notification extraction`() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Mom")
            putCharSequence(Notification.EXTRA_TEXT, "Dinner is ready at 7pm")
            putCharSequence(Notification.EXTRA_BIG_TEXT, "Dinner is ready at 7pm")
        }

        val notification = Notification().apply {
            this.extras = extras
        }

        val sbn = createMockSbn("com.viber.voip", notification)
        val result = NotificationParser.parseWithDiagnostics(sbn, "Viber")

        assertEquals(1, result.messages.size)
        assertEquals("Mom", result.messages[0].senderName)
        assertEquals("Dinner is ready at 7pm", result.messages[0].messageText)
    }

    @Test
    fun `Gmail notification verification preserving backward compatibility`() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Google Security")
            putCharSequence(Notification.EXTRA_TEXT, "Security alert: New sign-in detected")
            putCharSequence(Notification.EXTRA_BIG_TEXT, "Security alert: A new sign-in was detected on your account.")
        }

        val notification = Notification().apply {
            this.extras = extras
            category = Notification.CATEGORY_EMAIL
        }

        val sbn = createMockSbn("com.google.android.gm", notification)
        val result = NotificationParser.parseWithDiagnostics(sbn, "Gmail")

        assertEquals(1, result.messages.size)
        assertEquals("Google Security", result.messages[0].senderName)
        assertEquals("Security alert: A new sign-in was detected on your account.", result.messages[0].messageText)
    }

    @Test
    fun `Facebook notification verification preserving backward compatibility`() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Facebook")
            putCharSequence(Notification.EXTRA_TEXT, "Mark commented on your photo: Nice shot!")
        }

        val notification = Notification().apply {
            this.extras = extras
            category = Notification.CATEGORY_SOCIAL
        }

        val sbn = createMockSbn("com.facebook.katana", notification)
        val result = NotificationParser.parseWithDiagnostics(sbn, "Facebook")

        assertEquals(1, result.messages.size)
        assertEquals("Facebook", result.messages[0].senderName)
        assertEquals("Mark commented on your photo: Nice shot!", result.messages[0].messageText)
    }

    @Test
    fun `Group summary banner with aggregate count does not generate false placeholder`() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "WhatsApp")
            putCharSequence(Notification.EXTRA_TEXT, "3 new messages")
        }

        val notification = Notification().apply {
            this.extras = extras
            flags = flags or Notification.FLAG_GROUP_SUMMARY
        }

        val sbn = createMockSbn("com.whatsapp", notification)
        val result = NotificationParser.parseWithDiagnostics(sbn, "WhatsApp")

        // Group summary banner without child messages should be gracefully skipped
        // so actual child notifications are stored cleanly
        assertEquals(0, result.messages.size)
        assertEquals("GroupSummary", result.extractionMethod)
    }

    @Test
    fun `Anti-delete preservation distinguishes system deletion alerts from conversation text`() {
        // True deletion notices
        assertTrue(NotificationParser.isDeletionNotice("This message was deleted"))
        assertTrue(NotificationParser.isDeletionNotice("this message was deleted by the sender"))
        assertTrue(NotificationParser.isDeletionNotice("You deleted this message"))
        assertTrue(NotificationParser.isDeletionNotice("unsent a message"))
        assertTrue(NotificationParser.isDeletionNotice("Ce message a été supprimé"))
        assertTrue(NotificationParser.isDeletionNotice("Este mensaje fue eliminado"))

        // Conversational text containing the word 'deleted' should NOT be treated as unsend notice
        assertFalse(NotificationParser.isDeletionNotice("I deleted the old draft yesterday"))
        assertFalse(NotificationParser.isDeletionNotice("Why did the company delete that post?"))
        assertFalse(NotificationParser.isDeletionNotice("He deleted his social media profile"))
    }

    @Test
    fun `DiagnosticTracker records pipeline stages without exposing private text`() {
        val pkg = "com.whatsapp"
        val app = "WhatsApp"

        DiagnosticTracker.recordEventReceived(pkg, app)
        DiagnosticTracker.recordFilterAccepted(pkg, app)
        DiagnosticTracker.recordExtractionSuccess(pkg, app, 1, "MessagingStyle")
        DiagnosticTracker.recordInsertionSuccess(pkg, app, 1)

        val status = DiagnosticTracker.packageDiagnostics.value.find { it.packageName == pkg }
        assertNotNull(status)
        assertTrue("Notification received must be true", status!!.notificationReceived)
        assertTrue("Package accepted must be true", status.packageAccepted)
        assertTrue("Message extracted must be true", status.messageExtracted)
        assertTrue("Database inserted must be true", status.databaseInserted)
        assertEquals(1, status.totalMessagesSaved)

        // Verify statusReason does NOT contain sensitive message text
        assertFalse(status.statusReason.contains("Secret password"))
        assertTrue(status.statusReason.contains("Database insertion succeeded"))
    }

    @Test
    fun `Message type and partial capture identification tests`() {
        // 1. Text message: Complete capture
        val textExtras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Liam")
            putCharSequence(Notification.EXTRA_TEXT, "See you tomorrow!")
        }
        val textSbn = createMockSbn("com.whatsapp", Notification().apply { extras = textExtras })
        val textResult = NotificationParser.parseWithDiagnostics(textSbn, "WhatsApp")
        assertEquals(1, textResult.messages.size)
        assertEquals("TEXT", textResult.messages[0].messageType)
        assertFalse(textResult.messages[0].isPartialCapture)

        // 2. Photo attachment message: IMAGE type
        val photoExtras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Emma")
            putCharSequence(Notification.EXTRA_TEXT, "📷 Photo")
        }
        val photoSbn = createMockSbn("com.whatsapp", Notification().apply { extras = photoExtras })
        val photoResult = NotificationParser.parseWithDiagnostics(photoSbn, "WhatsApp")
        assertEquals("IMAGE", photoResult.messages[0].messageType)

        // 3. Voice note: AUDIO type
        val audioExtras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Noah")
            putCharSequence(Notification.EXTRA_TEXT, "🎙️ Voice message (0:15)")
        }
        val audioSbn = createMockSbn("org.telegram.messenger", Notification().apply { extras = audioExtras })
        val audioResult = NotificationParser.parseWithDiagnostics(audioSbn, "Telegram")
        assertEquals("AUDIO", audioResult.messages[0].messageType)

        // 4. Video message: VIDEO type
        val videoExtras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Sophia")
            putCharSequence(Notification.EXTRA_TEXT, "📹 Video (2:30)")
        }
        val videoSbn = createMockSbn("com.facebook.orca", Notification().apply { extras = videoExtras })
        val videoResult = NotificationParser.parseWithDiagnostics(videoSbn, "Messenger")
        assertEquals("VIDEO", videoResult.messages[0].messageType)

        // 5. Hidden notification preview: Partial capture
        val hiddenExtras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Signal")
            putCharSequence(Notification.EXTRA_TEXT, "New message")
        }
        val hiddenSbn = createMockSbn("org.thoughtcrime.securesms", Notification().apply { extras = hiddenExtras })
        val hiddenResult = NotificationParser.parseWithDiagnostics(hiddenSbn, "Signal")
        assertEquals(1, hiddenResult.messages.size)
        assertTrue(hiddenResult.messages[0].isPartialCapture)
    }
}
