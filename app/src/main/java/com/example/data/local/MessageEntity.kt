package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["sourcePackage"]),
        Index(value = ["receivedAt"]),
        Index(value = ["isFavorite"]),
        Index(value = ["notificationKey"]),
        Index(value = ["senderName"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourcePackage: String,
    val sourceAppName: String,
    val senderName: String? = null,
    val conversationTitle: String? = null,
    val messageText: String,
    val receivedAt: Long = System.currentTimeMillis(),
    val capturedAt: Long = System.currentTimeMillis(),
    val conversationId: String? = null,
    val notificationKey: String? = null,
    val isGroupMessage: Boolean = false,
    val isFavorite: Boolean = false,
    val isRead: Boolean = false,
    val messageCategory: String = "CHAT",
    val attachmentDescription: String? = null,
    val contentAvailable: Boolean = true,
    val isDeletedBySender: Boolean = false,
    val deletedNoticeTimestamp: Long? = null
)
