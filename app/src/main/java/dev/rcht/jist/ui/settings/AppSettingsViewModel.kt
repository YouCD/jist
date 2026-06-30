package dev.rcht.jist.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import dev.rcht.jist.data.db.entity.AppRuleEntity
import dev.rcht.jist.data.repository.AppRuleRepository
import dev.rcht.jist.data.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppSettingsUiState(
    val suggestedApps: List<AppRuleEntity> = emptyList(),
    val otherApps: List<AppRuleEntity> = emptyList(),
    val allAppsCount: Int = 0,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class AppSettingsViewModel(
    private val context: Context,
    private val appRuleRepository: AppRuleRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppSettingsUiState())
    val uiState: StateFlow<AppSettingsUiState> = _uiState
    
    // Cache maintains original alphabetical order (insertion order)
    private var allAppsCache: List<AppRuleEntity> = emptyList()
    // Display order maintains the sorted order for UI
    private var displayOrderOtherApps: List<String> = emptyList()

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

                // Get existing rules from database
                val existingRules = appRuleRepository.getAll().associateBy { it.packageName }

                // Build app list
                val allApps = launcherApps.mapNotNull { resolveInfo ->
                    try {
                        val packageName = resolveInfo.activityInfo.packageName
                        val appLabel = resolveInfo.loadLabel(packageManager).toString()
                        val isMessaging = isMessagingOrEmailApp(packageName)
                        existingRules[packageName] ?: AppRuleEntity(
                            packageName = packageName,
                            appName = appLabel,
                            enabled = isMessaging
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.sortedBy { it.appName }

                allAppsCache = allApps
                
                // Initialize display order based on initial sort (active first, then alphabetical)
                val (suggested, others) = separateAndSortApps(allApps)
                displayOrderOtherApps = others.map { it.packageName }
                
                updateUiState(allApps, suggested, others)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error loading apps: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    private fun separateAndSortApps(apps: List<AppRuleEntity>): Pair<List<AppRuleEntity>, List<AppRuleEntity>> {
        val suggested = apps.filter { isMessagingOrEmailApp(it.packageName) }
        val others = apps
            .filterNot { isMessagingOrEmailApp(it.packageName) }
            .sortedWith(compareByDescending<AppRuleEntity> { it.enabled }.thenBy { it.appName })
        return Pair(suggested, others)
    }

    private fun updateUiState(
        apps: List<AppRuleEntity>, 
        suggested: List<AppRuleEntity>, 
        others: List<AppRuleEntity>
    ) {
        _uiState.value = _uiState.value.copy(
            suggestedApps = suggested,
            otherApps = others,
            allAppsCount = apps.size,
            isLoading = false,
            error = null
        )
    }

    private fun updateUiStateFromCache() {
        // 1. Filter by search query
        val query = _uiState.value.searchQuery
        val filteredApps = if (query.isBlank()) {
            allAppsCache
        } else {
            allAppsCache.filter { 
                it.appName.contains(query, ignoreCase = true) || 
                it.packageName.contains(query, ignoreCase = true) 
            }
        }

        // 2. Separate Suggested apps (these get re-filtered)
        val suggested = filteredApps.filter { isMessagingOrEmailApp(it.packageName) }
        
        // 3. Other apps - maintain display order, not re-sort
        val otherAppsMap = filteredApps.filterNot { isMessagingOrEmailApp(it.packageName) }
            .associateBy { it.packageName }
        
        // Maintain display order using the cached order
        val others = displayOrderOtherApps
            .mapNotNull { packageName -> otherAppsMap[packageName] }
        
        _uiState.value = _uiState.value.copy(
            suggestedApps = suggested,
            otherApps = others,
            allAppsCount = allAppsCache.size
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
            // Batch update in DB logic would go here, loop for now
            updatedApps.forEach {
                if (it.id == 0L) appRuleRepository.insert(it) else appRuleRepository.update(it)
            }
            allAppsCache = updatedApps
            
            // For toggle all, we re-sort since it's a bulk operation
            val (suggested, others) = separateAndSortApps(updatedApps)
            displayOrderOtherApps = others.map { it.packageName }
            updateUiState(updatedApps, suggested, others)
        }
    }

    fun disableAllNonSuggested() {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedApps = allAppsCache.map { app ->
                if (isMessagingOrEmailApp(app.packageName)) {
                    app // Keep suggested apps as they are
                } else {
                    app.copy(enabled = false)
                }
            }
            // Batch update in DB
            updatedApps.forEach {
                if (it.id == 0L) appRuleRepository.insert(it) else appRuleRepository.update(it)
            }
            allAppsCache = updatedApps
            
            // For disable all, we re-sort since it's a bulk operation
            val (suggested, others) = separateAndSortApps(updatedApps)
            displayOrderOtherApps = others.map { it.packageName }
            updateUiState(updatedApps, suggested, others)
        }
    }

    fun updateCustomPrompt(appRule: AppRuleEntity, customPrompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedRule = appRule.copy(customPrompt = customPrompt.ifBlank { null })
            if (updatedRule.id == 0L) {
                val newId = appRuleRepository.insert(updatedRule)
                updateCacheAndUI(updatedRule.copy(id = newId))
            } else {
                appRuleRepository.update(updatedRule)
                updateCacheAndUI(updatedRule)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        updateUiStateFromCache()
    }

    private fun isMessagingOrEmailApp(packageName: String): Boolean {
        return dev.rcht.jist.util.MessagingApps.isMessagingOrEmailApp(packageName)
    }

    companion object {
        private const val TAG = "AppSettingsViewModel"
    }
}
