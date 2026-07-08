package dev.rcht.jist.ui.watchdetail

import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.db.entity.WatchTopicEntity
import dev.rcht.jist.data.repository.NotificationRepository
import dev.rcht.jist.data.repository.WatchCollectedItemRepository
import dev.rcht.jist.data.repository.WatchTopicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CollectedItemDisplay(
    val id: Long,
    val appName: String,
    val packageName: String,
    val notificationTitle: String,
    val notificationContent: String,
    val matchedKeyword: String,
    val matchType: String,
    val aiExtractedInfo: String?,
    val importance: Int,
    val matchedAt: Long,
    val formattedTime: String
)

data class TimeGroup(
    val label: String,
    val items: List<CollectedItemDisplay>
)

data class AppGroup(
    val appName: String,
    val packageName: String,
    val timeGroups: List<TimeGroup>
)

data class WatchDetailUiState(
    val topic: WatchTopicEntity? = null,
    val groups: List<AppGroup> = emptyList(),
    val totalCount: Int = 0,
    val appCount: Int = 0,
    val isLoading: Boolean = true
)

class WatchDetailViewModel(
    private val watchTopicRepository: WatchTopicRepository,
    private val watchCollectedItemRepository: WatchCollectedItemRepository,
    private val notificationRepository: NotificationRepository,
    private val watchId: Long,
    private val packageManager: PackageManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchDetailUiState())
    val uiState: StateFlow<WatchDetailUiState> = _uiState

    private val dayNames = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

    init { loadData() }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val topic = watchTopicRepository.getById(watchId) ?: return@launch
                val items = watchCollectedItemRepository.getItemsForTopic(watchId)

                val displayItems = items.mapNotNull { item ->
                    val notification = notificationRepository.getById(item.notificationId)
                    val appLabel = try {
                        packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(item.sourceApp, 0)
                        ).toString()
                    } catch (_: Exception) { item.sourceApp }
                    val bucket = getTimeBucket(item.matchedAt)
                    CollectedItemDisplay(
                        id = item.id,
                        appName = appLabel,
                        packageName = item.sourceApp,
                        notificationTitle = notification?.title ?: "",
                        notificationContent = notification?.content ?: (item.aiExtractedInfo ?: item.matchedKeyword),
                        matchedKeyword = item.matchedKeyword,
                        matchType = item.matchType,
                        aiExtractedInfo = item.aiExtractedInfo,
                        importance = item.importance,
                        matchedAt = item.matchedAt,
                        formattedTime = formatItemTime(item.matchedAt, bucket)
                    )
                }

                val groups = displayItems
                    .groupBy { it.packageName }
                    .values
                    .map { pkgs ->
                        val sorted = pkgs.sortedByDescending { it.matchedAt }
                        val timeGroups = sorted
                            .groupBy { getTimeBucket(it.matchedAt) }
                            .let { buckets ->
                                listOf("今天", "昨天", "本周", "更早")
                                    .mapNotNull { label ->
                                        val bucketItems = buckets[label]
                                        if (bucketItems.isNullOrEmpty()) null
                                        else TimeGroup(label, bucketItems)
                                    }
                            }
                        AppGroup(
                            appName = pkgs.first().appName,
                            packageName = pkgs.first().packageName,
                            timeGroups = timeGroups
                        )
                    }
                    .sortedByDescending { it.timeGroups.firstOrNull()?.let { g -> timeBucketOrder(g.label) } ?: 0 }

                _uiState.value = WatchDetailUiState(
                    topic = topic,
                    groups = groups,
                    totalCount = displayItems.size,
                    appCount = groups.size,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading detail", e)
                _uiState.value = WatchDetailUiState(isLoading = false)
            }
        }
    }

    private fun getTimeBucket(timestampMs: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val now = Calendar.getInstance()
        val todayStart = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.after(todayStart)) return "今天"

        val yesterdayStart = Calendar.getInstance().apply {
            timeInMillis = todayStart.timeInMillis
            add(Calendar.DAY_OF_MONTH, -1)
        }
        if (cal.after(yesterdayStart)) return "昨天"

        val weekStart = Calendar.getInstance().apply {
            timeInMillis = todayStart.timeInMillis
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        if (cal.after(weekStart)) return "本周"

        return "更早"
    }

    private fun timeBucketOrder(label: String): Int = when (label) {
        "今天" -> 3; "昨天" -> 2; "本周" -> 1; else -> 0
    }

    private fun formatItemTime(timestampMs: Long, bucket: String): String {
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
        return when (bucket) {
            "今天" -> timeStr
            "昨天" -> "昨天 $timeStr"
            "本周" -> {
                val c = Calendar.getInstance().apply { timeInMillis = timestampMs }
                "${dayNames[c.get(Calendar.DAY_OF_WEEK) - 1]} $timeStr"
            }
            else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestampMs)) + " $timeStr"
        }
    }

    fun toggleEnabled() {
        viewModelScope.launch(Dispatchers.IO) {
            val topic = _uiState.value.topic ?: return@launch
            watchTopicRepository.update(topic.copy(isEnabled = !topic.isEnabled))
            loadData()
        }
    }

    fun delete(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            watchCollectedItemRepository.deleteForTopic(watchId)
            val topic = watchTopicRepository.getById(watchId) ?: return@launch
            watchTopicRepository.delete(topic)
            onComplete()
        }
    }

    fun deleteItems(itemIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            watchCollectedItemRepository.deleteItems(itemIds)
            loadData()
        }
    }

    companion object {
        private const val TAG = "WatchDetailViewModel"
    }
}
