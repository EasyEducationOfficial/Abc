package com.example.repository

import com.example.data.local.AppDatabase
import com.example.data.local.MediaVaultEntity
import com.example.data.local.MessageEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

class MessageRepository(private val database: AppDatabase) {

    private val messageDao = database.messageDao()
    private val mediaVaultDao = database.mediaVaultDao()

    val allMessages: Flow<List<MessageEntity>> = messageDao.getAllMessages()
    val favorites: Flow<List<MessageEntity>> = messageDao.getFavorites()
    val totalCount: Flow<Int> = messageDao.getTotalCount()
    val distinctApps: Flow<List<String>> = messageDao.getDistinctApps()

    val allMedia: Flow<List<MediaVaultEntity>> = mediaVaultDao.getAllMedia()
    val favoriteMedia: Flow<List<MediaVaultEntity>> = mediaVaultDao.getFavoriteMedia()
    val totalMediaCount: Flow<Int> = mediaVaultDao.getTotalCount()

    fun getRecentMessages(limit: Int): Flow<List<MessageEntity>> = messageDao.getRecentMessages(limit)

    fun getMessagesByApp(packageName: String): Flow<List<MessageEntity>> = messageDao.getMessagesByApp(packageName)

    fun searchMessages(query: String): Flow<List<MessageEntity>> = messageDao.searchMessages(query)

    fun getMessageById(id: Long): Flow<MessageEntity?> = messageDao.getMessageById(id)

    suspend fun getMessageByIdDirect(id: Long): MessageEntity? = messageDao.getMessageByIdDirect(id)

    suspend fun insertMessage(message: MessageEntity): Long = messageDao.insertMessage(message)

    suspend fun toggleFavorite(message: MessageEntity) {
        messageDao.updateFavorite(message.id, !message.isFavorite)
    }

    suspend fun deleteMessage(id: Long) {
        messageDao.deleteById(id)
    }

    suspend fun deleteMessages(ids: List<Long>) {
        messageDao.deleteByIds(ids)
    }

    suspend fun clearAllMessages() {
        messageDao.deleteAll()
    }

    // Media Vault methods
    fun getMediaByType(type: String): Flow<List<MediaVaultEntity>> = mediaVaultDao.getMediaByType(type)

    fun getMediaCountByType(type: String): Flow<Int> = mediaVaultDao.getCountByType(type)

    suspend fun getMediaByIdDirect(id: Long): MediaVaultEntity? = mediaVaultDao.getMediaByIdDirect(id)

    suspend fun insertMedia(media: MediaVaultEntity): Long = mediaVaultDao.insertMedia(media)

    suspend fun toggleFavoriteMedia(media: MediaVaultEntity) {
        mediaVaultDao.updateFavorite(media.id, !media.isFavorite)
    }

    suspend fun deleteMedia(media: MediaVaultEntity) {
        // Also remove file from internal storage if requested
        try {
            val file = File(media.localFilePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (ignored: Exception) {}
        mediaVaultDao.deleteById(media.id)
    }

    suspend fun clearAllMedia() {
        mediaVaultDao.deleteAll()
    }
}
