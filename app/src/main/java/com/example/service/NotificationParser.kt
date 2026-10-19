package com.example.service

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.data.local.MessageEntity
import com.example.data.model.SupportedAppRegistry

object NotificationParser {

    data class ParsedNotificationResult(
        val messages: List<MessageEntity>
    )

    fun parse(sbn: StatusBarNotification, appDisplayName: String): List<MessageEntity> {
        val notification = sbn.notification ?: return emptyList()
        val packageName = sbn.packageName ?: return emptyList()

        // Filter out ongoing non-clearable notifications (like music players, active calls, background sync)
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (isOngoing) {
            return emptyList()
        }

        val extras = notification.extras ?: return emptyList()
        val postTime = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        val notificationKey = sbn.key ?: "${packageName}_${sbn.id}_$postTime"

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()

        // Check if there are bundled messages in MessagingStyle
        val extractedMessagingStyleMessages = extractMessagingStyleMessages(
            notification = notification,
            extras = extras,
            packageName = packageName,
            appDisplayName = appDisplayName,
            conversationTitle = conversationTitle,
            defaultTitle = title,
            notificationKey = notificationKey,
            fallbackPostTime = postTime
        )

        if (extractedMessagingStyleMessages.isNotEmpty()) {
            return extractedMessagingStyleMessages
        }

        // Check inbox style messages (e.g. EXTRA_TEXT_LINES)
        val rawLines = extras.get(Notification.EXTRA_TEXT_LINES)
        val textLines: List<CharSequence>? = when (rawLines) {
            is Array<*> -> rawLines.filterIsInstance<CharSequence>()
            is Iterable<*> -> rawLines.filterIsInstance<CharSequence>()
            else -> null
        }
        if (!textLines.isNullOrEmpty()) {
            val inboxMessages = mutableListOf<MessageEntity>()
            for (line in textLines) {
                val lineStr = line?.toString()?.trim() ?: continue
                if (lineStr.isBlank()) continue

                // Lines often format as "Sender: Message"
                val sender: String?
                val msgContent: String
                if (lineStr.contains(": ")) {
                    val parts = lineStr.split(": ", limit = 2)
                    sender = parts[0].trim()
                    msgContent = parts[1].trim()
                } else {
                    sender = title
                    msgContent = lineStr
                }

                if (isIgnorableSystemText(msgContent)) continue

                inboxMessages.add(
                    MessageEntity(
                        sourcePackage = packageName,
                        sourceAppName = appDisplayName,
                        senderName = sender ?: title,
                        conversationTitle = conversationTitle ?: title,
                        messageText = msgContent,
                        receivedAt = postTime,
                        capturedAt = System.currentTimeMillis(),
                        notificationKey = notificationKey,
                        isGroupMessage = conversationTitle != null || (title != null && sender != null && title != sender),
                        attachmentDescription = detectAttachment(msgContent),
                        contentAvailable = true
                    )
                )
            }
            if (inboxMessages.isNotEmpty()) {
                return inboxMessages
            }
        }

        // Standard Single or BigText Notification
        val effectiveText = when {
            !bigText.isNullOrBlank() -> bigText
            !text.isNullOrBlank() -> text
            else -> null
        }

        if (effectiveText.isNullOrBlank()) {
            // Check if there is an attachment or image metadata
            val hasPicture = extras.containsKey(Notification.EXTRA_PICTURE) || extras.containsKey(Notification.EXTRA_PICTURE_ICON)
            if (hasPicture || !title.isNullOrBlank()) {
                return listOf(
                    MessageEntity(
                        sourcePackage = packageName,
                        sourceAppName = appDisplayName,
                        senderName = title,
                        conversationTitle = conversationTitle ?: subText,
                        messageText = if (hasPicture) "[Photo attachment]" else "Message content unavailable in notification.",
                        receivedAt = postTime,
                        capturedAt = System.currentTimeMillis(),
                        notificationKey = notificationKey,
                        isGroupMessage = !conversationTitle.isNullOrBlank(),
                        attachmentDescription = if (hasPicture) "Photo" else null,
                        contentAvailable = hasPicture
                    )
                )
            }
            return emptyList()
        }

        if (isIgnorableSystemText(effectiveText)) {
            return emptyList()
        }

        val isDeletion = isDeletionNotice(effectiveText)
        val isPlaceholder = isGenericPlaceholderText(effectiveText)
        val finalText = when {
            isPlaceholder -> "New message (Content hidden by notification preview settings)"
            else -> effectiveText
        }

        return listOf(
            MessageEntity(
                sourcePackage = packageName,
                sourceAppName = appDisplayName,
                senderName = title,
                conversationTitle = conversationTitle ?: subText,
                messageText = finalText,
                receivedAt = postTime,
                capturedAt = System.currentTimeMillis(),
                notificationKey = notificationKey,
                isGroupMessage = !conversationTitle.isNullOrBlank() || (!subText.isNullOrBlank() && subText != title),
                attachmentDescription = detectAttachment(effectiveText),
                contentAvailable = !isPlaceholder,
                isDeletedBySender = isDeletion,
                deletedNoticeTimestamp = if (isDeletion) postTime else null
            )
        )
    }

