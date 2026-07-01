package dev.rcht.jist.ui.watchlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.db.entity.WatchTopicEntity
import dev.rcht.jist.data.repository.WatchCollectedItemRepository
import dev.rcht.jist.data.repository.WatchTopicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class WatchListItem(
    val topic: WatchTopicEntity,
    val collectedCount: Int,
    val latestPreview: String?,
    val timeAgo: String
)

data class WatchListUiState(
    val items: List<WatchListItem> = emptyList(),
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false
)

class WatchListViewModel(
    private val watchTopicRepository: WatchTopicRepository,
    private val watchCollectedItemRepository: WatchCollectedItemRepository,
    private val timeJustNow: String,
    private val timeMinAgo: String,
    private val timeHourAgo: String,
    private val timeDayAgo: String,
    private val timeNever: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchListUiState())
    val uiState: StateFlow<WatchListUiState> = _uiState

    init { loadData() }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val topics = watchTopicRepository.getAll()
                val items = topics.map { topic ->
                    val stats = watchCollectedItemRepository.getStatsForTopic(topic.id)
                    WatchListItem(
                        topic = topic,
                        collectedCount = stats.count,
                        latestPreview = stats.latestPreview,
                        timeAgo = formatTimeAgo(stats.latestMatchedAt)
                    )
                }
                _uiState.value = WatchListUiState(
                    items = items,
                    isLoading = false,
                    isEmpty = items.isEmpty()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading watch list", e)
                _uiState.value = WatchListUiState(isLoading = false, isEmpty = true)
            }
        }
    }

    fun deleteTopic(topic: WatchTopicEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                watchCollectedItemRepository.deleteForTopic(topic.id)
                watchTopicRepository.delete(topic)
                loadData()
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting topic", e)
            }
        }
    }

    fun toggleEnabled(topic: WatchTopicEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                watchTopicRepository.update(topic.copy(isEnabled = !topic.isEnabled))
                loadData()
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling topic", e)
            }
        }
    }

    private fun formatTimeAgo(timestampMs: Long?): String {
        if (timestampMs == null) return timeNever
        val now = System.currentTimeMillis()
        val diff = now - timestampMs
        return when {
            diff < 60_000 -> timeJustNow
            diff < 3_600_000 -> timeMinAgo.format(diff / 60_000)
            diff < 86_400_000 -> timeHourAgo.format(diff / 3_600_000)
            else -> timeDayAgo.format(diff / 86_400_000)
        }
    }

    companion object {
        private const val TAG = "WatchListViewModel"
    }
}
