package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_vault",
    indices = [
        Index(value = ["mediaType"]),
        Index(value = ["sourcePackage"]),
        Index(value = ["capturedAt"]),
        Index(value = ["isFavorite"]),
        Index(value = ["linkedMessageId"])
    ]
)
data class MediaVaultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalFileName: String,
    val localFilePath: String,
    val mediaType: String, // "IMAGE", "VIDEO", "AUDIO", "DOCUMENT"
    val mimeType: String,
    val fileSizeBytes: Long,
    val sourcePackage: String,
    val sourceAppName: String,
    val senderName: String? = null,
    val capturedAt: Long = System.currentTimeMillis(),
    val linkedMessageId: Long? = null,
    val isFavorite: Boolean = false,
    val durationMs: Long = 0L,
    val sha256Checksum: String? = null
)
