package dev.rcht.jist.data.config

import dev.rcht.jist.data.db.entity.AppRuleEntity
import dev.rcht.jist.data.db.entity.ChatSourceEntity
import dev.rcht.jist.data.db.entity.CustomPromptEntity
import dev.rcht.jist.data.db.entity.LlmConfigEntity
import dev.rcht.jist.data.db.entity.WatchedChatEntity
import dev.rcht.jist.data.db.entity.WatchTopicEntity
import dev.rcht.jist.data.preferences.JistPreferences
import kotlinx.serialization.Serializable

@Serializable
data class ConfigExportData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val preferences: JistPreferences? = null,
    val llmConfigs: List<LlmConfigDto> = emptyList(),
    val appRules: List<AppRuleDto> = emptyList(),
    val customPrompts: List<CustomPromptDto> = emptyList(),
    val watchTopics: List<WatchTopicDto> = emptyList(),
    val chatSources: List<ChatSourceDto> = emptyList(),
    val watchedChats: List<WatchedChatDto> = emptyList()
)

@Serializable
data class LlmConfigDto(
    val name: String,
    val provider: String,
    val apiKey: String,
    val baseUrl: String,
    val modelId: String,
    val isDefault: Boolean = false,
    val maxTokens: Int = 512,
    val temperature: Float = 0.3f
)

@Serializable
data class AppRuleDto(
    val packageName: String,
    val appName: String,
    val enabled: Boolean = true,
    val mode: String = "AUTO",
    val batchWindowMinutes: Int = 15,
    val minMessagesForSummary: Int = 3,
    val customPrompt: String? = null,
    val userEnabled: Boolean = false
)

@Serializable
data class CustomPromptDto(
    val appName: String,
    val systemPrompt: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class WatchTopicDto(
    val title: String,
    val description: String = "",
    val keywords: String = "[]",
    val matchMode: String = "KEYWORD_ONLY",
    val isEnabled: Boolean = true
)

@Serializable
data class ChatSourceDto(
    val packageName: String,
    val displayName: String,
    val isEnabled: Boolean = true
)

@Serializable
data class WatchedChatDto(
    val sourcePkg: String,
    val chatId: String,
    val chatName: String,
    val isEnabled: Boolean = true,
    val isSummarized: Boolean = false,
    val customPrompt: String? = null,
    val minMessagesForSummary: Int = 5,
    val retentionDays: Int = 7
)

fun ChatSourceEntity.toDto() = ChatSourceDto(
    packageName = packageName,
    displayName = displayName,
    isEnabled = isEnabled
)

fun ChatSourceDto.toEntity() = ChatSourceEntity(
    packageName = packageName,
    displayName = displayName,
    isEnabled = isEnabled
)

fun WatchedChatEntity.toDto(sourcePkg: String) = WatchedChatDto(
    sourcePkg = sourcePkg,
    chatId = chatId,
    chatName = chatName,
    isEnabled = isEnabled,
    isSummarized = isSummarized,
    customPrompt = customPrompt,
    minMessagesForSummary = minMessagesForSummary,
    retentionDays = retentionDays
)

fun WatchedChatDto.toEntity(sourceId: Long) = WatchedChatEntity(
    sourceId = sourceId,
    chatId = chatId,
    chatName = chatName,
    isEnabled = isEnabled,
    isSummarized = isSummarized,
    customPrompt = customPrompt,
    minMessagesForSummary = minMessagesForSummary,
    retentionDays = retentionDays
)

fun LlmConfigEntity.toDto() = LlmConfigDto(
    name = name,
    provider = provider,
    apiKey = apiKey,
    baseUrl = baseUrl,
    modelId = modelId,
    isDefault = isDefault,
    maxTokens = maxTokens,
    temperature = temperature
)

fun LlmConfigDto.toEntity() = LlmConfigEntity(
    name = name,
    provider = provider,
    apiKey = apiKey,
    baseUrl = baseUrl,
    modelId = modelId,
    isDefault = isDefault,
    maxTokens = maxTokens,
    temperature = temperature
)

fun AppRuleEntity.toDto() = AppRuleDto(
    packageName = packageName,
    appName = appName,
    enabled = enabled,
    mode = mode,
    batchWindowMinutes = batchWindowMinutes,
    minMessagesForSummary = minMessagesForSummary,
    customPrompt = customPrompt,
    userEnabled = userEnabled
)

fun AppRuleDto.toEntity() = AppRuleEntity(
    packageName = packageName,
    appName = appName,
    enabled = enabled,
    mode = mode,
    batchWindowMinutes = batchWindowMinutes,
    minMessagesForSummary = minMessagesForSummary,
    customPrompt = customPrompt,
    userEnabled = userEnabled
)

fun CustomPromptEntity.toDto() = CustomPromptDto(
    appName = appName,
    systemPrompt = systemPrompt,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CustomPromptDto.toEntity() = CustomPromptEntity(
    appName = appName,
    systemPrompt = systemPrompt,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun WatchTopicEntity.toDto() = WatchTopicDto(
    title = title,
    description = description,
    keywords = keywords,
    matchMode = matchMode,
    isEnabled = isEnabled
)

fun WatchTopicDto.toEntity() = WatchTopicEntity(
    title = title,
    description = description,
    keywords = keywords,
    matchMode = matchMode,
    isEnabled = isEnabled
)
