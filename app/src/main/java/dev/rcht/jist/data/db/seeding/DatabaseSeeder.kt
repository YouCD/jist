package dev.rcht.jist.data.db.seeding

import android.content.Context
import android.content.SharedPreferences
import dev.rcht.jist.data.db.JistDatabase
import dev.rcht.jist.data.db.entity.AppRuleEntity
import dev.rcht.jist.data.db.entity.LlmConfigEntity

/**
 * Seeds initial database with default LLM config and app rules
 */
object DatabaseSeeder {

    private const val PREFS_NAME = "jist_seeding"
    private const val KEY_SEEDED = "database_seeded"

    /**
     * Seed database if not already seeded
     */
    suspend fun seedIfNeeded(context: Context, database: JistDatabase) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_SEEDED, false)) {
            seedDatabase(database, prefs)
        }
    }

    private suspend fun seedDatabase(database: JistDatabase, prefs: SharedPreferences) {
        try {
            // Seed default LLM config
            seedDefaultLlmConfig(database)

            // Seed common app rules
            // Disabled automatic seeding of app rules to ensure onboarding screen shows on first run/reinstall
            // seedCommonAppRules(database)

            // Mark as seeded
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun seedDefaultLlmConfig(database: JistDatabase) {
        try {
            val existingConfigs = database.llmConfigDao().getAll()
            if (existingConfigs.isEmpty()) {
                // Create a placeholder default config
                // User will need to add their actual API key
                val defaultConfig = LlmConfigEntity(
                    name = "Default Config",
                    provider = "OPENAI",
                    apiKey = "",
                    baseUrl = "",
                    modelId = "",
                    isDefault = true,
                    maxTokens = 1000,
                    temperature = 0.7f
                )
                database.llmConfigDao().insert(defaultConfig)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun seedCommonAppRules(database: JistDatabase) {
        try {
            val commonApps = listOf(
                AppRuleEntity(
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    enabled = true,
                    mode = "AUTO",
                    batchWindowMinutes = 15,
                    minMessagesForSummary = 3,
                    customPrompt = null
                ),
                AppRuleEntity(
                    packageName = "org.telegram.messenger",
                    appName = "Telegram",
                    enabled = true,
                    mode = "AUTO",
                    batchWindowMinutes = 15,
                    minMessagesForSummary = 3,
                    customPrompt = null
                ),
                AppRuleEntity(
                    packageName = "com.google.android.gm",
                    appName = "Gmail",
                    enabled = true,
                    mode = "MANUAL",
                    batchWindowMinutes = 30,
                    minMessagesForSummary = 2,
                    customPrompt = null
                ),
                AppRuleEntity(
                    packageName = "com.slack",
                    appName = "Slack",
                    enabled = true,
                    mode = "AUTO",
                    batchWindowMinutes = 10,
                    minMessagesForSummary = 2,
                    customPrompt = null
                ),
                AppRuleEntity(
                    packageName = "com.discord",
                    appName = "Discord",
                    enabled = true,
                    mode = "AUTO",
                    batchWindowMinutes = 15,
                    minMessagesForSummary = 3,
                    customPrompt = null
                ),
                AppRuleEntity(
                    packageName = "com.twitter.android",
                    appName = "Twitter",
                    enabled = true,
                    mode = "MANUAL",
                    batchWindowMinutes = 60,
                    minMessagesForSummary = 5,
                    customPrompt = null
                ),
                AppRuleEntity(
                    packageName = "com.instagram.android",
                    appName = "Instagram",
                    enabled = true,
                    mode = "MANUAL",
                    batchWindowMinutes = 30,
                    minMessagesForSummary = 3,
                    customPrompt = null
                ),
                AppRuleEntity(
                    packageName = "org.signal.android",
                    appName = "Signal",
                    enabled = true,
                    mode = "AUTO",
                    batchWindowMinutes = 15,
                    minMessagesForSummary = 3,
                    customPrompt = null
                )
            )

            for (appRule in commonApps) {
                val existing = database.appRuleDao().getForApp(appRule.packageName)
                if (existing == null) {
                    database.appRuleDao().insert(appRule)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
