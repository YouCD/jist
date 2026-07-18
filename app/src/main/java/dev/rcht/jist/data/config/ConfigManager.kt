package dev.rcht.jist.data.config

import android.content.Context
import android.net.Uri
import dev.rcht.jist.data.preferences.JistPreferences
import dev.rcht.jist.data.preferences.PreferencesRepository
import dev.rcht.jist.data.repository.AppRuleRepository
import dev.rcht.jist.data.repository.ChatSourceRepository
import dev.rcht.jist.data.repository.CustomPromptRepository
import dev.rcht.jist.data.repository.LlmConfigRepository
import dev.rcht.jist.data.repository.WatchedChatRepository
import dev.rcht.jist.data.repository.WatchTopicRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

class ConfigManager(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val llmConfigRepository: LlmConfigRepository,
    private val appRuleRepository: AppRuleRepository,
    private val customPromptRepository: CustomPromptRepository,
    private val watchTopicRepository: WatchTopicRepository,
    private val chatSourceRepository: ChatSourceRepository,
    private val watchedChatRepository: WatchedChatRepository
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportToJson(): String {
        val prefs = preferencesRepository.preferencesFlow.first()
        val llmConfigs = llmConfigRepository.getAll().map { it.toDto() }
        val appRules = appRuleRepository.getAll().map { it.toDto() }
        val customPrompts = customPromptRepository.getAll().map { it.toDto() }
        val watchTopics = watchTopicRepository.getAll().map { it.toDto() }
        val chatSources = chatSourceRepository.getAll().map { it.toDto() }
        val watchedChats = watchedChatRepository.getAll().map { wc ->
            val src = chatSourceRepository.getAll().find { it.id == wc.sourceId }
            wc.toDto(src?.packageName ?: "")
        }

        val data = ConfigExportData(
            preferences = prefs,
            llmConfigs = llmConfigs,
            appRules = appRules,
            customPrompts = customPrompts,
            watchTopics = watchTopics,
            chatSources = chatSources,
            watchedChats = watchedChats
        )
        return json.encodeToString(ConfigExportData.serializer(), data)
    }

    suspend fun importFromJson(jsonString: String): Result<Unit> {
        return try {
            val data = json.decodeFromString(ConfigExportData.serializer(), jsonString)
            importData(data)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromUri(uri: Uri): Result<Unit> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Cannot open file"))
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.readText()
            reader.close()
            importFromJson(jsonString)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportToUri(uri: Uri): Result<Unit> {
        return try {
            val jsonString = exportToJson()
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: return Result.failure(Exception("Cannot open file"))
            outputStream.write(jsonString.toByteArray())
            outputStream.close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun importData(data: ConfigExportData) {
        data.preferences?.let { prefs ->
            preferencesRepository.replaceAll(prefs)
        }

        if (data.llmConfigs.isNotEmpty()) {
            llmConfigRepository.replaceAll(data.llmConfigs.map { it.toEntity() })
        }

        if (data.appRules.isNotEmpty()) {
            appRuleRepository.replaceAll(data.appRules.map { it.toEntity() })
        }

        if (data.customPrompts.isNotEmpty()) {
            customPromptRepository.replaceAll(data.customPrompts.map { it.toEntity() })
        }

        if (data.watchTopics.isNotEmpty()) {
            watchTopicRepository.replaceAll(data.watchTopics.map { it.toEntity() })
        }

        if (data.chatSources.isNotEmpty()) {
            chatSourceRepository.replaceAll(data.chatSources.map { it.toEntity() })
        }

        // Import chat sources (getOrCreate to avoid deleting existing)
        for (dto in data.chatSources) {
            chatSourceRepository.getOrCreate(dto.packageName, dto.displayName)
        }

        // Import watched chats (update existing or insert new)
        for (wc in data.watchedChats) {
            val pkg = wc.sourcePkg.ifBlank { continue }
            val src = chatSourceRepository.getByPackageName(pkg) ?: continue
            val existing = watchedChatRepository.getByChatKey(src.id, wc.chatId)
            if (existing != null) {
                watchedChatRepository.update(existing.copy(
                    chatName = wc.chatName,
                    isEnabled = wc.isEnabled,
                    isSummarized = wc.isSummarized,
                    customPrompt = wc.customPrompt,
                    minMessagesForSummary = wc.minMessagesForSummary,
                    retentionDays = wc.retentionDays
                ))
            } else {
                watchedChatRepository.insert(wc.toEntity(src.id))
            }
        }
    }
}
