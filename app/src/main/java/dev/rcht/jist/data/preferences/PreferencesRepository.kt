package dev.rcht.jist.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jist_prefs")

class PreferencesRepository(private val context: Context) {
    
    private object PreferenceKeys {
        val IS_ONBOARDING_COMPLETE = booleanPreferencesKey("is_onboarding_complete")
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val DEFAULT_BATCH_WINDOW = intPreferencesKey("default_batch_window")
        val DEFAULT_MIN_MESSAGES = intPreferencesKey("default_min_messages")
        val AUTO_DELETE_NOTIFICATIONS_DAYS = intPreferencesKey("auto_delete_notifications_days")
        val AUTO_DELETE_SUMMARIES_DAYS = intPreferencesKey("auto_delete_summaries_days")
        val DELETE_RAW_AFTER_SUMMARIZING = booleanPreferencesKey("delete_raw_after_summarizing")
        val THEME = stringPreferencesKey("theme")
        val SUMMARY_NOTIFICATION_SOUND = booleanPreferencesKey("summary_notification_sound")
        val SUMMARY_NOTIFICATION_VIBRATE = booleanPreferencesKey("summary_notification_vibrate")
        val WRITING_STYLE = stringPreferencesKey("writing_style")
        val SUMMARY_TONE = stringPreferencesKey("summary_tone")
        val SUMMARY_LENGTH = stringPreferencesKey("summary_length")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }
    
    val preferencesFlow: Flow<JistPreferences> = context.dataStore.data.map { preferences ->
        JistPreferences(
            isOnboardingComplete = preferences[PreferenceKeys.IS_ONBOARDING_COMPLETE] ?: false,
            defaultMode = preferences[PreferenceKeys.DEFAULT_MODE] ?: "AUTO",
            defaultBatchWindowMinutes = preferences[PreferenceKeys.DEFAULT_BATCH_WINDOW] ?: 15,
            defaultMinMessages = preferences[PreferenceKeys.DEFAULT_MIN_MESSAGES] ?: 3,
            autoDeleteNotificationsAfterDays = preferences[PreferenceKeys.AUTO_DELETE_NOTIFICATIONS_DAYS] ?: 7,
            autoDeleteSummariesAfterDays = preferences[PreferenceKeys.AUTO_DELETE_SUMMARIES_DAYS] ?: 30,
            deleteRawAfterSummarizing = preferences[PreferenceKeys.DELETE_RAW_AFTER_SUMMARIZING] ?: false,
            theme = preferences[PreferenceKeys.THEME] ?: "SYSTEM",
            summaryNotificationSound = preferences[PreferenceKeys.SUMMARY_NOTIFICATION_SOUND] ?: true,
            summaryNotificationVibrate = preferences[PreferenceKeys.SUMMARY_NOTIFICATION_VIBRATE] ?: true,
            writingStyle = preferences[PreferenceKeys.WRITING_STYLE] ?: "CONCISE",
            summaryTone = preferences[PreferenceKeys.SUMMARY_TONE] ?: "PROFESSIONAL",
            summaryLength = preferences[PreferenceKeys.SUMMARY_LENGTH] ?: "MEDIUM",
            notificationsEnabled = preferences[PreferenceKeys.NOTIFICATIONS_ENABLED] ?: true
        )
    }
    
    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.IS_ONBOARDING_COMPLETE] = complete
        }
    }
    
    suspend fun setDefaultMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DEFAULT_MODE] = mode
        }
    }
    
    suspend fun setDefaultBatchWindow(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DEFAULT_BATCH_WINDOW] = minutes
        }
    }
    
    suspend fun setDefaultMinMessages(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DEFAULT_MIN_MESSAGES] = count
        }
    }
    
    suspend fun setTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.THEME] = theme
        }
    }

    suspend fun setWritingStyle(style: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.WRITING_STYLE] = style
        }
    }

    suspend fun setSummaryTone(tone: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.SUMMARY_TONE] = tone
        }
    }

    suspend fun setSummaryLength(length: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.SUMMARY_LENGTH] = length
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun replaceAll(prefs: JistPreferences) {
        context.dataStore.edit { preferences ->
            preferences.clear()
            preferences[PreferenceKeys.IS_ONBOARDING_COMPLETE] = prefs.isOnboardingComplete
            preferences[PreferenceKeys.DEFAULT_MODE] = prefs.defaultMode
            preferences[PreferenceKeys.DEFAULT_BATCH_WINDOW] = prefs.defaultBatchWindowMinutes
            preferences[PreferenceKeys.DEFAULT_MIN_MESSAGES] = prefs.defaultMinMessages
            preferences[PreferenceKeys.AUTO_DELETE_NOTIFICATIONS_DAYS] = prefs.autoDeleteNotificationsAfterDays
            preferences[PreferenceKeys.AUTO_DELETE_SUMMARIES_DAYS] = prefs.autoDeleteSummariesAfterDays
            preferences[PreferenceKeys.DELETE_RAW_AFTER_SUMMARIZING] = prefs.deleteRawAfterSummarizing
            preferences[PreferenceKeys.THEME] = prefs.theme
            preferences[PreferenceKeys.SUMMARY_NOTIFICATION_SOUND] = prefs.summaryNotificationSound
            preferences[PreferenceKeys.SUMMARY_NOTIFICATION_VIBRATE] = prefs.summaryNotificationVibrate
            preferences[PreferenceKeys.WRITING_STYLE] = prefs.writingStyle
            preferences[PreferenceKeys.SUMMARY_TONE] = prefs.summaryTone
            preferences[PreferenceKeys.SUMMARY_LENGTH] = prefs.summaryLength
            preferences[PreferenceKeys.NOTIFICATIONS_ENABLED] = prefs.notificationsEnabled
        }
    }
}