    private fun extractMessagingStyleMessages(
        notification: Notification,
        extras: Bundle,
        packageName: String,
        appDisplayName: String,
        conversationTitle: String?,
        defaultTitle: String?,
        notificationKey: String,
        fallbackPostTime: Long
    ): List<MessageEntity> {
        val messagesList = mutableListOf<MessageEntity>()
        val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)

        if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
            val isGroup = messagingStyle.isGroupConversation || !conversationTitle.isNullOrBlank()
            for (m in messagingStyle.messages) {
                val mText = m.text?.toString()?.trim() ?: continue
                if (mText.isBlank() || isIgnorableSystemText(mText)) continue

                val sender = m.person?.name?.toString()?.trim() 
                    ?: defaultTitle 
                    ?: "Unknown"

                val time = if (m.timestamp > 0) m.timestamp else fallbackPostTime

                messagesList.add(
                    MessageEntity(
                        sourcePackage = packageName,
                        sourceAppName = appDisplayName,
                        senderName = sender,
                        conversationTitle = conversationTitle ?: messagingStyle.conversationTitle?.toString(),
                        messageText = mText,
                        receivedAt = time,
                        capturedAt = System.currentTimeMillis(),
                        notificationKey = notificationKey,
                        isGroupMessage = isGroup,
                        attachmentDescription = m.dataMimeType ?: detectAttachment(mText),
                        contentAvailable = true
                    )
                )
            }
            return messagesList
        }

        // Direct Bundle fallback for MessagingStyle EXTRA_MESSAGES
        val rawMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (rawMessages != null && rawMessages.isNotEmpty()) {
            for (item in rawMessages) {
                if (item is Bundle) {
                    val msgText = item.getCharSequence("text")?.toString()?.trim() ?: continue
                    if (msgText.isBlank() || isIgnorableSystemText(msgText)) continue

                    val sender = item.getCharSequence("sender")?.toString()?.trim() ?: defaultTitle ?: "Unknown"
                    val time = item.getLong("time", fallbackPostTime)

                    messagesList.add(
                        MessageEntity(
                            sourcePackage = packageName,
                            sourceAppName = appDisplayName,
                            senderName = sender,
                            conversationTitle = conversationTitle,
                            messageText = msgText,
                            receivedAt = if (time > 0) time else fallbackPostTime,
                            capturedAt = System.currentTimeMillis(),
                            notificationKey = notificationKey,
                            isGroupMessage = !conversationTitle.isNullOrBlank(),
                            attachmentDescription = detectAttachment(msgText),
                            contentAvailable = true
                        )
                    )
                }
            }
        }

        return messagesList
    }

    private fun detectAttachment(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("photo") || lower.contains("image") || lower.contains("📷") -> "Photo"
            lower.contains("video") || lower.contains("🎥") -> "Video"
            lower.contains("voice message") || lower.contains("audio") || lower.contains("🎙️") -> "Voice Message"
            lower.contains("sticker") || lower.contains("gif") -> "Sticker/GIF"
            lower.contains("document") || lower.contains("file") || lower.contains("pdf") -> "Document"
            lower.contains("location") || lower.contains("pinned location") -> "Location"
            else -> null
        }
    }

    private fun isIgnorableSystemText(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower.contains("checking for new messages") ||
               lower.contains("whatsapp web is currently active") ||
               lower.contains("backup in progress") ||
               lower.contains("syncing mail") ||
               lower.contains("running in the background") ||
               lower.contains("tap for more info")
    }

    fun isDeletionNotice(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = text.lowercase().trim()
        return lower == "this message was deleted" ||
               lower == "this message was deleted by the sender" ||
               lower == "message was deleted" ||
               lower == "message deleted" ||
               lower == "message unsent" ||
               lower == "unsent a message" ||
               lower == "this message was unsent" ||
               lower.contains("this message was deleted") ||
               lower.contains("this message was unsent") ||
               lower.contains("unsent a message") ||
               lower.contains("message unsent") ||
               lower.contains("deleted this message") ||
               lower.contains("sender deleted this message") ||
               lower.contains("ce message a été supprimé") ||
               lower.contains("este mensaje fue eliminado") ||
               lower.contains("dieser beitrag wurde gelöscht") ||
               lower.contains("questo messaggio è stato eliminato")
    }

    fun isGenericPlaceholderText(text: String?): Boolean {
        if (text.isNullOrBlank()) return true
        val lower = text.lowercase().trim()
        return lower == "new message" ||
               lower == "new messages" ||
               lower == "1 new message" ||
               lower == "incoming message" ||
               lower == "message" ||
               lower.matches(Regex("\\d+\\s+new\\s+messages?"))
    }
}
