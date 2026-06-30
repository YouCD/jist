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
import dev.rcht.jist.data.db.entity.LlmConfigEntity
import dev.rcht.jist.llm.LlmClientFactory
import dev.rcht.jist.llm.LlmRequestConfig
import dev.rcht.jist.llm.LlmResult
import dev.rcht.jist.llm.model.ChatMessage
import dev.rcht.jist.ui.screens.onboarding.OnboardingStepIndicator
import dev.rcht.jist.ui.screens.onboarding.PermissionToggleCard
import dev.rcht.jist.ui.screens.onboarding.WritingStyleCard
import dev.rcht.jist.ui.components.GlassScaffold
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
    val pkg = context.packageName

    var step by rememberSaveable { mutableStateOf(0) }
    val totalSteps = 5

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
    var modelName by remember { mutableStateOf("gpt-4o-mini") }
    var temperature by remember { mutableStateOf(0.7f) }
    var maxTokens by remember { mutableStateOf(1000) }
    var testConnectionResult by remember { mutableStateOf<String?>(null) }
    var testConnectionLoading by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf("") }
    
    // Refresh function
    fun refreshStatuses() {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(context).contains(pkg)
        batteryIgnored = try {
            val pm = context.getSystemService(PowerManager::class.java)
            pm?.isIgnoringBatteryOptimizations(pkg) ?: false
        } catch (_: Exception) { false }
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
            .readTimeout(30, TimeUnit.SECONDS)
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
                        if (step < totalSteps - 1) {
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
                        4 -> apiKey.isNotBlank() && modelName.isNotBlank()
                        else -> true
                    }
                ) {
                    Text(
                        text = if (step == totalSteps - 1) stringResource(R.string.llm_save_config) else stringResource(R.string.onboarding_next_step),
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
                        3 -> Step4ManageApps(
                            onOpenAppSettings = onOpenAppSettings
                        )
                        4 -> Step6LlmConfiguration(
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
                            onBaseUrlChange = { baseUrl = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Step1GetStarted(
    notificationsEnabled: Boolean,
    onToggleNotifications: (Boolean) -> Unit
) {
    val context = LocalContext.current
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(48.dp))
        
        // Central Element Image with blur gradient background
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            // Blur gradient ball behind the image
            Box(
                modifier = Modifier
                    .size(500.dp)
                    .offset(y = (-40).dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                            )
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
            
            // Central Element Image
            Image(
                painter = painterResource(id = R.drawable.central_element),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .size(220.dp)
                    .offset(y = (-10).dp),
                contentScale = ContentScale.Fit
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.onboarding_get_started),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = stringResource(R.string.onboarding_magic),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        PermissionToggleCard(
            title = stringResource(R.string.onboarding_notifications),
            description = stringResource(R.string.onboarding_realtime),
            icon = Icons.Default.Notifications,
            isChecked = notificationsEnabled,
            onCheckedChange = onToggleNotifications
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                modifier = Modifier.size(16.dp).padding(top=2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.onboarding_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun Step2EnableAccess(
    listenerEnabled: Boolean,
    onOpenSettings: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(48.dp))
        
        // Notification Access Image with blur gradient background
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            // Blur gradient ball behind the image
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .offset(y = 20.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                            )
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
            
            // Notification Access Image
            Image(
                painter = painterResource(id = R.drawable.notificationaccess),
                contentDescription = stringResource(R.string.onboarding_notification_access),
                modifier = Modifier
                    .size(220.dp)
                    .offset(y = (-10).dp),
                contentScale = ContentScale.Fit
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.onboarding_enable_access),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = stringResource(R.string.onboarding_notifications_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Toggle card showing current status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.onboarding_notification_access), style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (listenerEnabled) stringResource(R.string.active) else stringResource(R.string.disabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (listenerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = listenerEnabled, onCheckedChange = null)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (!listenerEnabled) {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.onboarding_open_settings), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_access_enabled),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun Step3InstantSummaries(
    batteryIgnored: Boolean,
    onRequestBattery: () -> Unit
) {
    val context = LocalContext.current
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(48.dp))
        
        // AI Summary Image with blur gradient background
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            // Blur gradient ball behind the image
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .offset(y = (-20).dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                            )
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
            
            // AI Summary Image
            Image(
                painter = painterResource(id = R.drawable.aisummary),
                contentDescription = stringResource(R.string.onboarding_instant_summaries),
                modifier = Modifier
                    .size(300.dp)
                    .offset(y = (-10).dp),
                contentScale = ContentScale.Fit
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.onboarding_instant_summaries),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = stringResource(R.string.onboarding_battery_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Battery info card with detailed instructions
        Card(
             modifier = Modifier.fillMaxWidth(),
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
             shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.BatteryStd, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.onboarding_battery_settings), style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (batteryIgnored) stringResource(R.string.onboarding_unrestricted_access) else stringResource(R.string.onboarding_needs_attention),
                            style = MaterialTheme.typography.bodySmall, 
                            color = if (batteryIgnored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    if (batteryIgnored) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                if (!batteryIgnored) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "On the next screen:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = stringResource(R.string.onboarding_battery_step1) + "\n" + stringResource(R.string.onboarding_battery_step2) + "\n" + stringResource(R.string.onboarding_battery_step3) + "\n" + stringResource(R.string.onboarding_battery_step4),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (!batteryIgnored) {
            Button(
                onClick = onRequestBattery,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.onboarding_open_battery), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = stringResource(R.string.onboarding_battery_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_background_enabled), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun Step4ManageApps(
    onOpenAppSettings: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(120.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Apps,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.onboarding_choose_apps),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onboarding_apps_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onOpenAppSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Outlined.Apps, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.onboarding_manage_apps),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_apps_suggested),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun Step6LlmConfiguration(
    selectedProvider: String,
    onSelectProvider: (String) -> Unit,
    modelName: String,
    onModelNameChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    temperature: Float,
    onTemperatureChange: (Float) -> Unit,
    maxTokens: Int,
    onMaxTokensChange: (Int) -> Unit,
    onTestConnection: () -> Unit,
    testConnectionResult: String?,
    testConnectionLoading: Boolean,
    summaryTone: String,
    onSelectTone: (String) -> Unit,
    summaryLength: String,
    onSelectLength: (String) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit
) {
    var showApiKey by remember { mutableStateOf(false) }
    var showAdvancedSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.onboarding_configure_ai),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.onboarding_ai_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Provider Cards
        Text(
            text = stringResource(R.string.llm_ai_provider),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        val providers = listOf("OPENAI", "ANTHROPIC")
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            providers.forEach { provider ->
                val isSelected = provider == selectedProvider
                
                val (primaryColor, _) = when (provider) {
                    "OPENAI" -> Pair(Color(0xFF10A37F), "O")
                    "ANTHROPIC" -> Pair(Color(0xFFCC785C), "A")
                    else -> Pair(MaterialTheme.colorScheme.primary, "?")
                }
                
                Card(
                    modifier = Modifier
                        .width(110.dp)
                        .height(110.dp)
                        .clickable { 
                            onSelectProvider(provider)
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) primaryColor.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, primaryColor) else null
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Spacer(modifier = Modifier.weight(1f))
                            
                            // Logo image with blurred radial gradient background
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(
                                        if (isSelected) primaryColor.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.32f),
                                        RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val drawableId = when (provider) {
                                    "OPENAI" -> R.drawable.openai
                                    "ANTHROPIC" -> R.drawable.anthropic
                                    else -> R.drawable.ic_launcher_foreground
                                }

                                // Blurred radial gradient ball behind the logo
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .offset(y = (0).dp)
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(primaryColor.copy(alpha = 0.30f), Color.Transparent)
                                            ),
                                            shape = CircleShape
                                        )
                                        .blur(12.dp)
                                )

                                // Logo image with slight top margin
                                Image(
                                    painter = painterResource(id = drawableId),
                                    contentDescription = "$provider logo",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .offset(y = 0.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = when (provider) {
                                    "OPENAI" -> stringResource(R.string.llm_provider_openai)
                                    "ANTHROPIC" -> stringResource(R.string.llm_provider_anthropic)
                                    else -> provider
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Model
        Text(
            text = stringResource(R.string.llm_model),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = modelName,
            onValueChange = onModelNameChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.llm_custom_model_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Base URL
        Text(
            text = stringResource(R.string.llm_base_url),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.llm_base_url_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.llm_api_key_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Authentication
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.llm_authentication),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.llm_api_key_hint)) },
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Default.Lock else Icons.Default.Lock,
                            contentDescription = if (showApiKey) stringResource(R.string.llm_hide) else stringResource(R.string.llm_show)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Additional Settings (Collapsible)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvancedSettings = !showAdvancedSettings }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.llm_additional_settings),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (showAdvancedSettings) 
                            Icons.Default.KeyboardArrowUp 
                        else 
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = if (showAdvancedSettings) stringResource(R.string.llm_collapse) else stringResource(R.string.llm_expand)
                    )
                }

                // Expandable content
                AnimatedVisibility(
                    visible = showAdvancedSettings,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Temperature
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.llm_temperature),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${String.format("%.1f", temperature)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Slider(
                                value = temperature,
                                onValueChange = { onTemperatureChange(it) },
                                valueRange = 0f..2f,
                                steps = 19
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.llm_precise),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.llm_creative),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Max Tokens
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.llm_max_tokens),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "$maxTokens",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Slider(
                                value = maxTokens.toFloat(),
                                onValueChange = { onMaxTokensChange(it.toInt()) },
                                valueRange = 100f..4000f,
                                steps = 38
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.onboarding_100),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.onboarding_4000),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Test Button
            Button(
                onClick = onTestConnection,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                enabled = apiKey.isNotBlank() && !testConnectionLoading && modelName.isNotBlank()
            ) {
                if (testConnectionLoading) {
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
                onClick = { /* Save handled by main flow */ },
                modifier = Modifier.weight(1f),
                enabled = apiKey.isNotBlank() && modelName.isNotBlank()
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.save))
            }
        }

        // Test Connection Result
        if (testConnectionResult != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (testConnectionResult!!.startsWith("✓"))
                        Color(0xFF4CAF50).copy(alpha = 0.1f)
                    else
                        Color(0xFFE57373).copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (testConnectionResult!!.startsWith("✓"))
                            Icons.Default.Check
                        else
                            Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (testConnectionResult!!.startsWith("✓"))
                            Color(0xFF4CAF50)
                        else
                            Color(0xFFE57373)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = testConnectionResult!!,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun ProviderCard(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.2f)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha=0.2f)
    
    Card(
        modifier = modifier.height(80.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = if(selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
fun ToneChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}
