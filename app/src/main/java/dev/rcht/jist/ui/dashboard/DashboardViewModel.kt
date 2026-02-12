package dev.rcht.jist.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.repository.NotificationRepository
import dev.rcht.jist.data.repository.SummaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class DashboardUiState(
    val notificationsTodayCount: Int = 0,
    val totalNotificationsCount: Int = 0,
    val unsummarizedCount: Int = 0,
    val summariesTodayCount: Int = 0,
    val lastSummarizedTime: String = "Never",
    val isNotificationListenerActive: Boolean = false,
    val isLoading: Boolean = true,
    val isSummarizing: Boolean = false,
    val summarizeError: String? = null
)

class DashboardViewModel(
    private val notificationRepository: NotificationRepository,
    private val summaryRepository: SummaryRepository,
    private val summaryEngine: dev.rcht.jist.engine.SummaryEngine,
    private val summaryNotificationManager: dev.rcht.jist.notification.SummaryNotificationManager
) : ViewModel() {

    companion object {
        private const val TAG = "DashboardViewModel"
    }

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        Log.d(TAG, "Loading dashboard data...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get recent notifications (last 100)
                val recentNotifications = notificationRepository.getRecent(limit = 100)
                Log.d(TAG, "Got ${recentNotifications.size} recent notifications")

                // Count today's notifications
                val todayMs = getTodayStartMs()
                val notificationsTodayCount = recentNotifications.count { it.timestamp >= todayMs }
                Log.d(TAG, "Today's notifications: $notificationsTodayCount")

                // Count unsummarized
                val allNotifications = notificationRepository.getAllUnsummarized()
                val unsummarizedCount = allNotifications.size
                Log.d(TAG, "Total unsummarized: $unsummarizedCount")
                allNotifications.forEachIndexed { index, notif ->
                    Log.d(TAG, "  [$index] ${notif.appName} - ${notif.title}: ${notif.content.take(30)}...")
                }

                // Get all summaries
                val allSummaries = summaryRepository.getAll()
                Log.d(TAG, "Got ${allSummaries.size} total summaries")

                // Count today's summaries
                val summariesTodayCount = allSummaries.count { it.createdAt >= todayMs }
                Log.d(TAG, "Today's summaries: $summariesTodayCount")

                // Get last summarized time
                val lastSummarizedTime = allSummaries.maxByOrNull { it.createdAt }?.let {
                    formatTimestamp(it.createdAt)
                } ?: "Never"
                Log.d(TAG, "Last summarized: $lastSummarizedTime")

                // Assume listener is active if we have recent notifications (within 1 hour)
                val oneHourAgoMs = System.currentTimeMillis() - (60 * 60 * 1000)
                val isListenerActive = recentNotifications.any { it.timestamp >= oneHourAgoMs }
                Log.d(TAG, "Listener active: $isListenerActive")

                _uiState.value = DashboardUiState(
                    notificationsTodayCount = notificationsTodayCount,
                    totalNotificationsCount = recentNotifications.size,
                    unsummarizedCount = unsummarizedCount,
                    summariesTodayCount = summariesTodayCount,
                    lastSummarizedTime = lastSummarizedTime,
                    isNotificationListenerActive = isListenerActive,
                    isLoading = false
                )
                Log.d(TAG, "Dashboard data loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading dashboard data: ${e.message}", e)
                _uiState.value = DashboardUiState(
                    isLoading = false
                )
            }
        }
    }

    fun refreshData() {
        loadDashboardData()
    }

    fun summarizeNow() {
        Log.d(TAG, "=== SUMMARIZE NOW BUTTON PRESSED ===")
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Launching summarization coroutine")
            _uiState.value = _uiState.value.copy(isSummarizing = true, summarizeError = null)
            try {
                // Get all pending conversations and summarize them
                Log.d(TAG, "Step 1: Getting all pending conversations from SummaryEngine")
                val results = summaryEngine.summarizeAllPending()
                Log.d(TAG, "Step 2: SummaryEngine returned ${results.size} results")

                // Collect all successful summaries
                val summaries = mutableListOf<dev.rcht.jist.data.db.entity.SummaryEntity>()
                
                results.forEachIndexed { index, result ->
                    Log.d(TAG, "Processing result ${index + 1}/${results.size}")
                    when (result) {
                        is dev.rcht.jist.engine.SummaryResult.Success -> {
                            Log.d(TAG, "✓ Success result: summaryId=${result.summaryId}, text=${result.summaryText.take(50)}...")
                            
                            // Get the summary to extract metadata
                            val summary = summaryRepository.getById(result.summaryId)
                            if (summary != null) {
                                summaries.add(summary)
                                Log.d(TAG, "✓ Summary found: packageName=${summary.packageName}, appName=${summary.appName}, contact=${summary.contactOrGroup}")
                            } else {
                                Log.w(TAG, "✗ Summary not found with id=${result.summaryId}")
                            }
                        }
                        is dev.rcht.jist.engine.SummaryResult.Error -> {
                            Log.e(TAG, "✗ Error result: ${result.message}")
                            _uiState.value = _uiState.value.copy(summarizeError = result.message)
                        }
                    }
                }

                // Post grouped notifications for all summaries
                if (summaries.isNotEmpty()) {
                    Log.d(TAG, "Step 3: Posting grouped notifications for ${summaries.size} summaries")
                    summaryNotificationManager.postGroupedSummaryNotifications(summaries)
                    Log.d(TAG, "✓ Notifications posted")
                }

                Log.d(TAG, "Step 4: Refreshing dashboard data")
                // Refresh data to show updated stats
                loadDashboardData()
                
                _uiState.value = _uiState.value.copy(isSummarizing = false)
                Log.d(TAG, "=== SUMMARIZATION COMPLETE ===")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Exception during summarization: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isSummarizing = false,
                    summarizeError = "Error: ${e.message}"
                )
            }
        }
    }

    private fun getTodayStartMs(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun formatTimestamp(timeMs: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = now - timeMs

        return when {
            diffMs < 60000 -> "Just now"
            diffMs < 3600000 -> "${diffMs / 60000}m ago"
            diffMs < 86400000 -> "${diffMs / 3600000}h ago"
            else -> "${diffMs / 86400000}d ago"
        }
    }
}
