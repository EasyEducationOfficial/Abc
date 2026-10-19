package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.SupportedAppRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "msgvault_settings")

enum class RetentionPolicy(val days: Int, val label: String) {
    FOREVER(-1, "Keep Forever"),
    DAYS_7(7, "7 Days"),
    DAYS_30(30, "30 Days"),
    DAYS_90(90, "90 Days")
}

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_MONITORED_PACKAGES = stringSetPreferencesKey("monitored_packages")
        private val KEY_RETENTION_POLICY = stringPreferencesKey("retention_policy")
        private val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val KEY_APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")
        private val KEY_BIOMETRICS_ENABLED = booleanPreferencesKey("biometrics_enabled")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_MONITOR_GENERIC_APPS = booleanPreferencesKey("monitor_generic_apps")
        private val KEY_MONITOR_ALL_NOTIFICATIONS = booleanPreferencesKey("monitor_all_notifications")

        val DEFAULT_MONITORED_PACKAGES = SupportedAppRegistry.ALL_SUPPORTED_APPS.map { it.packageName }.toSet()
    }

    private val dataStoreFlow: Flow<Preferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    val monitoredPackages: Flow<Set<String>> = dataStoreFlow.map { prefs ->
        prefs[KEY_MONITORED_PACKAGES] ?: DEFAULT_MONITORED_PACKAGES
    }

    val retentionPolicy: Flow<RetentionPolicy> = dataStoreFlow.map { prefs ->
        val name = prefs[KEY_RETENTION_POLICY] ?: RetentionPolicy.FOREVER.name
        try {
            RetentionPolicy.valueOf(name)
        } catch (e: Exception) {
            RetentionPolicy.FOREVER
        }
    }

    val isAppLockEnabled: Flow<Boolean> = dataStoreFlow.map { prefs ->
        prefs[KEY_APP_LOCK_ENABLED] ?: false
    }

    val appLockPin: Flow<String?> = dataStoreFlow.map { prefs ->
        prefs[KEY_APP_LOCK_PIN]
    }

    val isBiometricsEnabled: Flow<Boolean> = dataStoreFlow.map { prefs ->
        prefs[KEY_BIOMETRICS_ENABLED] ?: false
    }

    val isOnboardingCompleted: Flow<Boolean> = dataStoreFlow.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val isGenericAppsMonitoringEnabled: Flow<Boolean> = dataStoreFlow.map { prefs ->
        prefs[KEY_MONITOR_GENERIC_APPS] ?: true
    }

    val isAllNotificationsMonitoringEnabled: Flow<Boolean> = dataStoreFlow.map { prefs ->
        prefs[KEY_MONITOR_ALL_NOTIFICATIONS] ?: false
    }

    suspend fun setMonitoredPackages(packages: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MONITORED_PACKAGES] = packages
        }
    }

    suspend fun toggleAppMonitoring(packageName: String, enable: Boolean) {
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_MONITORED_PACKAGES] ?: DEFAULT_MONITORED_PACKAGES).toMutableSet()
            if (enable) {
                current.add(packageName)
            } else {
                current.remove(packageName)
            }
            prefs[KEY_MONITORED_PACKAGES] = current
        }
    }

    suspend fun setAllAppsMonitoring(enable: Boolean) {
        context.dataStore.edit { prefs ->
            if (enable) {
                prefs[KEY_MONITORED_PACKAGES] = DEFAULT_MONITORED_PACKAGES
            } else {
                prefs[KEY_MONITORED_PACKAGES] = emptySet()
            }
        }
    }

    suspend fun setRetentionPolicy(policy: RetentionPolicy) {
        context.dataStore.edit { prefs ->
            prefs[KEY_RETENTION_POLICY] = policy.name
        }
    }

    suspend fun setAppLock(enabled: Boolean, pin: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_LOCK_ENABLED] = enabled
            if (pin != null) {
                prefs[KEY_APP_LOCK_PIN] = pin
            } else if (!enabled) {
                prefs.remove(KEY_APP_LOCK_PIN)
            }
        }
    }

    suspend fun setBiometricsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BIOMETRICS_ENABLED] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setGenericAppsMonitoring(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MONITOR_GENERIC_APPS] = enabled
        }
    }

    suspend fun setAllNotificationsMonitoring(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MONITOR_ALL_NOTIFICATIONS] = enabled
        }
    }
}
