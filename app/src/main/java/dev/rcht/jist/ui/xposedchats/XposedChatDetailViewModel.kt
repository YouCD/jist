package dev.rcht.jist.ui.xposedchats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.db.entity.ChatMessageEntity
import dev.rcht.jist.data.db.entity.SummaryEntity
import dev.rcht.jist.data.db.entity.WatchedChatEntity
import dev.rcht.jist.data.repository.ChatMessageRepository
import dev.rcht.jist.data.repository.ChatSourceRepository
import dev.rcht.jist.data.repository.LlmConfigRepository
import dev.rcht.jist.data.repository.SummaryRepository
import dev.rcht.jist.data.repository.WatchedChatRepository
import dev.rcht.jist.llm.LlmClientFactory
import dev.rcht.jist.llm.LlmResult
import dev.rcht.jist.llm.NotificationForSummary
import dev.rcht.jist.llm.PromptBuilder
import dev.rcht.jist.llm.LlmRequestConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class XposedChatDetailState(
    val chat: WatchedChatEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val summarizedMessageIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val isSummarizing: Boolean = false,
    val summarizeError: String? = null
)

class XposedChatDetailViewModel(
    private val watchedChatRepository: WatchedChatRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val summaryRepository: SummaryRepository,
    private val llmConfigRepository: LlmConfigRepository,
    private val chatSourceRepository: ChatSourceRepository,
    private val httpClient: OkHttpClient,
    private val chatId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(XposedChatDetailState())
    val uiState: StateFlow<XposedChatDetailState> = _uiState

    init { loadData() }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chat = watchedChatRepository.getAll().find { it.id == chatId }
                val messages = chatMessageRepository.getByWatchedChat(chatId).sortedByDescending { it.timestamp }
                val convKey = chat?.let { "xposed_${it.chatId}" } ?: ""
                val summaries = summaryRepository.getForConversation(convKey)
                val summarizedIds = if (summaries.isNotEmpty()) {
                    messages.filter { msg ->
                        summaries.any { s -> msg.timestamp in s.notificationTimeFrom..s.notificationTimeTo }
                    }.map { it.id }.toSet()
                } else emptySet()
                _uiState.value = XposedChatDetailState(chat = chat, messages = messages, summarizedMessageIds = summarizedIds, isLoading = false)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading chat detail", e)
                _uiState.value = XposedChatDetailState(isLoading = false)
            }
        }
    }

    fun deleteMessages(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                chatMessageRepository.deleteByIds(*ids.toLongArray())
                loadData()
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting messages $ids", e)
            }
        }
    }

    fun updateCustomPrompt(prompt: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chat = _uiState.value.chat ?: return@launch
                watchedChatRepository.update(chat.copy(customPrompt = prompt))
                loadData()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating custom prompt", e)
            }
        }
    }

    fun summarize() {
        val chat = _uiState.value.chat ?: return
        val state = _uiState.value
        val messages = state.messages.filter { it.id !in state.summarizedMessageIds }
        if (messages.isEmpty()) return
        if (messages.size < chat.minMessagesForSummary) {
            _uiState.value = _uiState.value.copy(isSummarizing = false,
                summarizeError = "消息不足 ${chat.minMessagesForSummary} 条，暂不生成摘要")
            return
        }
        _uiState.value = _uiState.value.copy(isSummarizing = true, summarizeError = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = llmConfigRepository.getDefault() ?: run {
                    _uiState.value = _uiState.value.copy(isSummarizing = false, summarizeError = "未配置 LLM，请先在设置中配置 AI 模型")
                    return@launch
                }

                val source = chatSourceRepository.getEnabled().find { it.id == chat.sourceId }
                val appName = source?.displayName ?: "微信"
                val contactOrGroup = chat.chatName

                val notifications = messages.map { msg ->
                    NotificationForSummary(
                        text = msg.content,
                        timestamp = msg.timestamp,
                        sender = msg.senderName.ifBlank { "" },
                        appName = appName
                    )
                }

                val llmMessages = PromptBuilder().buildMessages(
                    notifications = notifications,
                    appName = appName,
                    contactOrGroup = contactOrGroup,
                    customPrompt = chat.customPrompt
                )

                val client = LlmClientFactory.createClient(config, httpClient)
                val llmConfig = LlmRequestConfig(
                    model = config.modelId,
                    maxTokens = config.maxTokens,
                    temperature = config.temperature,
                    apiKey = config.apiKey,
                    baseUrl = config.baseUrl
                )
                val result = client.complete(llmMessages, llmConfig)

                when (result) {
                    is LlmResult.Success -> {
                        val summary = SummaryEntity(
                            packageName = "com.tencent.mm",
                            conversationKey = "xposed_${chat.chatId}",
                            appName = appName,
                            contactOrGroup = contactOrGroup,
                            summaryText = result.data.text,
                            messageCount = messages.size,
                            modelUsed = result.data.model,
                            tokenCount = result.data.totalTokens,
                            createdAt = System.currentTimeMillis(),
                            notificationTimeFrom = messages.minOf { it.timestamp },
                            notificationTimeTo = messages.maxOf { it.timestamp }
                        )
                        val summaryId = summaryRepository.insert(summary)
                        _uiState.value = _uiState.value.copy(isSummarizing = false, summarizeError = null)
                        loadData()
                        Log.i(TAG, "summary created: id=$summaryId for ${chat.chatId}")
                    }
                    is LlmResult.Error -> {
                        _uiState.value = _uiState.value.copy(isSummarizing = false,
                            summarizeError = "摘要生成失败: ${result.error.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "summarize error", e)
                _uiState.value = _uiState.value.copy(isSummarizing = false,
                    summarizeError = "摘要出错: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "XposedChatDetailVM"
    }
}
