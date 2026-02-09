package dev.rcht.jist

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dev.rcht.jist.data.db.JistDatabase
import dev.rcht.jist.data.preferences.PreferencesRepository
import dev.rcht.jist.data.repository.AppRuleRepository
import dev.rcht.jist.data.repository.LlmConfigRepository
import dev.rcht.jist.data.repository.NotificationRepository
import dev.rcht.jist.data.repository.SummaryRepository

class JistApplication : Application() {
    
    // Database
    lateinit var database: JistDatabase
    
    // Repositories
    lateinit var notificationRepository: NotificationRepository
    lateinit var summaryRepository: SummaryRepository
    lateinit var appRuleRepository: AppRuleRepository
    lateinit var llmConfigRepository: LlmConfigRepository
    lateinit var preferencesRepository: PreferencesRepository
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize database
        database = JistDatabase.getInstance(this)
        
        // Initialize repositories
        notificationRepository = NotificationRepository(database.notificationDao())
        summaryRepository = SummaryRepository(database.summaryDao())
        appRuleRepository = AppRuleRepository(database.appRuleDao())
        llmConfigRepository = LlmConfigRepository(database.llmConfigDao())
        preferencesRepository = PreferencesRepository(this)
        
        // Create notification channels
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            
            // Summaries channel
            val summariesChannel = NotificationChannel(
                CHANNEL_SUMMARIES,
                "Notification Summaries",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Summary notifications from Jist"
            }
            notificationManager.createNotificationChannel(summariesChannel)
            
            // Prompt channel (for "Summarize" buttons)
            val promptChannel = NotificationChannel(
                CHANNEL_SUMMARIZE_PROMPT,
                "Summarize Prompts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Tap to summarize notifications"
            }
            notificationManager.createNotificationChannel(promptChannel)
            
            // Service channel
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "Jist Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Jist notification listener service"
            }
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }
    
    companion object {
        const val CHANNEL_SUMMARIES = "jist_summaries"
        const val CHANNEL_SUMMARIZE_PROMPT = "jist_summarize_prompt"
        const val CHANNEL_SERVICE = "jist_service"
    }
}
