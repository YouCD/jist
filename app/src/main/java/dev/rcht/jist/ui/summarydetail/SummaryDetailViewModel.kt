package dev.rcht.jist.ui.summarydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.db.entity.NotificationEntity
import dev.rcht.jist.data.db.entity.SummaryEntity
import dev.rcht.jist.data.repository.NotificationRepository
import dev.rcht.jist.data.repository.SummaryRepository
import dev.rcht.jist.engine.SummaryEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SummaryDetailUiState(
    val summary: SummaryEntity? = null,
    val notifications: List<NotificationEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isReSummarizing: Boolean = false,
    val error: String? = null,
    val reSummarizeSuccess: Boolean? = null
)

class SummaryDetailViewModel(
    private val summaryRepository: SummaryRepository,
    private val notificationRepository: NotificationRepository,
    private val summaryEngine: SummaryEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummaryDetailUiState())
    val uiState: StateFlow<SummaryDetailUiState> = _uiState

    fun loadSummary(summaryId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val summary = summaryRepository.getById(summaryId)
                val notifications = if (summary != null) {
                    notificationRepository.getByConversationKey(summary.conversationKey)
                } else {
                    emptyList()
                }
                
                _uiState.value = _uiState.value.copy(
                    summary = summary,
                    notifications = notifications.sortedByDescending { it.timestamp },
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

    fun reSummarize() {
        val summary = _uiState.value.summary ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isReSummarizing = true)
            try {
                val result = summaryEngine.summarizeConversation(summary.conversationKey)
                val success = result::class.simpleName == "Success"
                val errorMsg = if (!success) {
                    // Extract error message using reflection fallback
                    try {
                        result::class.java.getDeclaredField("message").let { field ->
                            field.isAccessible = true
                            field.get(result) as? String
                        }
                    } catch (e: Exception) {
                        "Failed to re-summarize"
                    }
                } else null
                
                _uiState.value = _uiState.value.copy(
                    isReSummarizing = false,
                    reSummarizeSuccess = success,
                    error = errorMsg
                )
                
                // Reload the summary if successful
                if (success) {
                    loadSummary(summary.id)
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

    fun clearReSummarizeStatus() {
        _uiState.value = _uiState.value.copy(reSummarizeSuccess = null)
    }
}
