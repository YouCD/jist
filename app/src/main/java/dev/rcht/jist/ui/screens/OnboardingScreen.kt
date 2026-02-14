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
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.rcht.jist.JistApplication
import dev.rcht.jist.ui.screens.onboarding.OnboardingStepIndicator
import dev.rcht.jist.ui.screens.onboarding.PermissionToggleCard
import dev.rcht.jist.ui.screens.onboarding.WritingStyleCard
import dev.rcht.jist.ui.components.GlassScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    onOpenSettings: () -> Unit = {}
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

    var step by remember { mutableStateOf(0) }
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
    var selectedModel by remember { mutableStateOf("gpt-4-turbo") }
    
    // Refresh function
    fun refreshStatuses() {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(context).contains(pkg)
        batteryIgnored = try {
            val pm = context.getSystemService(PowerManager::class.java)
            pm?.isIgnoringBatteryOptimizations(pkg) ?: false
        } catch (_: Exception) { false }
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
                modifier = Modifier.statusBarsPadding()
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
                            viewModel.saveLlmConfig(apiKey, selectedProvider, selectedModel)
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
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = when (step) {
                        1 -> listenerEnabled // Step 2 requires Notification Listener
                        4 -> apiKey.isNotBlank() && selectedModel.isNotBlank() // Step 5 requires API Key & Model
                        else -> true
                    }
                ) {
                    Text(
                        text = if (step == totalSteps - 1) "Save Configuration" else "Next Step",
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
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
                    modifier = Modifier.fillMaxWidth()
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
                        3 -> Step4WritingStyle(
                            selectedStyle = uiState.writingStyle,
                            onSelectStyle = { viewModel.setWritingStyle(it) }
                        )
                        4 -> Step5LlmConfiguration(
                            selectedProvider = selectedProvider,
                            onSelectProvider = { selectedProvider = it },
                            selectedModel = selectedModel,
                            onSelectModel = { selectedModel = it },
                            apiKey = apiKey,
                            onApiKeyChange = { apiKey = it },
                            summaryTone = uiState.summaryTone,
                            onSelectTone = { viewModel.setSummaryTone(it) },
                            summaryLength = uiState.summaryLength,
                            onSelectLength = { viewModel.setSummaryLength(it) }
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(20.dp))
        
        // Hero Icon/Image placeholder
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
             Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Get Started",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Jist works its magic by analyzing your notifications to summarize exactly what matters most to you.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        PermissionToggleCard(
            title = "Notifications",
            description = "For real-time summaries",
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
                text = "Your data is processed locally and stays private.",
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
        Spacer(modifier = Modifier.height(40.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                     Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                     Spacer(modifier = Modifier.height(16.dp))
                     Row(
                         modifier = Modifier
                             .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                             .padding(horizontal = 16.dp, vertical = 12.dp),
                         verticalAlignment = Alignment.CenterVertically
                     ) {
                         Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                         Spacer(modifier = Modifier.width(12.dp))
                         Column {
                             Text("Allow Notifications", style = MaterialTheme.typography.labelLarge)
                             Text("Summaries & alerts", style = MaterialTheme.typography.labelSmall)
                         }
                         Spacer(modifier = Modifier.width(24.dp))
                         Switch(checked = listenerEnabled, onCheckedChange = null)
                     }
                 }
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            text = "Enable Access",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Allow Jist to read incoming alerts so our AI can summarize them instantly.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text("Open Settings", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun Step3InstantSummaries(
    batteryIgnored: Boolean,
    onRequestBattery: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(40.dp))
        
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            // Placeholder for battery circle UI
             Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Instant AI Summaries",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Jist needs background access to summarize notifications as they arrive. No waiting for the AI to catch up when you unlock your phone.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
             modifier = Modifier.fillMaxWidth(),
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
             shape = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.BatteryStd, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Battery Optimized", style = MaterialTheme.typography.labelLarge)
                    Text("Minimal impact on your daily battery life.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (!batteryIgnored) {
             Button(
                onClick = onRequestBattery,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("Enable Background Sync", color = MaterialTheme.colorScheme.onSurface)
            }
        } else {
             Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                 Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary) // Green check ideally
                 Spacer(modifier = Modifier.width(8.dp))
                 Text("Background Sync Enabled", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
             }
        }
    }
}

@Composable
fun Step4WritingStyle(
    selectedStyle: String,
    onSelectStyle: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(20.dp))
        
        Box(
            modifier = Modifier.size(60.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "How should Jist write?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Customize the AI personality.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        WritingStyleCard(
            title = "Concise",
            description = "Brief, to-the-point summaries. Perfect for glancing quickly.",
            previewText = "\"Meeting at 3pm.\"",
            icon = Icons.Default.Bolt,
            isSelected = selectedStyle == "CONCISE",
            onClick = { onSelectStyle("CONCISE") }
        )
        
        WritingStyleCard(
            title = "Bullet Points",
            description = "Key takeaways listed out. Easy to scan and digest.",
            previewText = "• Marketing meeting at 3pm\n• Discuss Q4 goals",
            icon = Icons.Default.FormatListBulleted,
            isSelected = selectedStyle == "BULLET_POINTS",
            onClick = { onSelectStyle("BULLET_POINTS") }
        )
    }
}

