package dev.rcht.jist.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.db.entity.LlmConfigEntity
import dev.rcht.jist.data.repository.LlmConfigRepository
import dev.rcht.jist.llm.LlmClientFactory
import dev.rcht.jist.llm.LlmRequestConfig
import dev.rcht.jist.llm.client.OpenAiCompatibleClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class LlmConfigUiState(
    val configs: List<LlmConfigEntity> = emptyList(),
    val selectedConfig: LlmConfigEntity? = null,
    val providers: List<String> = listOf("OPENAI", "CLAUDE", "CUSTOM"),
    val isLoading: Boolean = false,
    val testConnectionLoading: Boolean = false,
    val testConnectionResult: String? = null,
    val error: String? = null
)

class LlmConfigViewModel(
    private val llmConfigRepository: LlmConfigRepository,
    private val httpClient: OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(LlmConfigUiState())
    val uiState: StateFlow<LlmConfigUiState> = _uiState

    init {
        loadConfigs()
    }

    private fun loadConfigs() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val configs = llmConfigRepository.getAll()
                val defaultConfig = llmConfigRepository.getDefault()
                _uiState.value = _uiState.value.copy(
                    configs = configs,
                    selectedConfig = defaultConfig,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error loading configs: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun saveConfig(config: LlmConfigEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (config.id == 0L) {
                    llmConfigRepository.insert(config)
                } else {
                    llmConfigRepository.update(config)
                }
                if (config.isDefault) {
                    llmConfigRepository.setDefault(config)
                }
                loadConfigs()
                _uiState.value = _uiState.value.copy(error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error saving config: ${e.message}"
                )
            }
        }
    }

    fun deleteConfig(config: LlmConfigEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                llmConfigRepository.delete(config)
                loadConfigs()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error deleting config: ${e.message}"
                )
            }
        }
    }

    fun setDefaultConfig(config: LlmConfigEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                llmConfigRepository.setDefault(config)
                loadConfigs()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error setting default: ${e.message}"
                )
            }
        }
    }

    fun testConnection(config: LlmConfigEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(testConnectionLoading = true)
            try {
                val llmClient = LlmClientFactory.createClient(config, httpClient)
                val testMessages = listOf(
                    dev.rcht.jist.llm.model.ChatMessage(
                        role = "user",
                        content = "Say 'Connection successful' in exactly these words."
                    )
                )
                val requestConfig = LlmRequestConfig(
                    model = config.modelId,
                    maxTokens = 50,
                    temperature = 0.7f,
                    apiKey = config.apiKey,
                    baseUrl = config.baseUrl
                )

                val result = llmClient.complete(testMessages, requestConfig)
                when (result) {
                    is dev.rcht.jist.llm.LlmResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            testConnectionResult = "✓ Connection successful!\n\nResponse: ${result.data.text.take(100)}",
                            testConnectionLoading = false
                        )
                    }
                    is dev.rcht.jist.llm.LlmResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            testConnectionResult = "✗ Connection failed: ${result.error.message}",
                            testConnectionLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    testConnectionResult = "✗ Error: ${e.message}",
                    testConnectionLoading = false
                )
            }
        }
    }

    fun clearTestResult() {
        _uiState.value = _uiState.value.copy(testConnectionResult = null)
    }

    fun getDefaultBaseUrl(provider: String): String {
        return LlmClientFactory.getDefaultBaseUrl(provider)
    }

    fun getDefaultModel(provider: String): String {
        return LlmClientFactory.getDefaultModel(provider)
    }
}
