package dev.rcht.jist.ui.settings

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.preferences.PreferencesRepository
import dev.rcht.jist.data.repository.AppRuleRepository
import dev.rcht.jist.data.repository.LlmConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val userName: String = "Local User",
    val userEmail: String = "local@jist.app",
    val llmModelName: String = "Loading...",
    val summarizationStyle: String = "Concise",
    val summaryTone: String = "PROFESSIONAL",
    val summaryLength: String = "MEDIUM",
    val activeAppCount: Int = 0,
    val notificationsEnabled: Boolean = false,
    val hasSystemNotificationPermission: Boolean = false,
    val dailyDigestTime: String = "08:00 AM",
    val version: String = "1.0.0"
)

class SettingsViewModel(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val appRuleRepository: AppRuleRepository,
    private val llmConfigRepository: LlmConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadActiveAppCount()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val defaultConfig = llmConfigRepository.getDefault()
            val modelName = defaultConfig?.modelId ?: "Not configured"
            val isZh = java.util.Locale.getDefault().language == "zh"
            
            preferencesRepository.preferencesFlow.collect { prefs ->
                val hasSystemPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
                val toneLabel = when (prefs.summaryTone) {
                    "PROFESSIONAL" -> if (isZh) "专业" else "Professional"
                    "CASUAL" -> if (isZh) "随意" else "Casual"
                    "WITTY" -> if (isZh) "幽默" else "Witty"
                    "URGENT" -> if (isZh) "紧急" else "Urgent"
                    else -> prefs.summaryTone.lowercase().replaceFirstChar { it.uppercase() }
                }
                val lengthLabel = when (prefs.summaryLength) {
                    "SHORT" -> if (isZh) "短" else "Short"
                    "MEDIUM" -> if (isZh) "中" else "Medium"
                    "LONG" -> if (isZh) "长" else "Long"
                    else -> prefs.summaryLength.lowercase().replaceFirstChar { it.uppercase() }
                }
                
                _uiState.value = _uiState.value.copy(
                    summarizationStyle = "$toneLabel · $lengthLabel",
                    summaryTone = prefs.summaryTone,
                    summaryLength = prefs.summaryLength,
                    llmModelName = modelName,
                    notificationsEnabled = hasSystemPermission && prefs.notificationsEnabled,
                    hasSystemNotificationPermission = hasSystemPermission
                )
            }
        }
        
        // Load version
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val version = pInfo.versionName ?: "Unknown"
            _uiState.value = _uiState.value.copy(version = version)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun loadActiveAppCount() {
        viewModelScope.launch {
            appRuleRepository.getAllFlow().collect { appRules ->
                // Only count apps that:
                // 1. Are enabled in the database
                // 2. Are currently installed on the device
                // 3. Were manually enabled by the user (not auto-enabled during onboarding)
                val packageManager = context.packageManager
                val installedPackages = try {
                    packageManager.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                        .map { it.packageName }
                        .toSet()
                } catch (e: Exception) {
                    emptySet<String>()
                }
                
                // Count all enabled apps (whether user-enabled or auto-enabled)
                val activeCount = appRules.count { 
                    it.enabled && 
                    installedPackages.contains(it.packageName) 
                }
                _uiState.value = _uiState.value.copy(activeAppCount = activeCount)
            }
        }
    }

    fun toggleNotifications(enabled: Boolean, onPermissionRequired: () -> Unit) {
        viewModelScope.launch {
            val hasSystemPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
            
            if (enabled && !hasSystemPermission) {
                // User wants to enable notifications but doesn't have system permission
                // Call the callback to redirect to settings
                onPermissionRequired()
            } else {
                // Either disabling, or enabling with existing permission
                // Just update the local preference
                preferencesRepository.setNotificationsEnabled(enabled)
                _uiState.value = _uiState.value.copy(
                    notificationsEnabled = enabled,
                    hasSystemNotificationPermission = hasSystemPermission
                )
            }
        }
    }

    fun refreshNotificationPermission() {
        viewModelScope.launch {
            val hasSystemPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
            preferencesRepository.preferencesFlow.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    notificationsEnabled = hasSystemPermission && prefs.notificationsEnabled,
                    hasSystemNotificationPermission = hasSystemPermission
                )
            }
        }
    }

    fun setSummaryTone(tone: String) {
        viewModelScope.launch {
            preferencesRepository.setSummaryTone(tone)
        }
    }

    fun setSummaryLength(length: String) {
        viewModelScope.launch {
            preferencesRepository.setSummaryLength(length)
        }
    }
}
