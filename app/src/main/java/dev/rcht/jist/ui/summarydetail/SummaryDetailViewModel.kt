package dev.rcht.jist.ui.summarydetail

import android.util.Log
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
        Log.d(TAG, "reSummarize called")
        val summary = _uiState.value.summary
        if (summary == null) {
            Log.w(TAG, "reSummarize: no summary in state")
            return
        }
        Log.d(TAG, "reSummarize: conversationKey=${summary.conversationKey}")
        
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isReSummarizing = true)
            try {
                val result = summaryEngine.summarizeConversation(summary.conversationKey, includeSummarized = true)
                Log.d(TAG, "reSummarize result: $result")
                when (result) {
                    is dev.rcht.jist.engine.SummaryResult.Success -> {
                        loadSummary(result.summaryId)
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

    fun clearReSummarizeStatus() {
        _uiState.value = _uiState.value.copy(reSummarizeSuccess = null)
    }

    companion object {
        private const val TAG = "SummaryDetailVM"
    }
}
