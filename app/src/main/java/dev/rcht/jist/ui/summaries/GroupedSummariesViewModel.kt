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
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SummaryGroup(
    val label: String,
    val summaries: List<SummaryEntity>
)

data class GroupedSummariesUiState(
    val groups: List<SummaryGroup> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedAppFilter: String? = null,
    val showGrouped: Boolean = true  // Toggle between list and grouped view
)

class GroupedSummariesViewModel(
    private val summaryRepository: SummaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupedSummariesUiState())
    val uiState: StateFlow<GroupedSummariesUiState> = _uiState

    init {
        loadSummaries()
    }

    private fun loadSummaries() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val summaries = summaryRepository.getAll().sortedByDescending { it.createdAt }
                val groups = if (_uiState.value.showGrouped) {
                    groupSummariesByDate(summaries)
                } else {
                    listOf(SummaryGroup("All", summaries))
                }
                
                _uiState.value = _uiState.value.copy(
                    groups = groups,
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

    fun toggleGrouping() {
        val newShowGrouped = !_uiState.value.showGrouped
        _uiState.value = _uiState.value.copy(showGrouped = newShowGrouped)
        applyFilters(_uiState.value.searchQuery, _uiState.value.selectedAppFilter)
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
            
            val groups = if (_uiState.value.showGrouped) {
                groupSummariesByDate(filtered.sortedByDescending { it.createdAt })
            } else {
                listOf(SummaryGroup("All", filtered.sortedByDescending { it.createdAt }))
            }
            
            _uiState.value = _uiState.value.copy(groups = groups)
        }
    }

    private fun groupSummariesByDate(summaries: List<SummaryEntity>): List<SummaryGroup> {
        val now = Calendar.getInstance()
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val weekAgo = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val groups = mutableMapOf<String, MutableList<SummaryEntity>>()
        
        for (summary in summaries) {
            val summaryDate = Calendar.getInstance().apply { timeInMillis = summary.createdAt }
            
            val groupLabel = when {
                summaryDate >= today -> "Today"
                summaryDate >= yesterday -> "Yesterday"
                summaryDate >= weekAgo -> "This Week"
                else -> formatDate(summary.createdAt)
            }
            
            groups.getOrPut(groupLabel) { mutableListOf() }.add(summary)
        }
        
        return listOf("Today", "Yesterday", "This Week").mapNotNull { label ->
            groups[label]?.let { SummaryGroup(label, it) }
        } + groups.filter { it.key !in listOf("Today", "Yesterday", "This Week") }
            .map { (label, summaries) -> SummaryGroup(label, summaries) }
    }

    fun refreshSummaries() {
        loadSummaries()
    }

    fun formatDate(timeMs: Long): String {
        return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timeMs))
    }
}
