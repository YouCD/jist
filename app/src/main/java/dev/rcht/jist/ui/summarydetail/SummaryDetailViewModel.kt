package dev.rcht.jist.ui.summarydetail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.db.entity.ChatMessageEntity
import dev.rcht.jist.data.db.entity.NotificationEntity
import dev.rcht.jist.data.db.entity.SummaryEntity
import dev.rcht.jist.data.repository.ChatMessageRepository
import dev.rcht.jist.data.repository.NotificationRepository
import dev.rcht.jist.data.repository.SummaryRepository
import dev.rcht.jist.engine.SummaryEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SummaryDetailUiState(
    val summaries: List<SummaryEntity> = emptyList(),
    val currentIndex: Int = 0,
    val notifications: List<NotificationEntity> = emptyList(),
    val chatMessages: List<ChatMessageEntity> = emptyList(),
    val isXposedChat: Boolean = false,
    val isLoading: Boolean = true,
    val isReSummarizing: Boolean = false,
    val error: String? = null,
    val reSummarizeSuccess: Boolean? = null
)

class SummaryDetailViewModel(
    private val summaryRepository: SummaryRepository,
    private val notificationRepository: NotificationRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val summaryEngine: SummaryEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummaryDetailUiState())
    val uiState: StateFlow<SummaryDetailUiState> = _uiState

    private fun sortSummaries(summaries: List<SummaryEntity>): List<SummaryEntity> {
        val (unread, read) = summaries.partition { !it.isRead }
        return unread.sortedBy { it.createdAt } + read.sortedByDescending { it.createdAt }
    }

    private suspend fun resolveMessages(
        summary: SummaryEntity
    ): Triple<List<NotificationEntity>, List<ChatMessageEntity>, Boolean> {
        if (summary.conversationKey.startsWith(XPOSED_PREFIX)) {
            val chatId = summary.conversationKey.removePrefix(XPOSED_PREFIX)
            val messages = chatMessageRepository.getByChatAndTimeRange(
                chatAppKey = summary.packageName,
                chatId = chatId,
                timeFrom = summary.notificationTimeFrom,
                timeTo = summary.notificationTimeTo
            )
            return Triple(emptyList(), messages, true)
        }
        val notifications = notificationRepository.getByConversationKey(summary.conversationKey)
        return Triple(notifications.sortedByDescending { it.timestamp }, emptyList(), false)
    }

    fun loadAll(summaryId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val all = sortSummaries(summaryRepository.getAll())
                val index = all.indexOfFirst { it.id == summaryId }.coerceAtLeast(0)
                val summary = all.getOrNull(index)
                if (summary != null && !summary.isRead) {
                    summaryRepository.markAsRead(summary.id)
                }
                val (notifications, chatMessages, isXposedChat) = if (summary != null) {
                    resolveMessages(summary)
                } else {
                    Triple(emptyList(), emptyList(), false)
                }
                _uiState.value = SummaryDetailUiState(
                    summaries = all,
                    currentIndex = index,
                    notifications = notifications,
                    chatMessages = chatMessages,
                    isXposedChat = isXposedChat,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error loading summary: ${e.message}"
                )
            }
        }
    }

    fun onPageChanged(index: Int) {
        if (index == _uiState.value.currentIndex) return
        viewModelScope.launch(Dispatchers.IO) {
            val summary = _uiState.value.summaries.getOrNull(index) ?: return@launch
            if (!summary.isRead) {
                summaryRepository.markAsRead(summary.id)
            }
            val (notifications, chatMessages, isXposedChat) = resolveMessages(summary)
            _uiState.value = _uiState.value.copy(
                currentIndex = index,
                notifications = notifications,
                chatMessages = chatMessages,
                isXposedChat = isXposedChat,
                error = null
            )
        }
    }

    fun reSummarize() {
        val summary = _uiState.value.summaries.getOrNull(_uiState.value.currentIndex) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isReSummarizing = true)
            try {
                val result = summaryEngine.summarizeConversation(summary.conversationKey, includeSummarized = true)
                when (result) {
                    is dev.rcht.jist.engine.SummaryResult.Success -> {
                        reloadAllAfterResummarize(result.summaryId)
                        _uiState.value = _uiState.value.copy(
                            isReSummarizing = false,
                            reSummarizeSuccess = true,
                            error = null
                        )
                    }
                    is dev.rcht.jist.engine.SummaryResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isReSummarizing = false,
                            reSummarizeSuccess = false,
                            error = result.message
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isReSummarizing = false,
                    reSummarizeSuccess = false,
                    error = "Error re-summarizing: ${e.message}"
                )
            }
        }
    }

    private suspend fun reloadAllAfterResummarize(newSummaryId: Long) {
        val all = sortSummaries(summaryRepository.getAll())
        val index = all.indexOfFirst { it.id == newSummaryId }.coerceAtLeast(0)
        val summary = all[index]
        val (notifications, chatMessages, isXposedChat) = resolveMessages(summary)
        _uiState.value = SummaryDetailUiState(
            summaries = all,
            currentIndex = index,
            notifications = notifications,
            chatMessages = chatMessages,
            isXposedChat = isXposedChat,
            isLoading = false,
            isReSummarizing = false,
            error = null
        )
    }

    fun markAllAsRead() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                summaryRepository.markAllAsRead()
                val all = sortSummaries(summaryRepository.getAll())
                val currentSummaryId = _uiState.value.summaries.getOrNull(_uiState.value.currentIndex)?.id
                val index = all.indexOfFirst { it.id == currentSummaryId }.coerceAtLeast(0)
                val summary = all.getOrNull(index)
                val (notifications, chatMessages, isXposedChat) = if (summary != null) {
                    resolveMessages(summary)
                } else {
                    Triple(emptyList(), emptyList(), false)
                }
                _uiState.value = SummaryDetailUiState(
                    summaries = all,
                    currentIndex = index,
                    notifications = notifications,
                    chatMessages = chatMessages,
                    isXposedChat = isXposedChat,
                    isLoading = false,
                    isReSummarizing = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error marking all as read: ${e.message}"
                )
            }
        }
    }

    fun clearReSummarizeStatus() {
        _uiState.value = _uiState.value.copy(reSummarizeSuccess = null)
    }

    companion object {
        private const val TAG = "SummaryDetailVM"
        private const val XPOSED_PREFIX = "xposed_"
    }
}
