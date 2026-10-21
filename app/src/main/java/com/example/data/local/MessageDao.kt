package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages ORDER BY receivedAt DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY receivedAt DESC")
    suspend fun getAllMessagesList(): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY receivedAt DESC LIMIT :limit")
    fun getRecentMessages(limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isFavorite = 1 ORDER BY receivedAt DESC")
    fun getFavorites(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sourcePackage = :pkg ORDER BY receivedAt DESC")
    fun getMessagesByApp(pkg: String): Flow<List<MessageEntity>>

    @Query("""
        SELECT * FROM messages 
        WHERE messageText LIKE '%' || :query || '%' 
           OR senderName LIKE '%' || :query || '%' 
           OR sourceAppName LIKE '%' || :query || '%'
           OR conversationTitle LIKE '%' || :query || '%'
        ORDER BY receivedAt DESC
    """)
    fun searchMessages(query: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    fun getMessageById(id: Long): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageByIdDirect(id: Long): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("UPDATE messages SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE messages SET isRead = :isRead WHERE id = :id")
    suspend fun updateRead(id: Long, isRead: Boolean)

    @Query("UPDATE messages SET mediaFilePath = :mediaPath, mediaMimeType = :mimeType, mediaFileSize = :fileSize WHERE id = :id")
    suspend fun updateMediaAttachment(id: Long, mediaPath: String, mimeType: String, fileSize: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("DELETE FROM messages WHERE receivedAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Query("SELECT COUNT(*) FROM messages")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE receivedAt >= :sinceMillis")
    fun getCountSince(sinceMillis: Long): Flow<Int>

    @Query("UPDATE messages SET isDeletedBySender = 1, deletedNoticeTimestamp = :deletedTimestamp WHERE id = :id")
    suspend fun markDeletedBySender(id: Long, deletedTimestamp: Long)

    @Query("""
        SELECT * FROM messages 
        WHERE sourcePackage = :sourcePackage 
          AND ((:senderName IS NULL AND senderName IS NULL) OR senderName = :senderName)
          AND isDeletedBySender = 0
        ORDER BY receivedAt DESC 
        LIMIT 1
    """)
    suspend fun findLatestMessageForSender(sourcePackage: String, senderName: String?): MessageEntity?

    @Query("""
        SELECT * FROM messages 
        WHERE sourcePackage = :sourcePackage 
          AND ((:senderName IS NULL AND senderName IS NULL) OR senderName = :senderName)
          AND receivedAt >= :minTimestamp
        ORDER BY receivedAt DESC 
        LIMIT 1
    """)
    suspend fun findRecentMessageForSender(sourcePackage: String, senderName: String?, minTimestamp: Long): MessageEntity?

    @Query("""
        SELECT * FROM messages 
        WHERE sourcePackage = :sourcePackage 
          AND notificationKey = :notificationKey 
          AND messageText = :messageText 
          AND receivedAt = :receivedAt
        LIMIT 1
    """)
    suspend fun findExactNotificationDuplicate(
        sourcePackage: String,
        notificationKey: String?,
        messageText: String,
        receivedAt: Long
    ): MessageEntity?

    @Query("""
        SELECT * FROM messages 
        WHERE sourcePackage = :sourcePackage 
          AND ((:senderName IS NULL AND senderName IS NULL) OR senderName = :senderName)
          AND messageText = :messageText 
          AND capturedAt >= :minTimestamp 
        LIMIT 1
    """)
    suspend fun findRecentDuplicate(
        sourcePackage: String,
        senderName: String?,
        messageText: String,
        minTimestamp: Long
    ): MessageEntity?

    @Query("SELECT DISTINCT sourcePackage FROM messages")
    fun getDistinctApps(): Flow<List<String>>
}
