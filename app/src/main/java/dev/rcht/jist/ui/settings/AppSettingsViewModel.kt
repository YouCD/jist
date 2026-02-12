package dev.rcht.jist.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.pm.ApplicationInfo
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
    val messagingApps: List<AppRuleEntity> = emptyList(),
    val otherApps: List<AppRuleEntity> = emptyList(),
    val filteredMessagingApps: List<AppRuleEntity> = emptyList(),
    val filteredOtherApps: List<AppRuleEntity> = emptyList(),
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

    init {
        loadAppRules()
    }

    private fun loadAppRules() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val packageManager = context.packageManager
                val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                
                Log.d(TAG, "=== LOADING APPS ===")
                Log.d(TAG, "Total installed apps: ${installedApps.size}")
                
                // Log first 20 and last 5 to check if WhatsApp/Telegram are there
                installedApps.forEachIndexed { i, app ->
                    if (i < 5 || i >= installedApps.size - 5) {
                        Log.d(TAG, "ALL[$i]: ${app.packageName}")
                    }
                }
                
                // Get existing rules from database
                val existingRules = appRuleRepository.getAll().associateBy { it.packageName }

                Log.d(TAG, "Apps in database: ${existingRules.size}")

                // Build app list: use database rules if exist, else create with defaults
                val allApps = installedApps.mapNotNull { appInfo ->
                    try {
                        val packageName = appInfo.packageName
                        val appLabel = try {
                            packageManager.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to get label for $packageName: ${e.message}")
                            packageName
                        }
                        
                        val isMessaging = isMessagingOrEmailApp(packageName)
                        
                        // Skip system components
                        val isSystemComponent = appLabel == packageName && (
                            packageName.count { it == '.' } >= 4 ||
                            packageName.contains("overlay", ignoreCase = true) ||
                            packageName.contains("modules", ignoreCase = true) ||
                            packageName.contains("sdksandbox", ignoreCase = true)
                        )
                        
                        if (isSystemComponent) {
                            Log.d(TAG, "SKIP: $appLabel ($packageName) - system component")
                            null
                        } else {
                            Log.d(TAG, "KEEP: $appLabel ($packageName) - messaging=$isMessaging")
                            // Use database rule if exists, else create with defaults
                            existingRules[packageName] ?: AppRuleEntity(
                                packageName = packageName,
                                appName = appLabel,
                                enabled = isMessaging
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "ERROR: ${appInfo.packageName}: ${e.message}")
                        null
                    }
                }.sortedBy { it.appName }

                // Categorize apps
                val messagingApps = allApps.filter { isMessagingOrEmailApp(it.packageName) }
                val otherApps = allApps.filterNot { isMessagingOrEmailApp(it.packageName) }

                Log.d(TAG, "Final count: ${allApps.size} total")
                Log.d(TAG, "Messaging apps found: ${messagingApps.size}")
                Log.d(TAG, "Other apps: ${otherApps.size}")
                
                if (messagingApps.isNotEmpty()) {
                    messagingApps.forEach { Log.d(TAG, "  ✓ Messaging: ${it.appName} (${it.packageName})") }
                } else {
                    Log.d(TAG, "No messaging apps installed - when you install WhatsApp, Telegram, Gmail, etc., they will appear here")
                    Log.d(TAG, "MessagingApps list has ${dev.rcht.jist.util.MessagingApps.MESSAGING_APP_PACKAGES.size} known apps")
                }
                
                _uiState.value = _uiState.value.copy(
                    messagingApps = messagingApps,
                    otherApps = otherApps,
                    filteredMessagingApps = messagingApps,
                    filteredOtherApps = otherApps,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading apps: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    error = "Error loading apps: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    private fun isMessagingOrEmailApp(packageName: String): Boolean {
        return dev.rcht.jist.util.MessagingApps.isMessagingOrEmailApp(packageName)
    }

    fun toggleAppEnabled(appRule: AppRuleEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updatedRule = appRule.copy(enabled = !appRule.enabled)
                
                // Insert if new, update if existing
                if (appRule.id == 0L) {
                    val newId = appRuleRepository.insert(updatedRule)
                    // Update with new ID
                    updateAppInState(updatedRule.copy(id = newId))
                } else {
                    appRuleRepository.update(updatedRule)
                    updateAppInState(updatedRule)
                }
                
                Log.d(TAG, "✓ App ${appRule.appName} enabled=${updatedRule.enabled}")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating app rule: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    error = "Error updating app rule: ${e.message}"
                )
            }
        }
    }

    private fun updateAppInState(updatedApp: AppRuleEntity) {
        val allApps = _uiState.value.messagingApps + _uiState.value.otherApps
        val updatedAllApps = allApps.map { app ->
            if (app.packageName == updatedApp.packageName) {
                updatedApp
            } else {
                app
            }
        }
        
        // Re-categorize
        val messagingApps = updatedAllApps.filter { isMessagingOrEmailApp(it.packageName) }
        val otherApps = updatedAllApps.filterNot { isMessagingOrEmailApp(it.packageName) }
        
        // Update filtered lists too
        val filteredMessaging = if (_uiState.value.searchQuery.isBlank()) {
            messagingApps
        } else {
            messagingApps.filter { app ->
                app.appName.contains(_uiState.value.searchQuery, ignoreCase = true) ||
                app.packageName.contains(_uiState.value.searchQuery, ignoreCase = true)
            }
        }
        
        val filteredOther = if (_uiState.value.searchQuery.isBlank()) {
            otherApps
        } else {
            otherApps.filter { app ->
                app.appName.contains(_uiState.value.searchQuery, ignoreCase = true) ||
                app.packageName.contains(_uiState.value.searchQuery, ignoreCase = true)
            }
        }
        
        _uiState.value = _uiState.value.copy(
            messagingApps = messagingApps,
            otherApps = otherApps,
            filteredMessagingApps = filteredMessaging,
            filteredOtherApps = filteredOther
        )
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        
        val filteredMessaging = if (query.isBlank()) {
            _uiState.value.messagingApps
        } else {
            _uiState.value.messagingApps.filter { app ->
                app.appName.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            }
        }
        
        val filteredOther = if (query.isBlank()) {
            _uiState.value.otherApps
        } else {
            _uiState.value.otherApps.filter { app ->
                app.appName.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            }
        }
        
        _uiState.value = _uiState.value.copy(
            filteredMessagingApps = filteredMessaging,
            filteredOtherApps = filteredOther
        )
    }

    companion object {
        private const val TAG = "AppSettingsViewModel"
    }
}
