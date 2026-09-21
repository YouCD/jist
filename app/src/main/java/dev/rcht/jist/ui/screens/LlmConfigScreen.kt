package dev.rcht.jist.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rcht.jist.data.db.entity.LlmConfigEntity
import dev.rcht.jist.llm.LlmClientFactory
import dev.rcht.jist.ui.components.GlassScaffold
import dev.rcht.jist.ui.components.JistSnackbarHost
import dev.rcht.jist.ui.settings.LlmConfigUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import dev.rcht.jist.R
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmConfigScreen(
    uiState: LlmConfigUiState = LlmConfigUiState(),
    onSaveConfig: (LlmConfigEntity) -> Unit = {},
    onDeleteConfig: (LlmConfigEntity) -> Unit = {},
    onTestConnection: (LlmConfigEntity) -> Unit = {},
    onClearTestResult: () -> Unit = {},
    onSetDefault: (LlmConfigEntity) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateToAppSettings: () -> Unit = {},
    onRunOnboarding: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Provider and model state
    val providers = listOf("OPENAI", "ANTHROPIC")
    
    // Form state
    var selectedProvider by remember { mutableStateOf(uiState.selectedConfig?.provider ?: "OPENAI") }
    var isCustomModel by remember { mutableStateOf(false) }
    var customModelName by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf(uiState.selectedConfig?.modelId ?: "") }
    var apiKey by remember { mutableStateOf(uiState.selectedConfig?.apiKey ?: "") }
    
    // Additional settings state
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf(uiState.selectedConfig?.temperature ?: 0.7f) }
    var maxTokens by remember { mutableStateOf(uiState.selectedConfig?.maxTokens ?: 1000) }
    var baseUrl by remember { mutableStateOf(uiState.selectedConfig?.baseUrl ?: "") }
    // 自定义请求头：输入框显示值与 headersText 镜像同步（击键时二者恒等，不会重置文本/光标）；
    // headersInitText 仅在加载配置时变化、作为 key 驱动输入框重建；显示值取 headersText 可保证
    // 折叠卡片后输入框重建时仍能恢复最新编辑内容（修复折叠/展开后显示值与实际保存值不一致）
    var headersText by remember { mutableStateOf("") }
    var headersInitText by remember { mutableStateOf(uiState.selectedConfig?.customHeaders ?: "") }

    // 滚动跟踪：自定义请求头输入框聚焦时，自动滚动到可视区上部（键盘弹出后会遮挡底部）
    // 统一使用窗口坐标系（positionInWindow），避免不同节点 positionInRoot 坐标系不一致导致计算错误
    val scrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    
    // Dropdown state
    var expandedModel by remember { mutableStateOf(false) }
    
    // Fetched models state
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelsLoading by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }
    
    // Fetch available models function
    fun fetchModels() {
        val effectiveBaseUrl = baseUrl.ifBlank { LlmClientFactory.getDefaultBaseUrl(selectedProvider) }
        if (effectiveBaseUrl.isBlank() || apiKey.isBlank()) {
            modelsError = "Please enter API key and base URL first"
            return
        }

        modelsLoading = true
        modelsError = null

        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val httpClient = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.MINUTES)
                    .build()

                val result = LlmClientFactory.fetchAvailableModels(effectiveBaseUrl, apiKey, httpClient)
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    modelsLoading = false
                    
                    result.fold(
                        onSuccess = { models ->
                            fetchedModels = models
                            modelsError = null
                            val currentModel = if (isCustomModel) customModelName else selectedModel
                            if (currentModel.isBlank() || currentModel !in models) {
                                selectedModel = models.firstOrNull() ?: ""
                                isCustomModel = false
                                customModelName = ""
                            }
                        },
                        onFailure = { error ->
                            modelsError = error.message
                            fetchedModels = emptyList()
                        }
                    )
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    modelsLoading = false
                    modelsError = "Failed to fetch models: ${e.message}"
                    fetchedModels = emptyList()
                }
            }
        }
    }
    
    // Update state when selected config changes
    LaunchedEffect(uiState.selectedConfig) {
        uiState.selectedConfig?.let { config ->
            selectedProvider = config.provider
            apiKey = config.apiKey
            temperature = config.temperature
            maxTokens = config.maxTokens
            baseUrl = config.baseUrl
            headersInitText = config.customHeaders
            headersText = config.customHeaders
            selectedModel = config.modelId
            isCustomModel = false
            customModelName = ""
        }
    }

    // 保存成功后：留在本页、保留表单值，仅收起键盘退出编辑态（可继续编辑再次保存）
    val focusManager = LocalFocusManager.current
    LaunchedEffect(uiState.savedToken) {
        if (uiState.savedToken == 0L) return@LaunchedEffect
        focusManager.clearFocus(force = true)
    }
    
    GlassScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.llm_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        snackbarHost = { JistSnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // AI Provider Section
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.llm_ai_provider),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    if (uiState.selectedConfig != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            )
                            Text(
                                text = stringResource(R.string.active),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Provider Cards
                LlmProviderCardsRow(
                    selectedProvider = selectedProvider,
                    onSelectProvider = { provider ->
                        selectedProvider = provider
                        isCustomModel = false
                    }
                )
            }
            
            // Model Selection
            LlmModelSelectionSection(
                selectedModel = selectedModel,
                isCustomModel = isCustomModel,
                customModelName = customModelName,
                onCustomModelNameChange = { customModelName = it },
                expandedModel = expandedModel,
                onExpandedModelChange = { expandedModel = it },
                fetchedModels = fetchedModels,
                onModelSelected = { model ->
                    selectedModel = model
                    isCustomModel = false
                },
                onCustomModelToggle = { isCustomModel = true },
                onFetchModels = { fetchModels() },
                modelsLoading = modelsLoading,
                modelsError = modelsError,
                apiKey = apiKey,
                baseUrl = baseUrl,
                selectedProvider = selectedProvider
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Authentication Section
            LlmAuthenticationSection(
                apiKey = apiKey,
                onApiKeyChange = { apiKey = it }
            )
            
            // Base URL
            LlmBaseUrlField(baseUrl = baseUrl, onBaseUrlChange = { baseUrl = it })

            // Additional Settings (Collapsible)
            LlmAdvancedSettingsCard(
                temperature = temperature,
                onTemperatureChange = { temperature = it },
                maxTokens = maxTokens,
                onMaxTokensChange = { maxTokens = it },
                headersInitText = headersInitText,
                headersCurrentText = headersText,
                onHeadersTextChange = { headersText = it }
            )
            
            // Test Connection Result
            if (uiState.testConnectionResult != null) {
                LlmTestResultCard(result = uiState.testConnectionResult!!)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Test Button
                Button(
                    onClick = {
                        val modelId = if (isCustomModel) customModelName else selectedModel
                        val config = LlmConfigEntity(
                            id = uiState.selectedConfig?.id ?: 0,
                            name = "$selectedProvider Config",
                            provider = selectedProvider,
                            apiKey = apiKey,
                            baseUrl = baseUrl.ifBlank { LlmClientFactory.getDefaultBaseUrl(selectedProvider) },
                        modelId = modelId,
                        isDefault = uiState.selectedConfig?.isDefault ?: false,
                        maxTokens = maxTokens,
                        temperature = temperature,
                        customHeaders = headersText
                        )
                        onTestConnection(config)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    enabled = apiKey.isNotBlank() && !uiState.testConnectionLoading && (!isCustomModel || customModelName.isNotBlank())
                ) {
                    if (uiState.testConnectionLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.llm_test_connection))
                }

                // Save Button
                Button(
                    onClick = {
                        val modelId = if (isCustomModel) customModelName else selectedModel
                        val config = LlmConfigEntity(
                            id = uiState.selectedConfig?.id ?: 0,
                            name = "$selectedProvider Config",
                            provider = selectedProvider,
                            apiKey = apiKey,
                        baseUrl = baseUrl.ifBlank { LlmClientFactory.getDefaultBaseUrl(selectedProvider) },
                        modelId = modelId,
                        isDefault = true,
                        maxTokens = maxTokens,
                        temperature = temperature,
                        customHeaders = headersText
                    )
                    onSaveConfig(config)
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.llm_config_saved))
                    }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = apiKey.isNotBlank() && (!isCustomModel || customModelName.isNotBlank())
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.llm_save_config))
                }
            }
            
            // Existing Configs List (if any)
            if (uiState.configs.size > 1) {
                LlmSavedConfigList(configs = uiState.configs, onDeleteConfig = onDeleteConfig)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    if (uiState.error != null) {
        LaunchedEffect(uiState.error) {
            snackbarHostState.showSnackbar(uiState.error!!)
        }
    }
}


