package dev.rcht.jist.ui.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.JistApplication
import dev.rcht.jist.data.db.entity.NotificationEntity
import kotlinx.coroutines.launch

class NotificationLogViewModel(application: Application) : AndroidViewModel(application) {

    private val notificationRepository = (application as JistApplication).notificationRepository

    private val _notifications = MutableLiveData<List<NotificationEntity>>()
    val notifications: LiveData<List<NotificationEntity>> = _notifications

    private val _filteredNotifications = MutableLiveData<List<NotificationEntity>>()
    val filteredNotifications: LiveData<List<NotificationEntity>> = _filteredNotifications

    private val _selectedAppFilter = MutableLiveData<String?>()
    val selectedAppFilter: LiveData<String?> = _selectedAppFilter

    private val _searchQuery = MutableLiveData<String>()
    val searchQuery: LiveData<String> = _searchQuery

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            val notifications = notificationRepository.getRecent(limit = 100)
            _notifications.postValue(notifications)
            applyFilters()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    fun setAppFilter(appName: String?) {
        _selectedAppFilter.value = appName
        applyFilters()
    }

    private fun applyFilters() {
        val all = _notifications.value ?: return
        val query = _searchQuery.value?.lowercase() ?: ""
        val appFilter = _selectedAppFilter.value

        val filtered = all.filter { notification ->
            val matchesApp = appFilter == null || notification.appName == appFilter
            val matchesQuery = query.isEmpty() || 
                notification.title.lowercase().contains(query) ||
                notification.content.lowercase().contains(query) ||
                notification.appName.lowercase().contains(query)
            matchesApp && matchesQuery
        }

        _filteredNotifications.value = filtered
    }
}
