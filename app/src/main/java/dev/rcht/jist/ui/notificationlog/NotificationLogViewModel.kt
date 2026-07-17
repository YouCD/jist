package dev.rcht.jist.ui.notificationlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.db.entity.NotificationEntity
import dev.rcht.jist.data.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppNotificationGroup(
    val packageName: String,
    val appName: String,
    val notifications: List<NotificationEntity>,
    val count: Int
)

data class NotificationLogUiState(
    val appGroups: List<AppNotificationGroup> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class NotificationLogViewModel(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationLogUiState())
    val uiState: StateFlow<NotificationLogUiState> = _uiState

    init {
        loadNotifications()
    }

    private fun loadNotifications() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val all = notificationRepository.getAll()
                val groups = all.groupBy { it.packageName }
                    .map { (pkg, notifications) ->
                        AppNotificationGroup(
                            packageName = pkg,
                            appName = notifications.firstOrNull()?.appName ?: pkg,
                            notifications = notifications.sortedByDescending { it.timestamp },
                            count = notifications.size
                        )
                    }
                    .sortedByDescending { it.count }
                _uiState.value = _uiState.value.copy(
                    appGroups = groups,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error loading notifications: ${e.message}"
                )
            }
        }
    }

    fun refresh() {
        loadNotifications()
    }

    fun deleteNotifications(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                notificationRepository.deleteByIds(ids)
                loadNotifications()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Delete failed: ${e.message}")
            }
        }
    }
}
