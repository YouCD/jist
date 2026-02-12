package dev.rcht.jist.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.JistApplication
import dev.rcht.jist.data.db.entity.AppRuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val setupComplete: Boolean = false
)

class OnboardingViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val app = context.applicationContext as JistApplication
    private val pm = context.packageManager

    companion object {
        private const val TAG = "OnboardingViewModel"
    }

    // Do not auto-run setup; UI will call startSetup()
    fun startSetup() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { it.enabled && it.packageName != context.packageName }

                for (appInfo in installedApps) {
                    val packageName = appInfo.packageName
                    if (dev.rcht.jist.util.MessagingApps.isMessagingOrEmailApp(packageName)) {
                        try {
                            val existing = app.appRuleRepository.getByPackageName(packageName)
                            if (existing == null) {
                                val appLabel = pm.getApplicationLabel(appInfo).toString()
                                val appRule = AppRuleEntity(
                                    packageName = packageName,
                                    appName = appLabel,
                                    enabled = true
                                )
                                app.appRuleRepository.insert(appRule)
                                Log.d(TAG, "✓ Enabled $appLabel")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error setting up $packageName: ${e.message}")
                        }
                    }
                }

                Log.d(TAG, "✓ Onboarding setup finished")
                _uiState.value = _uiState.value.copy(isLoading = false, setupComplete = true)
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error during onboarding setup: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Setup failed: ${e.message}")
            }
        }
    }

    fun finishOnboarding() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.preferencesRepository.setOnboardingComplete(true)
                Log.d(TAG, "✓ Onboarding marked complete in preferences")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to mark onboarding complete: ${e.message}")
            }
        }
    }
}