@Composable
fun Step5LlmConfiguration(
    selectedProvider: String,
    onSelectProvider: (String) -> Unit,
    selectedModel: String,
    onSelectModel: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    summaryTone: String,
    onSelectTone: (String) -> Unit,
    summaryLength: String,
    onSelectLength: (String) -> Unit
) {
    // Define models per provider
    val openAiModels = listOf("gpt-4-turbo", "gpt-4o", "gpt-3.5-turbo")
    val geminiModels = listOf("gemini-1.5-pro", "gemini-1.5-flash", "gemini-pro")
    val localModels = listOf("llama-3-8b", "mistral-7b", "gemma-7b")

    val currentModels = when(selectedProvider) {
        "OPENAI" -> openAiModels
        "GEMINI" -> geminiModels
        "LOCAL" -> localModels
        else -> openAiModels
    }
    
    var isCustomModel by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    // Reset custom model state when provider changes
    LaunchedEffect(selectedProvider) {
        onSelectModel(currentModels.first())
        isCustomModel = false
    }

    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = "LLM Configuration", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(24.dp))

        Text("AI PROVIDER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProviderCard(name = "OpenAI", icon = Icons.Default.AutoAwesome, selected = selectedProvider == "OPENAI", onClick = { onSelectProvider("OPENAI") }, modifier = Modifier.weight(1f))
            ProviderCard(name = "Gemini", icon = Icons.Default.Diamond, selected = selectedProvider == "GEMINI", onClick = { onSelectProvider("GEMINI") }, modifier = Modifier.weight(1f))
            ProviderCard(name = "Local", icon = Icons.Default.SdStorage, selected = selectedProvider == "LOCAL", onClick = { onSelectProvider("LOCAL") }, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Model Version", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.1f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.3f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (isCustomModel) "Custom Model" else selectedModel, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                currentModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            onSelectModel(model)
                            isCustomModel = false
                            expanded = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Custom...") },
                    onClick = {
                        isCustomModel = true
                        onSelectModel("") // Clear for input
                        expanded = false
                    }
                )
            }
        }
        
        if (isCustomModel) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = selectedModel,
                onValueChange = onSelectModel,
                label = { Text("Enter Model Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("AUTHENTICATION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("API Key") },
            visualTransformation = PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { /* Paste logic */ }) { Text("Paste") }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.1f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.1f),
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha=0.5f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha=0.3f)
            )
        )
        Text("We prioritize privacy. Keys are encrypted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top=4.dp))

        Spacer(modifier = Modifier.height(24.dp))
        
        Text("OUTPUT PREFERENCES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        
        Text("Summary Tone", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToneChip("Professional", summaryTone == "PROFESSIONAL") { onSelectTone("PROFESSIONAL") }
            ToneChip("Casual", summaryTone == "CASUAL") { onSelectTone("CASUAL") }
            ToneChip("Witty", summaryTone == "WITTY") { onSelectTone("WITTY") }
             ToneChip("Urgent", summaryTone == "URGENT") { onSelectTone("URGENT") }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
             Text("Summary Length", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
             Text(summaryLength, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        
        Slider(
            value = when(summaryLength) { "SHORT" -> 0f; "MEDIUM" -> 1f; else -> 2f },
            onValueChange = { 
                val newLength = when(it.toInt()) { 0 -> "SHORT"; 1 -> "MEDIUM"; else -> "LONG" }
                onSelectLength(newLength)
            },
            valueRange = 0f..2f,
            steps = 1
        )
         Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
             Text("CONCISE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
             Text("DETAILED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        Spacer(modifier = Modifier.height(80.dp)) // Padding for bottom bar
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
