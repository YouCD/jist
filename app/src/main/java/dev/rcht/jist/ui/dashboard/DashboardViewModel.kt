package dev.rcht.jist.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.repository.NotificationRepository
import dev.rcht.jist.data.repository.SummaryRepository
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
    val isLoading: Boolean = true
)

class DashboardViewModel(
    private val notificationRepository: NotificationRepository,
    private val summaryRepository: SummaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            try {
                // Get recent notifications (last 100)
                val recentNotifications = notificationRepository.getRecent(limit = 100)

                // Count today's notifications
                val todayMs = getTodayStartMs()
                val notificationsTodayCount = recentNotifications.count { it.timestamp >= todayMs }

                // Count unsummarized
                val allNotifications = notificationRepository.getAllUnsummarized()
                val unsummarizedCount = allNotifications.size

                // Get all summaries
                val allSummaries = summaryRepository.getAll()

                // Count today's summaries
                val summariesTodayCount = allSummaries.count { it.createdAt >= todayMs }

                // Get last summarized time
                val lastSummarizedTime = allSummaries.maxByOrNull { it.createdAt }?.let {
                    formatTimestamp(it.createdAt)
                } ?: "Never"

                // Assume listener is active if we have recent notifications (within 1 hour)
                val oneHourAgoMs = System.currentTimeMillis() - (60 * 60 * 1000)
                val isListenerActive = recentNotifications.any { it.timestamp >= oneHourAgoMs }

                _uiState.value = DashboardUiState(
                    notificationsTodayCount = notificationsTodayCount,
                    totalNotificationsCount = recentNotifications.size,
                    unsummarizedCount = unsummarizedCount,
                    summariesTodayCount = summariesTodayCount,
                    lastSummarizedTime = lastSummarizedTime,
                    isNotificationListenerActive = isListenerActive,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = DashboardUiState(
                    isLoading = false
                )
            }
        }
    }

    fun refreshData() {
        loadDashboardData()
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
