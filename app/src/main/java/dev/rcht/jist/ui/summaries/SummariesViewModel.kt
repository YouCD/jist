package dev.rcht.jist.ui.summaries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.db.entity.SummaryEntity
import dev.rcht.jist.data.repository.SummaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

data class SummariesUiState(
    val summaries: List<SummaryEntity> = emptyList(),
    val filteredSummaries: List<SummaryEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
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

    private fun sortSummaries(summaries: List<SummaryEntity>): List<SummaryEntity> {
        val (unread, read) = summaries.partition { !it.isRead }
        return unread.sortedBy { it.createdAt } +
            read.sortedByDescending { it.createdAt }
    }

    private fun loadSummaries() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val page = summaryRepository.getRecent(PAGE_SIZE, 0)
                val sorted = sortSummaries(page)
                _uiState.value = _uiState.value.copy(
                    summaries = sorted,
                    filteredSummaries = sorted,
                    isLoading = false,
                    hasMore = page.size == PAGE_SIZE,
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

    fun loadMoreSummaries() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        if (state.searchQuery.isNotBlank() || state.selectedAppFilter != null) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val offset = _uiState.value.summaries.size
                val page = summaryRepository.getRecent(PAGE_SIZE, offset)
                val merged = sortSummaries(_uiState.value.summaries + page)
                _uiState.value = _uiState.value.copy(
                    summaries = merged,
                    filteredSummaries = merged,
                    isLoadingMore = false,
                    hasMore = page.size == PAGE_SIZE
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
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

            val sorted = sortSummaries(filtered)
            _uiState.value = _uiState.value.copy(
                filteredSummaries = sorted,
                hasMore = false
            )
        }
    }

    fun refreshSummaries() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val page = summaryRepository.getRecent(PAGE_SIZE, 0)
                val sorted = sortSummaries(page)
                _uiState.value = _uiState.value.copy(
                    summaries = sorted,
                    filteredSummaries = sorted,
                    isRefreshing = false,
                    hasMore = page.size == PAGE_SIZE,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = "Error loading summaries: ${e.message}"
                )
            }
        }
    }

    fun deleteSummaries(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            summaryRepository.deleteByIds(ids)
            loadSummaries()
        }
    }
}
