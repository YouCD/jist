package dev.rcht.jist.ui.xposedchats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.db.entity.ChatMessageEntity
import dev.rcht.jist.data.db.entity.ChatSourceEntity
import dev.rcht.jist.data.db.entity.SummaryEntity
import dev.rcht.jist.data.db.entity.WatchedChatEntity
import dev.rcht.jist.data.repository.ChatMessageRepository
import dev.rcht.jist.data.repository.ChatSourceRepository
import dev.rcht.jist.data.repository.LlmConfigRepository
import dev.rcht.jist.data.repository.SummaryRepository
import dev.rcht.jist.data.repository.WatchedChatRepository
import dev.rcht.jist.llm.LlmClientFactory
import dev.rcht.jist.llm.LlmRequestConfig
import dev.rcht.jist.llm.LlmResult
import dev.rcht.jist.llm.NotificationForSummary
import dev.rcht.jist.llm.PromptBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class XposedChatItem(
    val chat: WatchedChatEntity,
    val sourceName: String,
    val messageCount: Int,
    val latestMessage: ChatMessageEntity?
)

data class XposedSourceGroup(
    val source: ChatSourceEntity,
    val chats: List<XposedChatItem>
)

data class XposedChatsState(
    val groups: List<XposedSourceGroup> = emptyList(),
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val isSummarizing: Boolean = false,
    val summarizeError: String? = null,
    val summaryDialogState: SummaryDialogState? = null,
    val summarizingChatName: String? = null,
    val showSummaryAfterSummarize: Boolean = false
)

data class SummaryDialogState(
    val chatId: Long,
    val chatName: String,
    val messageCount: Int,
    val timestamp: Long,
    val summaryText: String = ""
)

