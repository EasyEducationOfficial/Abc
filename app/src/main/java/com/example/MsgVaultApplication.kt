package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.preferences.AppPreferences
import com.example.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MsgVaultApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var messageRepository: MessageRepository
        private set

    lateinit var appPreferences: AppPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        messageRepository = MessageRepository(database.messageDao(), database)
        appPreferences = AppPreferences(this)

        // Apply automatic retention policy cleanup asynchronously on app launch
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val policy = appPreferences.retentionPolicy.first()
                messageRepository.applyRetentionPolicy(policy)
            } catch (e: Exception) {
                // Safeguard against launch crashes
            }
        }
    }
}
