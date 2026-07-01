package dev.rcht.jist.ui.watchedit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rcht.jist.data.db.entity.WatchTopicEntity
import dev.rcht.jist.data.repository.LlmConfigRepository
import dev.rcht.jist.data.repository.WatchTopicRepository
import dev.rcht.jist.llm.LlmClient
import dev.rcht.jist.llm.LlmClientFactory
import dev.rcht.jist.llm.LlmRequestConfig
import dev.rcht.jist.llm.LlmResult
import dev.rcht.jist.llm.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray

data class WatchEditUiState(
    val isEditing: Boolean = false,
    val title: String = "",
    val description: String = "",
    val keywords: List<String> = emptyList(),
    val matchMode: String = "KEYWORD_ONLY",
    val isEnabled: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val existingId: Long? = null,
    val isGeneratingKeywords: Boolean = false,
    val generatedKeywords: List<String> = emptyList()
)

class WatchEditViewModel(
    private val watchTopicRepository: WatchTopicRepository,
    private val watchId: Long?,
    private val errorTitleRequired: String,
    private val errorKeywordRequired: String,
    private val errorSaveFailed: String,
    private val llmConfigRepository: LlmConfigRepository,
    private val httpClient: OkHttpClient,
    private val keywordPromptTemplate: String,
    private val generateError: String,
    private val errorTitleDuplicate: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchEditUiState())
    val uiState: StateFlow<WatchEditUiState> = _uiState

    init {
        if (watchId != null) {
            loadExisting(watchId)
        }
    }

    private fun loadExisting(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val topic = watchTopicRepository.getById(id) ?: return@launch
                val keywords = try {
                    kotlinx.serialization.json.Json
                        .decodeFromString<List<String>>(topic.keywords)
                } catch (_: Exception) { emptyList() }

                _uiState.value = WatchEditUiState(
                    isEditing = true,
                    title = topic.title,
                    description = topic.description,
                    keywords = keywords,
                    matchMode = topic.matchMode,
                    isEnabled = topic.isEnabled,
                    existingId = topic.id
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading topic", e)
            }
        }
    }

    fun updateTitle(value: String) { _uiState.value = _uiState.value.copy(title = value) }
    fun updateDescription(value: String) { _uiState.value = _uiState.value.copy(description = value) }
    fun updateMatchMode(value: String) { _uiState.value = _uiState.value.copy(matchMode = value) }

    fun addKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return
        val current = _uiState.value.keywords
        if (current.contains(trimmed)) return
        _uiState.value = _uiState.value.copy(keywords = current + trimmed)
    }

    fun removeKeyword(keyword: String) {
        _uiState.value = _uiState.value.copy(
            keywords = _uiState.value.keywords - keyword
        )
    }

    fun generateKeywords() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.value = state.copy(error = errorTitleRequired)
            return
        }
        if (state.isGeneratingKeywords) return
        _uiState.value = state.copy(isGeneratingKeywords = true, error = null, generatedKeywords = emptyList())
        Log.d(TAG, "generateKeywords started, title=${state.title}")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = llmConfigRepository.getDefaultConfig()
                if (config == null) {
                    Log.w(TAG, "No LLM config found")
                    _uiState.value = _uiState.value.copy(
                        isGeneratingKeywords = false,
                        error = generateError
                    )
                    return@launch
                }
                Log.d(TAG, "LLM config found, model=${config.modelId}")
                val client = LlmClientFactory.createClient(config, httpClient)
                val llmConfig = LlmRequestConfig(
                    model = config.modelId,
                    maxTokens = 256,
                    temperature = 0.3f,
                    apiKey = config.apiKey,
                    baseUrl = config.baseUrl
                )
                val promptText = keywordPromptTemplate
                    .replace("{title}", state.title)
                    .replace("{description}", state.description)
                val prompt = listOf(
                    ChatMessage("user", promptText)
                )
                Log.d(TAG, "Calling LLM for keyword generation")
                val response = try {
                    client.complete(prompt, llmConfig)
                } catch (e: Exception) {
                    Log.e(TAG, "LLM call failed", e)
                    _uiState.value = _uiState.value.copy(
                        isGeneratingKeywords = false,
                        error = generateError
                    )
                    return@launch
                }
                val text = when (response) {
                    is LlmResult.Success -> {
                        Log.d(TAG, "LLM response received")
                        response.data.text
                    }
                    is LlmResult.Error -> {
                        Log.w(TAG, "LLM error: ${response.error.message}")
                        _uiState.value = _uiState.value.copy(
                            isGeneratingKeywords = false,
                            error = generateError
                        )
                        return@launch
                    }
                }
                val generated = try {
                    val trimmed = text.trim()
                    val start = trimmed.indexOf('[')
                    val end = trimmed.lastIndexOf(']')
                    if (start >= 0 && end > start) {
                        val arr = JSONArray(trimmed.substring(start, end + 1))
                        (0 until arr.length()).map { arr.getString(it) }
                    } else emptyList()
                } catch (_: Exception) {
                    Log.w(TAG, "Failed to parse keywords from: $text")
                    emptyList()
                }

                val newOnes = generated.filter { it.isNotBlank() }
                Log.d(TAG, "Generated ${generated.size} keywords")
                _uiState.value = _uiState.value.copy(
                    generatedKeywords = newOnes,
                    isGeneratingKeywords = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error generating keywords", e)
                _uiState.value = _uiState.value.copy(
                    isGeneratingKeywords = false,
                    error = generateError
                )
            }
        }
    }

    fun confirmGeneratedKeywords() {
        val state = _uiState.value
        val current = state.keywords
        val newOnes = state.generatedKeywords.filter { it !in current }
        _uiState.value = state.copy(
            keywords = current + newOnes,
            generatedKeywords = emptyList()
        )
    }

    fun discardGeneratedKeywords() {
        _uiState.value = _uiState.value.copy(generatedKeywords = emptyList())
    }

    fun save(onComplete: () -> Unit) {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.value = state.copy(error = errorTitleRequired)
            return
        }
        if (state.keywords.isEmpty()) {
            _uiState.value = state.copy(error = errorKeywordRequired)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val trimmed = state.title.trim()
                val existing = watchTopicRepository.getByTitle(trimmed)
                if (existing != null && existing.id != state.existingId) {
                    _uiState.value = _uiState.value.copy(error = errorTitleDuplicate)
                    return@launch
                }
                val json = JSONArray(state.keywords).toString()
                val entity = WatchTopicEntity(
                    id = state.existingId ?: 0,
                    title = trimmed,
                    description = state.description.trim(),
                    keywords = json,
                    matchMode = state.matchMode,
                    isEnabled = state.isEnabled,
                    updatedAt = System.currentTimeMillis()
                )
                watchTopicRepository.upsert(entity)
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving topic", e)
                _uiState.value = _uiState.value.copy(error = errorSaveFailed.format(e.message))
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    companion object {
        private const val TAG = "WatchEditViewModel"
    }
}