class XposedChatsViewModel(
    private val watchedChatRepository: WatchedChatRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatSourceRepository: ChatSourceRepository,
    private val summaryRepository: SummaryRepository,
    private val llmConfigRepository: LlmConfigRepository,
    private val httpClient: OkHttpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(XposedChatsState())
    val uiState: StateFlow<XposedChatsState> = _uiState

    init { loadData() }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sources = chatSourceRepository.getEnabled()
                val groups = sources.map { source ->
                    val chats = watchedChatRepository.getBySource(source.id)
                        .filter { it.isEnabled }
                        .map { chat ->
                            val messages = chatMessageRepository.getByWatchedChat(chat.id)
                            XposedChatItem(
                                chat = chat,
                                sourceName = source.displayName,
                                messageCount = messages.size,
                                latestMessage = messages.maxByOrNull { it.timestamp }
                            )
                        }
                        .sortedByDescending { it.latestMessage?.timestamp ?: 0L }
                    XposedSourceGroup(source = source, chats = chats)
                }.filter { it.chats.isNotEmpty() }

                _uiState.value = XposedChatsState(
                    groups = groups,
                    isLoading = false,
                    isEmpty = groups.isEmpty(),
                    summaryDialogState = _uiState.value.summaryDialogState,
                    isSummarizing = _uiState.value.isSummarizing,
                    summarizeError = _uiState.value.summarizeError,
                    summarizingChatName = _uiState.value.summarizingChatName
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading xposed chats", e)
                _uiState.value = _uiState.value.copy(isLoading = false, isEmpty = true)
            }
        }
    }

    fun toggleSummarized(chatId: Long, summarized: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                watchedChatRepository.setSummarized(chatId, summarized)
                loadData()
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling summarized", e)
            }
        }
    }

    fun updateCustomPrompt(chatId: Long, prompt: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chats = watchedChatRepository.getAll()
                val chat = chats.find { it.id == chatId } ?: return@launch
                watchedChatRepository.update(chat.copy(customPrompt = prompt))
                loadData()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating custom prompt", e)
            }
        }
    }

    fun updateMinMessages(chatId: Long, minMessages: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chats = watchedChatRepository.getAll()
                val chat = chats.find { it.id == chatId } ?: return@launch
                watchedChatRepository.update(chat.copy(minMessagesForSummary = minMessages))
                loadData()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating min messages", e)
            }
        }
    }

    fun updateRetentionDays(chatId: Long, days: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chats = watchedChatRepository.getAll()
                val chat = chats.find { it.id == chatId } ?: return@launch
                watchedChatRepository.update(chat.copy(retentionDays = days))
                loadData()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating retention days", e)
            }
        }
    }

    fun updateChatSettings(chatId: Long, prompt: String?, minMessages: Int, retentionDays: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chats = watchedChatRepository.getAll()
                val chat = chats.find { it.id == chatId } ?: return@launch
                watchedChatRepository.update(chat.copy(
                    customPrompt = prompt,
                    minMessagesForSummary = minMessages,
                    retentionDays = retentionDays
                ))
                loadData()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating chat settings", e)
            }
        }
    }

    fun deleteChat(chatId: Long) = deleteChats(listOf(chatId))

    fun deleteChats(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ids.forEach { watchedChatRepository.deleteById(it) }
                loadData()
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting chats $ids", e)
            }
        }
    }

    var summarizingChatId: Long? = null
    var summarizingChatName: String? = null

    fun summarizeChat(chatId: Long) {
        val chat = runBlocking { watchedChatRepository.getAll().find { it.id == chatId } } ?: return
        val source = runBlocking { chatSourceRepository.getEnabled().find { it.id == chat.sourceId } }
        val messages = runBlocking { chatMessageRepository.getByWatchedChat(chatId) }
        val appName = source?.displayName ?: "未知应用"

        if (messages.size < chat.minMessagesForSummary) {
            _uiState.value = _uiState.value.copy(summarizeError = "消息不足 ${chat.minMessagesForSummary} 条，暂不生成摘要")
            return
        }

        summarizingChatId = chatId
        summarizingChatName = chat.chatName
        _uiState.value = _uiState.value.copy(
            isSummarizing = true,
            summarizeError = null,
            summarizingChatName = chat.chatName,
            showSummaryAfterSummarize = false
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val notifications = messages.map { msg ->
                    NotificationForSummary(
                        text = msg.content,
                        timestamp = msg.timestamp,
                        sender = msg.senderName.ifBlank { "" },
                        appName = appName
                    )
                }

                val llmConfig = runBlocking { llmConfigRepository.getDefault() } ?: run {
                    _uiState.value = _uiState.value.copy(isSummarizing = false, summarizeError = "未配置 LLM，请先在设置中配置 AI 模型")
                    return@launch
                }

                val llmMessages = PromptBuilder().buildMessages(
                    notifications = notifications,
                    appName = appName,
                    contactOrGroup = chat.chatName,
                    customPrompt = chat.customPrompt
                )

                val client = LlmClientFactory.createClient(llmConfig, httpClient)
                val result = runBlocking { client.complete(llmMessages, LlmRequestConfig(
                    model = llmConfig.modelId,
                    maxTokens = llmConfig.maxTokens,
                    temperature = llmConfig.temperature,
                    apiKey = llmConfig.apiKey,
                    baseUrl = llmConfig.baseUrl
                )) }

                when (result) {
                    is LlmResult.Success -> {
                        val summaryText = result.data.text
                        val summary = SummaryEntity(
                            packageName = source?.packageName ?: "",
                            conversationKey = "xposed_${chat.chatId}",
                            appName = appName,
                            contactOrGroup = chat.chatName,
                            summaryText = summaryText,
                            messageCount = messages.size,
                            modelUsed = result.data.model,
                            tokenCount = result.data.totalTokens,
                            createdAt = System.currentTimeMillis(),
                            notificationTimeFrom = messages.minOf { it.timestamp },
                            notificationTimeTo = messages.maxOf { it.timestamp }
                        )
                        val summaryId = runBlocking { summaryRepository.insert(summary) }
                        _uiState.value = _uiState.value.copy(
                            isSummarizing = false,
                            summarizeError = null,
                            showSummaryAfterSummarize = true,
                            summaryDialogState = SummaryDialogState(
                                chatId = chatId,
                                chatName = chat.chatName,
                                messageCount = messages.size,
                                timestamp = messages.maxOf { it.timestamp },
                                summaryText = summaryText
                            )
                        )
                        loadData()
                        Log.i(TAG, "group chat summary created: id=$summaryId for $chatId")
                    }
                    is LlmResult.Error -> {
                        _uiState.value = _uiState.value.copy(isSummarizing = false, summarizeError = "摘要失败: ${result.error.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "summarize error", e)
                _uiState.value = _uiState.value.copy(isSummarizing = false, summarizeError = "摘要出错: ${e.message}")
            } finally {
                summarizingChatId = null
                summarizingChatName = null
            }
        }
    }

    fun showSummaryDialogWithText(chatId: Long, chatName: String, messageCount: Int, timestamp: Long, summaryText: String) {
        _uiState.value = _uiState.value.copy(summaryDialogState = SummaryDialogState(
            chatId = chatId,
            chatName = chatName,
            messageCount = messageCount,
            timestamp = timestamp,
            summaryText = summaryText
        ))
    }

    fun dismissSummaryDialog() {
        _uiState.value = _uiState.value.copy(summaryDialogState = null)
    }

    companion object {
        private const val TAG = "XposedChatsVM"
    }
}

sealed class SummarizeResult {
    data class Success(val summaryText: String) : SummarizeResult()
    data class Error(val message: String) : SummarizeResult()
}
