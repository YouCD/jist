package dev.rcht.jist.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.rcht.jist.data.db.entity.AppRuleEntity
import dev.rcht.jist.data.repository.AppRuleRepository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppSettingsUiState(
    val apps: List<AppRuleEntity> = emptyList(),
    val allAppsCount: Int = 0,
    val searchQuery: String = "",
    val showAll: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val snackbarMessage: String? = null
)

class AppSettingsViewModel(
    private val context: Context,
    private val appRuleRepository: AppRuleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppSettingsUiState())
    val uiState: StateFlow<AppSettingsUiState> = _uiState
    
    private var allAppsCache: List<AppRuleEntity> = emptyList()

    init {
        loadAppRules()
    }

    private fun loadAppRules() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val packageManager = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val launcherApps = packageManager.queryIntentActivities(mainIntent, 0)
                    .filter { it.activityInfo != null }
                    .distinctBy { it.activityInfo.packageName }

                val existingRules = appRuleRepository.getAll().associateBy { it.packageName }

                val allApps = launcherApps.mapNotNull { resolveInfo ->
                    try {
                        val packageName = resolveInfo.activityInfo.packageName
                        val appLabel = resolveInfo.loadLabel(packageManager).toString()
                        existingRules[packageName] ?: AppRuleEntity(
                            packageName = packageName,
                            appName = appLabel,
                            enabled = false
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.sortedBy { it.appName }

                allAppsCache = allApps
                updateUiStateFromCache()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error loading apps: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    private fun updateUiStateFromCache() {
        val state = _uiState.value
        val showAll = state.showAll || state.searchQuery.isNotBlank()
        val baseApps = (if (showAll) allAppsCache else allAppsCache.filter { it.enabled })
            .sortedWith(compareByDescending<AppRuleEntity> { it.enabled }.thenBy { it.appName })

        val filteredApps = if (state.searchQuery.isBlank()) {
            baseApps
        } else {
            baseApps.filter {
                it.appName.contains(state.searchQuery, ignoreCase = true) ||
                it.packageName.contains(state.searchQuery, ignoreCase = true)
            }
        }
        _uiState.value = state.copy(
            apps = filteredApps,
            allAppsCount = allAppsCache.size,
            isLoading = false,
            error = null
        )
    }

    fun toggleAppEnabled(appRule: AppRuleEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Mark as user-enabled since they manually toggled it
            val updatedRule = appRule.copy(
                enabled = !appRule.enabled,
                userEnabled = true
            )
            
            // DB Update
            if (appRule.id == 0L) {
                val newId = appRuleRepository.insert(updatedRule)
                updateCacheAndUI(updatedRule.copy(id = newId))
            } else {
                appRuleRepository.update(updatedRule)
                updateCacheAndUI(updatedRule)
            }
        }
    }
    
    private fun updateCacheAndUI(updatedApp: AppRuleEntity) {
        // Update the cache maintaining original positions
        allAppsCache = allAppsCache.map { 
            if (it.packageName == updatedApp.packageName) updatedApp else it 
        }
        
        // Update UI without re-sorting - maintain display order
        updateUiStateFromCache()
    }
    
    fun toggleAll(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedApps = allAppsCache.map { it.copy(enabled = enabled) }
            updatedApps.forEach {
                if (it.id == 0L) appRuleRepository.insert(it) else appRuleRepository.update(it)
            }
            allAppsCache = updatedApps
            updateUiStateFromCache()
        }
    }

    fun disableAllApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedApps = allAppsCache.map { it.copy(enabled = false) }
            updatedApps.forEach {
                if (it.id == 0L) appRuleRepository.insert(it) else appRuleRepository.update(it)
            }
            allAppsCache = updatedApps
            updateUiStateFromCache()
        }
    }

    fun updateCustomPrompt(appRule: AppRuleEntity, customPrompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updatedRule = appRule.copy(customPrompt = customPrompt.ifBlank { null })
                if (updatedRule.id == 0L) {
                    val newId = appRuleRepository.insert(updatedRule)
                    updateCacheAndUI(updatedRule.copy(id = newId))
                } else {
                    appRuleRepository.update(updatedRule)
                    updateCacheAndUI(updatedRule)
                }
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = context.getString(dev.rcht.jist.R.string.app_settings_prompt_saved)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save custom prompt", e)
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = context.getString(dev.rcht.jist.R.string.app_settings_save_failed, e.localizedMessage ?: "Unknown error")
                )
            }
        }
    }

    fun updateMinMessages(appRule: AppRuleEntity, minMessages: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updatedRule = appRule.copy(minMessagesForSummary = minMessages)
                if (updatedRule.id == 0L) {
                    val newId = appRuleRepository.insert(updatedRule)
                    updateCacheAndUI(updatedRule.copy(id = newId))
                } else {
                    appRuleRepository.update(updatedRule)
                    updateCacheAndUI(updatedRule)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save min messages", e)
            }
        }
    }

    fun savePromptAndMinMessages(appRule: AppRuleEntity, customPrompt: String, minMessages: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updatedRule = appRule.copy(
                    customPrompt = customPrompt.ifBlank { null },
                    minMessagesForSummary = minMessages
                )
                if (updatedRule.id == 0L) {
                    val newId = appRuleRepository.insert(updatedRule)
                    updateCacheAndUI(updatedRule.copy(id = newId))
                } else {
                    appRuleRepository.update(updatedRule)
                    updateCacheAndUI(updatedRule)
                }
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = context.getString(dev.rcht.jist.R.string.app_settings_prompt_saved)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save app settings", e)
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = context.getString(dev.rcht.jist.R.string.app_settings_save_failed, e.localizedMessage ?: "Unknown error")
                )
            }
        }
    }

    fun toggleShowAll() {
        _uiState.value = _uiState.value.copy(showAll = !_uiState.value.showAll)
        updateUiStateFromCache()
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        updateUiStateFromCache()
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    companion object {
        private const val TAG = "AppSettingsViewModel"
    }
}
