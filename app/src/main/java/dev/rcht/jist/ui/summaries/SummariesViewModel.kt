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
    val groupedSummaries: List<SummaryGroup> = emptyList(),
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

    private fun groupByDate(summaries: List<SummaryEntity>): List<SummaryGroup> {
        val calendar = java.util.Calendar.getInstance()
        val today = calendar.apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
        val yesterday = today - 86400000L
        val dateFormat = SimpleDateFormat("MMM dd, EEEE", Locale.getDefault())

        return summaries
            .groupBy { summary ->
                when {
                    summary.createdAt >= today -> "Today"
                    summary.createdAt >= yesterday -> "Yesterday"
                    else -> dateFormat.format(Date(summary.createdAt))
                }
            }
            .map { (label, items) -> SummaryGroup(label, items) }
            .sortedByDescending { it.summaries.firstOrNull()?.createdAt ?: 0L }
    }

    private fun loadSummaries() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val summaries = summaryRepository.getAll().sortedByDescending { it.createdAt }
                _uiState.value = _uiState.value.copy(
                    summaries = summaries,
                    filteredSummaries = summaries,
                    groupedSummaries = groupByDate(summaries),
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
                if (appFilter != null) {
                    summaryRepository.searchFtsByApp(searchQuery, appFilter)
                } else {
                    summaryRepository.searchFts(searchQuery)
                }
            } else {
                if (appFilter != null) {
                    summaryRepository.getByApp(appFilter)
                } else {
                    summaryRepository.getAll()
                }
            }
            
            _uiState.value = _uiState.value.copy(
                filteredSummaries = filtered,
                groupedSummaries = groupByDate(filtered)
            )
        }
    }

    fun refreshSummaries() {
        loadSummaries()
    }

    fun deleteSummaries(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            summaryRepository.deleteByIds(ids)
            loadSummaries()
        }
    }

    fun formatDate(timeMs: Long): String {
        return SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timeMs))
    }
}
