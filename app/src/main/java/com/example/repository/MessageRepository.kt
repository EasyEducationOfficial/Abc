package com.example.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.MessageDao
import com.example.data.local.MessageEntity
import com.example.data.model.ColumnInfo
import com.example.data.model.DatabaseStats
import com.example.data.preferences.RetentionPolicy
import com.example.ui.components.formatFullDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MessageRepository(
    private val messageDao: MessageDao,
    private val appDatabase: AppDatabase? = null
) {

    val allMessages: Flow<List<MessageEntity>> = messageDao.getAllMessages()
    val recentMessages: Flow<List<MessageEntity>> = messageDao.getRecentMessages(limit = 10)
    val favoriteMessages: Flow<List<MessageEntity>> = messageDao.getFavorites()
    val totalCount: Flow<Int> = messageDao.getTotalCount()

    fun getTodayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    val countToday: Flow<Int> = messageDao.getCountSince(getTodayStartMillis())

    fun searchMessages(query: String): Flow<List<MessageEntity>> {
        return messageDao.searchMessages(query)
    }

    fun getMessageById(id: Long): Flow<MessageEntity?> {
        return messageDao.getMessageById(id)
    }

    suspend fun getMessageByIdDirect(id: Long): MessageEntity? {
        return messageDao.getMessageByIdDirect(id)
    }

    suspend fun getAllMessagesList(): List<MessageEntity> {
        return messageDao.getAllMessagesList()
    }

    fun getMessagesByApp(pkg: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesByApp(pkg)
    }

    suspend fun insertMessage(message: MessageEntity): Long {
        return messageDao.insertMessage(message)
    }

    suspend fun toggleFavorite(id: Long, current: Boolean) {
        messageDao.updateFavorite(id, !current)
    }

    suspend fun deleteById(id: Long) {
        messageDao.deleteById(id)
    }

    suspend fun deleteByIds(ids: List<Long>) {
        messageDao.deleteByIds(ids)
    }

    suspend fun deleteAll() {
        messageDao.deleteAll()
    }

    suspend fun applyRetentionPolicy(policy: RetentionPolicy): Int {
        if (policy == RetentionPolicy.FOREVER) return 0
        val cutoff = System.currentTimeMillis() - (policy.days * 24L * 60L * 60L * 1000L)
        return messageDao.deleteOlderThan(cutoff)
    }

    suspend fun getDatabaseStats(context: Context): DatabaseStats = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("msgvault.db")
        val sizeBytes = if (dbFile.exists()) dbFile.length() else 0L

        val columns = mutableListOf<ColumnInfo>()
        var pageSize = 4096L
        var pageCount = 0L
        var integrity = "OK"
        var totalRecords = 0

        val db = appDatabase ?: AppDatabase.getInstance(context)
        try {
            val sqliteDb = db.openHelper.readableDatabase

            // Inspect columns via PRAGMA table_info(messages)
            sqliteDb.query("PRAGMA table_info(messages)").use { cursor ->
                val cidIdx = cursor.getColumnIndex("cid")
                val nameIdx = cursor.getColumnIndex("name")
                val typeIdx = cursor.getColumnIndex("type")
                val notnullIdx = cursor.getColumnIndex("notnull")
                val dfltIdx = cursor.getColumnIndex("dflt_value")
                val pkIdx = cursor.getColumnIndex("pk")

                while (cursor.moveToNext()) {
                    columns.add(
                        ColumnInfo(
                            cid = if (cidIdx >= 0) cursor.getInt(cidIdx) else 0,
                            name = if (nameIdx >= 0) cursor.getString(nameIdx) else "",
                            type = if (typeIdx >= 0) cursor.getString(typeIdx) else "",
                            notNull = if (notnullIdx >= 0) cursor.getInt(notnullIdx) == 1 else false,
                            defaultValue = if (dfltIdx >= 0) cursor.getString(dfltIdx) else null,
                            primaryKey = if (pkIdx >= 0) cursor.getInt(pkIdx) == 1 else false
                        )
                    )
                }
            }

            sqliteDb.query("PRAGMA page_size").use { cursor ->
                if (cursor.moveToNext()) pageSize = cursor.getLong(0)
            }

            sqliteDb.query("PRAGMA page_count").use { cursor ->
                if (cursor.moveToNext()) pageCount = cursor.getLong(0)
            }

            try {
                sqliteDb.query("PRAGMA integrity_check").use { cursor ->
                    if (cursor.moveToNext()) integrity = cursor.getString(0)
                }
            } catch (e: Exception) {
                integrity = "Integrity check exception: ${e.message}"
            }

            sqliteDb.query("SELECT COUNT(*) FROM messages").use { cursor ->
                if (cursor.moveToNext()) totalRecords = cursor.getInt(0)
            }
        } catch (e: Exception) {
            integrity = "Error reading DB: ${e.message}"
        }

        DatabaseStats(
            dbPath = dbFile.absolutePath,
            fileSizeBytes = sizeBytes,
            totalRecords = totalRecords,
            pageSize = pageSize,
            pageCount = pageCount,
            integrityCheck = integrity,
            columns = columns
        )
    }

    suspend fun vacuumDatabase(context: Context) = withContext(Dispatchers.IO) {
        val db = appDatabase ?: AppDatabase.getInstance(context)
        val writable = db.openHelper.writableDatabase
        writable.execSQL("VACUUM")
    }

    suspend fun exportMessagesToJson(context: Context): File = withContext(Dispatchers.IO) {
        val messages = messageDao.getAllMessagesList()

        val root = JSONObject()
        root.put("appName", "MsgVault")
        root.put("version", "1.0.0")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("exportedAtFormatted", formatFullDateTime(System.currentTimeMillis()))
        root.put("totalMessages", messages.size)

        val jsonArray = JSONArray()
        for (msg in messages) {
            val obj = JSONObject()
            obj.put("id", msg.id)
            obj.put("sourcePackage", msg.sourcePackage)
            obj.put("sourceAppName", msg.sourceAppName)
            obj.put("senderName", msg.senderName ?: JSONObject.NULL)
            obj.put("conversationTitle", msg.conversationTitle ?: JSONObject.NULL)
            obj.put("messageText", msg.messageText)
            obj.put("receivedAt", msg.receivedAt)
            obj.put("receivedAtFormatted", formatFullDateTime(msg.receivedAt))
            obj.put("capturedAt", msg.capturedAt)
            obj.put("notificationKey", msg.notificationKey ?: JSONObject.NULL)
            obj.put("isGroupMessage", msg.isGroupMessage)
            obj.put("isFavorite", msg.isFavorite)
            obj.put("isRead", msg.isRead)
            obj.put("messageCategory", msg.messageCategory)
            obj.put("attachmentDescription", msg.attachmentDescription ?: JSONObject.NULL)
            obj.put("contentAvailable", msg.contentAvailable)
            obj.put("isDeletedBySender", msg.isDeletedBySender)
            obj.put("deletedNoticeTimestamp", msg.deletedNoticeTimestamp ?: JSONObject.NULL)
            jsonArray.put(obj)
        }
        root.put("messages", jsonArray)

        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val filename = "msgvault_backup_${dateFormat.format(Date())}.json"

        val exportDir = context.getExternalFilesDir("backups") ?: File(context.filesDir, "backups")
        if (!exportDir.exists()) exportDir.mkdirs()

        val file = File(exportDir, filename)
        file.writeText(root.toString(2))
        file
    }
}
