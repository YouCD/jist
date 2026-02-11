package dev.rcht.jist.ui.summaries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.db.entity.SummaryEntity
import dev.rcht.jist.data.repository.SummaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SummariesUiState(
    val summaries: List<SummaryEntity> = emptyList(),
    val filteredSummaries: List<SummaryEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedAppFilter: String? = null
)

class SummariesViewModel(
    private val summaryRepository: SummaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummariesUiState())
    val uiState: StateFlow<SummariesUiState> = _uiState

    init {
        loadSummaries()
    }

    private fun loadSummaries() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val summaries = summaryRepository.getAll().sortedByDescending { it.createdAt }
                _uiState.value = _uiState.value.copy(
                    summaries = summaries,
                    filteredSummaries = summaries,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error loading summaries: ${e.message}"
                )
            }
        }
    }

    fun searchSummaries(query: String) {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(searchQuery = query)
        applyFilters(query, currentState.selectedAppFilter)
    }

    fun filterByApp(appName: String?) {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(selectedAppFilter = appName)
        applyFilters(currentState.searchQuery, appName)
    }

    private fun applyFilters(searchQuery: String, appFilter: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val filtered = if (searchQuery.isNotBlank()) {
                // Use FTS for better search performance
                if (appFilter != null) {
                    summaryRepository.searchFtsByApp(searchQuery, appFilter)
                } else {
                    summaryRepository.searchFts(searchQuery)
                }
            } else {
                // No search query, just filter by app if needed
                if (appFilter != null) {
                    summaryRepository.getByApp(appFilter)
                } else {
                    summaryRepository.getAll()
                }
            }
            
            _uiState.value = _uiState.value.copy(filteredSummaries = filtered)
        }
    }

    fun refreshSummaries() {
        loadSummaries()
    }

    fun formatDate(timeMs: Long): String {
        return SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timeMs))
    }
}
