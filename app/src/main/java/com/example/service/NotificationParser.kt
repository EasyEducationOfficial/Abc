package com.example.service

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.data.local.MessageEntity

object NotificationParser {

    data class ParsedNotificationResult(
        val messages: List<MessageEntity>,
        val extractionMethod: String,
        val failureReason: String? = null
    )

    fun parseWithDiagnostics(sbn: StatusBarNotification, appDisplayName: String): ParsedNotificationResult {
        val notification = sbn.notification ?: return ParsedNotificationResult(
            emptyList(),
            "None",
            "StatusBarNotification.notification is null"
        )
        val packageName = sbn.packageName ?: return ParsedNotificationResult(
            emptyList(),
            "None",
            "StatusBarNotification.packageName is null"
        )

        // Filter out non-message ongoing background services (e.g. music players, background sync)
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (isOngoing) {
            val extras = notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
            if (isIgnorableSystemText(title) || isIgnorableSystemText(text) || notification.category == Notification.CATEGORY_SERVICE) {
                return ParsedNotificationResult(emptyList(), "OngoingFilter", "Ongoing background service notification ignored")
            }
        }

        val extras = notification.extras ?: return ParsedNotificationResult(
            emptyList(),
            "None",
            "Notification.extras is null"
        )

        val postTime = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        val notificationKey = sbn.key ?: "${packageName}_${sbn.id}_$postTime"

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.trim()

        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0

        // 1. Try MessagingStyle extraction (Android Framework extras & AndroidX Compat)
        val messagingStyleMessages = extractMessagingStyleMessages(
            notification = notification,
            extras = extras,
            packageName = packageName,
            appDisplayName = appDisplayName,
            conversationTitle = conversationTitle ?: summaryText,
            defaultTitle = title,
            notificationKey = notificationKey,
            fallbackPostTime = postTime
        )

        if (messagingStyleMessages.isNotEmpty()) {
            return ParsedNotificationResult(messagingStyleMessages, "MessagingStyle")
        }

        // 2. Try InboxStyle (EXTRA_TEXT_LINES)
        val rawLines = extras.get(Notification.EXTRA_TEXT_LINES)
        val textLines: List<CharSequence>? = when (rawLines) {
            is Array<*> -> rawLines.filterIsInstance<CharSequence>()
            is Iterable<*> -> rawLines.filterIsInstance<CharSequence>()
            else -> null
        }

        if (!textLines.isNullOrEmpty()) {
            val inboxMessages = mutableListOf<MessageEntity>()
            for (line in textLines) {
                val lineStr = line.toString().trim()
                if (lineStr.isBlank() || isIgnorableSystemText(lineStr)) continue

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

                val isDeletion = isDeletionNotice(msgContent)
                val attachment = detectAttachment(msgContent)
                val msgType = resolveMessageType(null, attachment, msgContent)

                inboxMessages.add(
                    MessageEntity(
                        sourcePackage = packageName,
                        sourceAppName = appDisplayName,
                        senderName = sender ?: title,
                        conversationTitle = conversationTitle ?: summaryText ?: title,
                        messageText = msgContent,
                        receivedAt = postTime,
                        capturedAt = System.currentTimeMillis(),
                        notificationKey = notificationKey,
                        isGroupMessage = conversationTitle != null || (title != null && sender != null && title != sender),
                        attachmentDescription = attachment,
                        contentAvailable = true,
                        isDeletedBySender = isDeletion,
                        deletedNoticeTimestamp = if (isDeletion) postTime else null,
                        messageType = msgType,
                        isPartialCapture = false
                    )
                )
            }
            if (inboxMessages.isNotEmpty()) {
                return ParsedNotificationResult(inboxMessages, "InboxStyle")
            }
        }

        // 3. Skip pure container summary notification if it only has placeholder count
        val effectiveText = when {
            !bigText.isNullOrBlank() -> bigText
            !text.isNullOrBlank() -> text
            else -> null
        }

        if (isGroupSummary && (effectiveText == null || isGenericPlaceholderText(effectiveText))) {
            return ParsedNotificationResult(
                emptyList(),
                "GroupSummary",
                "Group container summary notification skipped (child messages captured individually)"
            )
        }

        // 4. Standard Single / BigText notification
        if (effectiveText.isNullOrBlank()) {
            val hasPicture = extras.containsKey(Notification.EXTRA_PICTURE) ||
                    extras.containsKey(Notification.EXTRA_PICTURE_ICON) ||
                    extras.containsKey("android.picture")

            if (hasPicture || !title.isNullOrBlank()) {
                val resolvedText = if (hasPicture) "[Photo attachment]" else "Message content unavailable in notification."
                val msgType = if (hasPicture) "IMAGE" else "OTHER"
                return ParsedNotificationResult(
                    listOf(
                        MessageEntity(
                            sourcePackage = packageName,
                            sourceAppName = appDisplayName,
                            senderName = title,
                            conversationTitle = conversationTitle ?: subText ?: summaryText,
                            messageText = resolvedText,
                            receivedAt = postTime,
                            capturedAt = System.currentTimeMillis(),
                            notificationKey = notificationKey,
                            isGroupMessage = !conversationTitle.isNullOrBlank(),
                            attachmentDescription = if (hasPicture) "Photo" else null,
                            contentAvailable = hasPicture,
                            messageType = msgType,
                            isPartialCapture = true
                        )
                    ),
                    if (hasPicture) "MediaAttachment" else "TitleOnly"
                )
            }

            return ParsedNotificationResult(
                emptyList(),
                "EmptyContent",
                "No message text or attachment found in notification extras"
            )
        }

        if (isIgnorableSystemText(effectiveText)) {
            return ParsedNotificationResult(
                emptyList(),
                "IgnorableText",
                "Ignorable system / synchronization alert"
            )
        }

        // Parse formatted "Sender: Message" (common in Telegram, Discord, Messenger)
        var parsedSender = title
        var parsedContent = effectiveText
        if (!title.isNullOrBlank() && (title == appDisplayName || title.contains("Group", ignoreCase = true) || title.startsWith("#"))) {
            if (effectiveText.contains(": ")) {
                val parts = effectiveText.split(": ", limit = 2)
                val prefix = parts[0].trim()
                val hasActionVerb = prefix.contains("commented", ignoreCase = true) ||
                        prefix.contains("liked", ignoreCase = true) ||
                        prefix.contains("shared", ignoreCase = true) ||
                        prefix.contains("posted", ignoreCase = true) ||
                        prefix.contains("tagged", ignoreCase = true) ||
                        prefix.contains("reacted", ignoreCase = true)
                if (prefix.length in 1..30 && !hasActionVerb && prefix.split(" ").size <= 3) {
                    parsedSender = prefix
                    parsedContent = parts[1].trim()
                }
            }
        }

        val isDeletion = isDeletionNotice(parsedContent)
        val isPlaceholder = isGenericPlaceholderText(parsedContent)
        val finalText = when {
            isPlaceholder -> "New message (Content hidden by notification preview settings)"
            else -> parsedContent
        }

        val attachment = detectAttachment(effectiveText)
        val msgType = resolveMessageType(null, attachment, effectiveText)
        val isPartial = isPlaceholder || effectiveText.isBlank()

        val message = MessageEntity(
            sourcePackage = packageName,
            sourceAppName = appDisplayName,
            senderName = parsedSender ?: title,
            conversationTitle = conversationTitle ?: subText ?: summaryText,
            messageText = finalText,
            receivedAt = postTime,
            capturedAt = System.currentTimeMillis(),
            notificationKey = notificationKey,
            isGroupMessage = !conversationTitle.isNullOrBlank() || (!subText.isNullOrBlank() && subText != title),
            attachmentDescription = attachment,
            contentAvailable = !isPlaceholder,
            isDeletedBySender = isDeletion,
            deletedNoticeTimestamp = if (isDeletion) postTime else null,
            messageType = msgType,
            isPartialCapture = isPartial
        )

        return ParsedNotificationResult(listOf(message), if (!bigText.isNullOrBlank()) "BigTextStyle" else "StandardText")
    }

