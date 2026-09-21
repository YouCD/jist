package dev.rcht.jist.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.rcht.jist.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.rcht.jist.JistApplication
import dev.rcht.jist.data.config.ConfigManager
import dev.rcht.jist.data.db.entity.LlmConfigEntity
import dev.rcht.jist.llm.LlmClientFactory
import dev.rcht.jist.llm.LlmRequestConfig
import dev.rcht.jist.llm.LlmResult
import dev.rcht.jist.llm.model.ChatMessage
import dev.rcht.jist.ui.screens.onboarding.OnboardingStepIndicator
import dev.rcht.jist.ui.screens.onboarding.PermissionToggleCard
import dev.rcht.jist.ui.screens.onboarding.WritingStyleCard
import dev.rcht.jist.ui.components.GlassScaffold
import dev.rcht.jist.ui.components.JistSnackbarHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.rcht.jist.ui.theme.JistCyan
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.shape.CircleShape
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    onOpenAppSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: OnboardingViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return OnboardingViewModel(context) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val configManager = remember {
        val jistApp = context.applicationContext as JistApplication
        ConfigManager(context, jistApp.preferencesRepository, jistApp.llmConfigRepository, jistApp.appRuleRepository, jistApp.customPromptRepository, jistApp.watchTopicRepository, jistApp.chatSourceRepository, jistApp.watchedChatRepository)
    }

    val importSuccessMsg = stringResource(R.string.onboarding_import_config_success)
    var importSuccess by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                configManager.importFromUri(uri).fold(
                    onSuccess = {
                        importSuccess = true
                        snackbarHostState.showSnackbar(importSuccessMsg)
                    },
                    onFailure = { e ->
                        snackbarHostState.showSnackbar("${e.message}")
                    }
                )
            }
        }
    }

    val pkg = context.packageName

    var step by rememberSaveable { mutableStateOf(0) }
    val totalSteps = 6

    // Permissions State
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        notificationsEnabled = isGranted
    }
    
    // Listener State
    var listenerEnabled by remember { mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(pkg)) }
    
    // Battery State
    var batteryIgnored by remember { mutableStateOf(try {
        val pm = context.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(pkg) ?: false
    } catch (_: Exception) { false }) }

    // LLM Config State
    var apiKey by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf("OPENAI") }
    var modelName by remember { mutableStateOf("") }
    var temperature by remember { mutableStateOf(0.7f) }
    var maxTokens by remember { mutableStateOf(1000) }
    var testConnectionResult by remember { mutableStateOf<String?>(null) }
    var testConnectionLoading by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf("") }
    
    // Available models state
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelsLoading by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }
    var showModelDropdown by remember { mutableStateOf(false) }
    
    // Refresh function
    fun refreshStatuses() {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(context).contains(pkg)
        batteryIgnored = try {
            val pm = context.getSystemService(PowerManager::class.java)
            pm?.isIgnoringBatteryOptimizations(pkg) ?: false
        } catch (_: Exception) { false }
    }

    // Fetch available models function
    fun fetchModels() {
        val effectiveBaseUrl = baseUrl.ifBlank { LlmClientFactory.getDefaultBaseUrl(selectedProvider) }
        if (effectiveBaseUrl.isBlank() || apiKey.isBlank()) {
            modelsError = "Please enter API key and base URL first"
            return
        }

        modelsLoading = true
        modelsError = null
        availableModels = emptyList()

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
                            availableModels = models
                            modelsError = null
                            if (modelName.isBlank() || modelName !in models) {
                                modelName = models.firstOrNull() ?: ""
                            }
                        },
                        onFailure = { error ->
                            modelsError = error.message
                            availableModels = emptyList()
                        }
                    )
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    modelsLoading = false
                    modelsError = "Failed to fetch models: ${e.message}"
                    availableModels = emptyList()
                }
            }
        }
    }

    // Test connection function
    fun testConnection() {
        val effectiveBaseUrl = baseUrl.ifBlank { LlmClientFactory.getDefaultBaseUrl(selectedProvider) }
        val config = LlmConfigEntity(
            id = 0,
            name = "$selectedProvider Config",
            provider = selectedProvider,
            apiKey = apiKey,
            baseUrl = effectiveBaseUrl,
            modelId = modelName,
            isDefault = true,
            temperature = temperature,
            maxTokens = maxTokens
        )

        val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()

        testConnectionLoading = true
        testConnectionResult = null

        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val llmClient = LlmClientFactory.createClient(config, httpClient)
                val testMessages = listOf(
                    ChatMessage(role = "user", content = "Say 'OK' if you receive this.")
                )
                val requestConfig = LlmRequestConfig(
                    model = modelName,
                    maxTokens = 10,
                    temperature = 0.7f,
                    apiKey = apiKey,
                    baseUrl = effectiveBaseUrl
                )

                val result = llmClient.complete(testMessages, requestConfig)
                testConnectionLoading = false
                testConnectionResult = when (result) {
                    is LlmResult.Success -> context.getString(R.string.onboarding_connection_success)
                    is LlmResult.Error -> "✗ Connection failed: ${result.error.message}"
                }
            } catch (e: Exception) {
                testConnectionLoading = false
                testConnectionResult = "✗ Connection failed: ${e.message}"
            }
        }
    }

    // Poll for status updates
    LaunchedEffect(step) {
        while (true) {
            refreshStatuses()
            delay(1000)
        }
    }

    GlassScaffold(
        snackbarHost = { JistSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            OnboardingStepIndicator(
                currentStep = step,
                totalSteps = totalSteps,
                modifier = Modifier.statusBarsPadding(),
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                Button(
                    onClick = {
                        if (step == 3 && importSuccess) {
                            viewModel.startSetup()
                            viewModel.finishOnboarding()
                            onOnboardingComplete()
                        } else if (step < totalSteps - 1) {
                            step++
                        } else {
                            viewModel.saveLlmConfig(apiKey, selectedProvider, modelName, temperature, maxTokens, baseUrl)
                            viewModel.startSetup()
                            viewModel.finishOnboarding()
                            onOnboardingComplete()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JistCyan,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = when (step) {
                        1 -> listenerEnabled
                        5 -> apiKey.isNotBlank() && modelName.isNotBlank()
                        else -> true
                    }
                ) {
                    Text(
                        text = when {
                            step == 3 && importSuccess -> stringResource(R.string.done)
                            step == totalSteps - 1 -> stringResource(R.string.llm_save_config)
                            else -> stringResource(R.string.onboarding_next_step)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { width -> width } + fadeIn() with
                        slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        slideInHorizontally { width -> -width } + fadeIn() with
                        slideOutHorizontally { width -> width } + fadeOut()
                    }
                },
                label = "OnboardingStep"
            ) { currentStep ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (currentStep) {
                        0 -> Step1GetStarted(
                            notificationsEnabled = notificationsEnabled,
                            onToggleNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, pkg) }
                                    context.startActivity(intent)
                                }
                            }
                        )
                        1 -> Step2EnableAccess(
                            listenerEnabled = listenerEnabled,
                            onOpenSettings = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        )
                        2 -> Step3InstantSummaries(
                            batteryIgnored = batteryIgnored,
                            onRequestBattery = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:$pkg") }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                }
                            }
                        )
                        3 -> Step4ImportConfig(
                            importSuccess = importSuccess,
                            onSelectFile = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                        )
                        4 -> Step4ManageApps(
                            onOpenAppSettings = onOpenAppSettings
                        )
                        5 -> Step6LlmConfiguration(
                            selectedProvider = selectedProvider,
                            onSelectProvider = { selectedProvider = it },
                            modelName = modelName,
                            onModelNameChange = { modelName = it },
                            apiKey = apiKey,
                            onApiKeyChange = { apiKey = it },
                            temperature = temperature,
                            onTemperatureChange = { temperature = it },
                            maxTokens = maxTokens,
                            onMaxTokensChange = { maxTokens = it },
                            onTestConnection = { testConnection() },
                            testConnectionResult = testConnectionResult,
                            testConnectionLoading = testConnectionLoading,
                            summaryTone = uiState.summaryTone,
                            onSelectTone = { viewModel.setSummaryTone(it) },
                            summaryLength = uiState.summaryLength,
                            onSelectLength = { viewModel.setSummaryLength(it) },
                            baseUrl = baseUrl,
                            onBaseUrlChange = { baseUrl = it },
                            availableModels = availableModels,
                            modelsLoading = modelsLoading,
                            modelsError = modelsError,
                            onFetchModels = { fetchModels() },
                            showModelDropdown = showModelDropdown,
                            onShowModelDropdownChange = { showModelDropdown = it }
                        )
                    }
                }
            }
        }
    }
}
