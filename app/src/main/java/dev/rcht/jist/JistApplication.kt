package dev.rcht.jist

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dev.rcht.jist.data.db.JistDatabase
import dev.rcht.jist.data.db.seeding.DatabaseSeeder
import dev.rcht.jist.data.preferences.PreferencesRepository
import dev.rcht.jist.data.repository.AppRuleRepository
import dev.rcht.jist.data.repository.LlmConfigRepository
import dev.rcht.jist.data.repository.NotificationRepository
import dev.rcht.jist.data.repository.SummaryRepository
import dev.rcht.jist.engine.SummaryEngine
import dev.rcht.jist.worker.SummaryWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class JistApplication : Application() {
    
    // Database
    lateinit var database: JistDatabase
    
    // HTTP Client
    lateinit var httpClient: OkHttpClient
    
    // Repositories
    lateinit var notificationRepository: NotificationRepository
    lateinit var summaryRepository: SummaryRepository
    lateinit var appRuleRepository: AppRuleRepository
    lateinit var llmConfigRepository: LlmConfigRepository
    lateinit var preferencesRepository: PreferencesRepository
    lateinit var customPromptRepository: dev.rcht.jist.data.repository.CustomPromptRepository

    // Engines
    lateinit var summaryEngine: SummaryEngine
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize database
        database = JistDatabase.getInstance(this)
        
        // Seed database on first launch (synchronously to ensure it completes before UI loads)
        runBlocking {
            DatabaseSeeder.seedIfNeeded(this@JistApplication, database)
        }
        
        // Initialize HTTP client with timeouts
        httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        
        // Initialize repositories
        notificationRepository = NotificationRepository(database.notificationDao())
        summaryRepository = SummaryRepository(database.summaryDao())
        appRuleRepository = AppRuleRepository(database.appRuleDao())
        llmConfigRepository = LlmConfigRepository(database.llmConfigDao())
        customPromptRepository = dev.rcht.jist.data.repository.CustomPromptRepository(database.customPromptDao())
        preferencesRepository = PreferencesRepository(this)

        // Ensure onboarding is shown when DB has no app rules even if preferences say complete (first-run recovery)
        runBlocking {
            try {
                val prefs = preferencesRepository.preferencesFlow.first()
                val rules = appRuleRepository.getAll()
                if (prefs.isOnboardingComplete && rules.isEmpty()) {
                    preferencesRepository.setOnboardingComplete(false)
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // Initialize engines
        summaryEngine = SummaryEngine(
            this,
            notificationRepository,
            summaryRepository,
            llmConfigRepository,
            appRuleRepository,
            preferencesRepository,
            httpClient
        )
        
        // Schedule periodic summarization
        SummaryWorker.schedule(this)
        
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