    fun parse(sbn: StatusBarNotification, appDisplayName: String): List<MessageEntity> {
        return parseWithDiagnostics(sbn, appDisplayName).messages
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

        // Strategy A: Android Framework EXTRA_MESSAGES ("android.messages")
        val rawMessages = extras.get(Notification.EXTRA_MESSAGES)
        val messageBundles: List<Bundle> = when (rawMessages) {
            is Array<*> -> rawMessages.filterIsInstance<Bundle>()
            is Iterable<*> -> rawMessages.filterIsInstance<Bundle>()
            else -> emptyList()
        }

        if (messageBundles.isNotEmpty()) {
            val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) ||
                    !conversationTitle.isNullOrBlank()

            for (bundle in messageBundles) {
                val msgText = bundle.getCharSequence("text")?.toString()?.trim() ?: continue
                if (msgText.isBlank() || isIgnorableSystemText(msgText)) continue

                var sender: String? = bundle.getCharSequence("sender")?.toString()?.trim()
                if (sender.isNullOrBlank()) {
                    sender = extractSenderPersonName(bundle)
                }
                if (sender.isNullOrBlank()) {
                    sender = if (!isGroup) defaultTitle else null
                }
                if (sender.isNullOrBlank()) {
                    sender = defaultTitle ?: "Unknown"
                }

                val time = bundle.getLong("time", fallbackPostTime)
                val isDeletion = isDeletionNotice(msgText)
                val mimeType = bundle.getString("type") ?: bundle.getString("data_mime_type")
                val attachment = mimeType ?: detectAttachment(msgText)
                val msgType = resolveMessageType(mimeType, attachment, msgText)

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
                        isGroupMessage = isGroup,
                        attachmentDescription = attachment,
                        contentAvailable = true,
                        isDeletedBySender = isDeletion,
                        deletedNoticeTimestamp = if (isDeletion) (if (time > 0) time else fallbackPostTime) else null,
                        messageType = msgType,
                        isPartialCapture = false
                    )
                )
            }

            if (messagesList.isNotEmpty()) {
                return messagesList
            }
        }

        // Strategy B: AndroidX NotificationCompat extraction
        try {
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
                    val isDeletion = isDeletionNotice(mText)
                    val attachment = m.dataMimeType ?: detectAttachment(mText)
                    val msgType = resolveMessageType(m.dataMimeType, attachment, mText)

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
                            attachmentDescription = attachment,
                            contentAvailable = true,
                            isDeletedBySender = isDeletion,
                            deletedNoticeTimestamp = if (isDeletion) time else null,
                            messageType = msgType,
                            isPartialCapture = false
                        )
                    )
                }
                if (messagesList.isNotEmpty()) {
                    return messagesList
                }
            }
        } catch (ignored: Exception) {}

        return emptyList()
    }

    private fun extractSenderPersonName(bundle: Bundle): String? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val person = bundle.getParcelable<android.app.Person>("sender_person")
                if (person != null) {
                    val name = person.name?.toString()?.trim()
                    if (!name.isNullOrBlank()) return name
                }
            }
        } catch (ignored: Exception) {}

        try {
            val personBundle = bundle.getBundle("sender_person")
            if (personBundle != null) {
                val name = personBundle.getCharSequence("name")?.toString()?.trim()
                if (!name.isNullOrBlank()) return name
            }
        } catch (ignored: Exception) {}

        return null
    }

    private fun detectAttachment(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("photo") || lower.contains("image") || lower.contains("📷") || lower.contains("🖼️") -> "Photo"
            lower.contains("video") || lower.contains("🎥") || lower.contains("📹") -> "Video"
            lower.contains("voice message") || lower.contains("audio") || lower.contains("🎙️") || lower.contains("🎤") -> "Voice Message"
            lower.contains("sticker") || lower.contains("gif") -> "Sticker/GIF"
            lower.contains("document") || lower.contains("file") || lower.contains("pdf") -> "Document"
            lower.contains("location") || lower.contains("pinned location") || lower.contains("📍") -> "Location"
            lower.contains("contact") || lower.contains("vcard") -> "Contact Card"
            else -> null
        }
    }

    fun isIgnorableSystemText(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower.contains("checking for new messages") ||
                lower.contains("whatsapp web is currently active") ||
                lower.contains("backup in progress") ||
                lower.contains("syncing mail") ||
                lower.contains("running in the background") ||
                lower.contains("tap for more info") ||
                lower.contains("connected to chat") ||
                lower.contains("connecting...")
    }

    fun isDeletionNotice(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = text.lowercase().trim()

        return lower == "this message was deleted" ||
                lower == "this message was deleted by the sender" ||
                lower == "you deleted this message" ||
                lower == "message was deleted" ||
                lower == "message deleted" ||
                lower == "message unsent" ||
                lower == "unsent a message" ||
                lower == "this message was unsent" ||
                lower.endsWith("unsent a message") ||
                lower.endsWith("message was deleted") ||
                lower == "ce message a été supprimé" ||
                lower == "este mensaje fue eliminado" ||
                lower == "dieser beitrag wurde gelöscht" ||
                lower == "questo messaggio è stato eliminato" ||
                lower == "mensagem apagada"
    }

    fun isGenericPlaceholderText(text: String?): Boolean {
        if (text.isNullOrBlank()) return true
        val lower = text.lowercase().trim()
        return lower == "new message" ||
                lower == "new messages" ||
                lower == "1 new message" ||
                lower == "incoming message" ||
                lower == "message" ||
                lower == "new chat" ||
                lower.matches(Regex("\\d+\\s+new\\s+messages?")) ||
                lower.matches(Regex("\\d+\\s+messages?\\s+from\\s+\\d+\\s+chats?"))
    }

    fun resolveMessageType(mimeType: String?, attachmentDesc: String?, text: String?): String {
        val mime = mimeType?.lowercase() ?: ""
        if (mime.startsWith("image/")) return "IMAGE"
        if (mime.startsWith("video/")) return "VIDEO"
        if (mime.startsWith("audio/")) return "AUDIO"
        if (mime.contains("pdf") || mime.contains("document") || mime.contains("file")) return "DOCUMENT"

        val combined = "${attachmentDesc ?: ""} ${text ?: ""}".lowercase()
        return when {
            combined.contains("photo") || combined.contains("image") || combined.contains("picture") || combined.contains("📷") || combined.contains("🖼️") -> "IMAGE"
            combined.contains("video") || combined.contains("movie") || combined.contains("🎥") || combined.contains("📹") -> "VIDEO"
            combined.contains("voice message") || combined.contains("audio") || combined.contains("sound") || combined.contains("🎙️") || combined.contains("🎤") -> "AUDIO"
            combined.contains("pdf") || combined.contains("document") || combined.contains("file") || combined.contains("sheet") -> "DOCUMENT"
            combined.contains("sticker") || combined.contains("gif") -> "STICKER"
            combined.contains("location") || combined.contains("map") || combined.contains("pinned") || combined.contains("📍") -> "LOCATION"
            combined.contains("contact") || combined.contains("vcard") -> "CONTACT"
            else -> "TEXT"
        }
    }
}
