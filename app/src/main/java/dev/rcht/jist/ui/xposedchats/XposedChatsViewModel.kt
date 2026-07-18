package dev.rcht.jist.ui.xposedchats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.db.entity.ChatMessageEntity
import dev.rcht.jist.data.db.entity.ChatSourceEntity
import dev.rcht.jist.data.db.entity.WatchedChatEntity
import dev.rcht.jist.data.repository.ChatMessageRepository
import dev.rcht.jist.data.repository.ChatSourceRepository
import dev.rcht.jist.data.repository.WatchedChatRepository
import kotlinx.coroutines.Dispatchers
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
    val isEmpty: Boolean = false
)

class XposedChatsViewModel(
    private val watchedChatRepository: WatchedChatRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatSourceRepository: ChatSourceRepository
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
                    isEmpty = groups.isEmpty()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading xposed chats", e)
                _uiState.value = XposedChatsState(isLoading = false, isEmpty = true)
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

    companion object {
        private const val TAG = "XposedChatsVM"
    }
}
