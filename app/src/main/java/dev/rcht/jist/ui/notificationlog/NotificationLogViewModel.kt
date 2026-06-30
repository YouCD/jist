package dev.rcht.jist.ui.notificationlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.db.entity.NotificationEntity
import dev.rcht.jist.data.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class NotificationLogUiState(
    val notifications: List<NotificationEntity> = emptyList(),
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
                _uiState.value = _uiState.value.copy(
                    notifications = all,
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
