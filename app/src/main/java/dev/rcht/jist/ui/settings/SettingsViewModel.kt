package dev.rcht.jist.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.preferences.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val userName: String = "Local User", // Placeholder
    val userEmail: String = "local@jist.app", // Placeholder
    val llmModelName: String = "Loading...",
    val summarizationStyle: String = "Concise",
    val activeAppCount: Int = 0,
    val notificationsEnabled: Boolean = false,
    val dailyDigestTime: String = "08:00 AM", // Placeholder
    val version: String = "1.0.0"
)

class SettingsViewModel(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    summarizationStyle = prefs.writingStyle.lowercase().replaceFirstChar { it.uppercase() },
                    // In a real app we'd get the actual selected model name here
                    llmModelName = "Configured" 
                )
            }
        }
        
        // Load app count (mock for now, or inject AppRuleRepository)
        // Load version
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val version = pInfo.versionName ?: "Unknown"
            _uiState.value = _uiState.value.copy(version = version)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        // Handle notification toggle (likely just system settings intent, so handled in UI)
        _uiState.value = _uiState.value.copy(notificationsEnabled = enabled)
    }
}
