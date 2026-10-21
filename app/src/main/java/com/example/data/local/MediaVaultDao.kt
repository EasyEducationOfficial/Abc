package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaVaultDao {

    @Query("SELECT * FROM media_vault ORDER BY capturedAt DESC")
    fun getAllMedia(): Flow<List<MediaVaultEntity>>

    @Query("SELECT * FROM media_vault WHERE mediaType = :type ORDER BY capturedAt DESC")
    fun getMediaByType(type: String): Flow<List<MediaVaultEntity>>

    @Query("SELECT * FROM media_vault WHERE isFavorite = 1 ORDER BY capturedAt DESC")
    fun getFavoriteMedia(): Flow<List<MediaVaultEntity>>

    @Query("SELECT * FROM media_vault WHERE id = :id LIMIT 1")
    fun getMediaById(id: Long): Flow<MediaVaultEntity?>

    @Query("SELECT * FROM media_vault WHERE id = :id LIMIT 1")
    suspend fun getMediaByIdDirect(id: Long): MediaVaultEntity?

    @Query("SELECT * FROM media_vault WHERE localFilePath = :path LIMIT 1")
    suspend fun findByLocalPath(path: String): MediaVaultEntity?

    @Query("SELECT * FROM media_vault WHERE sha256Checksum = :checksum LIMIT 1")
    suspend fun findByChecksum(checksum: String): MediaVaultEntity?

    @Query("""
        SELECT * FROM media_vault 
        WHERE sourcePackage = :sourcePackage 
          AND capturedAt >= :minTimestamp 
        ORDER BY capturedAt DESC 
        LIMIT 1
    """)
    suspend fun findRecentMediaForPackage(sourcePackage: String, minTimestamp: Long): MediaVaultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaVaultEntity): Long

    @Query("UPDATE media_vault SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE media_vault SET linkedMessageId = :messageId WHERE id = :id")
    suspend fun linkToMessage(id: Long, messageId: Long)

    @Query("DELETE FROM media_vault WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM media_vault")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM media_vault")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_vault WHERE mediaType = :type")
    fun getCountByType(type: String): Flow<Int>
}
