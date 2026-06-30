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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
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
import dev.rcht.jist.ui.settings.LlmConfigUiState
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import dev.rcht.jist.R

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
    val clipboardManager = LocalClipboardManager.current
    
    // Provider and model state
    val providers = listOf("OPENAI", "ANTHROPIC")
    val modelsByProvider = mapOf(
        "OPENAI" to listOf("gpt-5.2", "gpt-5-mini-2025-08-07", "gpt-4o", "gpt-4o-mini", "o1", "o3-mini"),
        "ANTHROPIC" to listOf("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229", "claude-3-haiku-20240307")
    )
    
    // Form state
    var selectedProvider by remember { mutableStateOf(uiState.selectedConfig?.provider ?: "OPENAI") }
    var isCustomModel by remember { mutableStateOf(false) }
    var customModelName by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf(uiState.selectedConfig?.modelId ?: modelsByProvider["OPENAI"]?.firstOrNull() ?: "") }
    var apiKey by remember { mutableStateOf(uiState.selectedConfig?.apiKey ?: "") }
    var showApiKey by remember { mutableStateOf(false) }
    
    // Additional settings state
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf(uiState.selectedConfig?.temperature ?: 0.7f) }
    var maxTokens by remember { mutableStateOf(uiState.selectedConfig?.maxTokens ?: 1000) }
    
    // Dropdown state
    var expandedModel by remember { mutableStateOf(false) }
    
    // Update state when selected config changes
    LaunchedEffect(uiState.selectedConfig) {
        uiState.selectedConfig?.let { config ->
            selectedProvider = config.provider
            apiKey = config.apiKey
            temperature = config.temperature
            maxTokens = config.maxTokens
            val availableModels = modelsByProvider[config.provider] ?: emptyList()
            if (config.modelId in availableModels) {
                selectedModel = config.modelId
                isCustomModel = false
                customModelName = ""
            } else {
                isCustomModel = true
                customModelName = config.modelId
                if (availableModels.isNotEmpty()) {
                    selectedModel = availableModels.first()
                }
            }
        }
    }

    // Update model when provider changes
    LaunchedEffect(selectedProvider) {
        val availableModels = modelsByProvider[selectedProvider] ?: emptyList()
        if (!isCustomModel && selectedModel !in availableModels && availableModels.isNotEmpty()) {
            selectedModel = availableModels.first()
        }
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    providers.forEach { provider ->
                        val isSelected = provider == selectedProvider
                        
                        // Provider-specific styling
                        val (primaryColor, logoText) = when (provider) {
                            "OPENAI" -> Pair(Color(0xFF10A37F), "O")
                            "ANTHROPIC" -> Pair(Color(0xFFCC785C), "A")
                            else -> Pair(MaterialTheme.colorScheme.primary, "?")
                        }
                        
                        Card(
                            modifier = Modifier
                                .width(110.dp)
                                .height(110.dp)
                                .clickable { 
                                    selectedProvider = provider
                                    isCustomModel = false
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) 
                                    primaryColor.copy(alpha = 0.15f)
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = if (isSelected) 
                                androidx.compose.foundation.BorderStroke(
                                    2.dp, 
                                    primaryColor
                                )
                            else null
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
                                    
                                    // Logo image with blurred radial gradient background and a top margin
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

                                        // Blurred radial gradient ball behind the logo (slightly offset upward)
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .offset(y = (-8).dp)
                                                .background(
                                                    brush = Brush.radialGradient(
                                                        colors = listOf(primaryColor.copy(alpha = 0.30f), Color.Transparent)
                                                    ),
                                                    shape = CircleShape
                                                )
                                                .blur(12.dp)
                                        )

                                        // Logo image (bigger) with slight top margin
                                        Image(
                                            painter = painterResource(id = drawableId),
                                            contentDescription = "$provider logo",
                                            modifier = Modifier
                                                .size(40.dp)
                                                .offset(y = 6.dp)
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
                                        color = if (isSelected) 
                                            primaryColor 
                                        else 
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            
            // Model Selection
            Column {
                Text(
                    text = stringResource(R.string.llm_model_version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (isCustomModel && customModelName.isNotBlank()) customModelName else if (isCustomModel) "" else selectedModel,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        placeholder = {
                            if (isCustomModel && customModelName.isBlank()) {
                                Text(stringResource(R.string.llm_custom_model_option))
                            }
                        },
                        trailingIcon = {
                            IconButton(onClick = { expandedModel = true }) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.llm_select_model)
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable { expandedModel = true }
                    )
                    
                    DropdownMenu(
                        expanded = expandedModel,
                        onDismissRequest = { expandedModel = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        modelsByProvider[selectedProvider]?.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = model,
                                        fontWeight = if (model == selectedModel && !isCustomModel) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    selectedModel = model
                                    isCustomModel = false
                                    expandedModel = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.llm_custom_model_option),
                                    fontWeight = if (isCustomModel) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                isCustomModel = true
                                expandedModel = false
                            }
                        )
                    }
                }
                
                AnimatedVisibility(
                    visible = isCustomModel,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customModelName,
                            onValueChange = { customModelName = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.llm_custom_model_hint)) },
                            label = { Text(stringResource(R.string.llm_custom_model)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Authentication Section
            Column {
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
                
                Text(
                    text = stringResource(R.string.llm_api_key),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.llm_api_key_hint)) },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.Warning else Icons.Default.Lock,
                                    contentDescription = if (showApiKey) stringResource(R.string.llm_hide) else stringResource(R.string.llm_show)
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.getText()?.text?.let { text ->
                                        apiKey = text
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = stringResource(R.string.llm_paste)
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
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.llm_api_key_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
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
                                    onValueChange = { temperature = it },
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
                                    onValueChange = { maxTokens = it.toInt() },
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
            
            // Test Connection Result
            if (uiState.testConnectionResult != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.testConnectionResult!!.startsWith("✓"))
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
                            imageVector = if (uiState.testConnectionResult!!.startsWith("✓"))
                                Icons.Default.Check
                            else
                                Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (uiState.testConnectionResult!!.startsWith("✓"))
                                Color(0xFF4CAF50)
                            else
                                Color(0xFFE57373)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = uiState.testConnectionResult!!,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
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
                            baseUrl = LlmClientFactory.getDefaultBaseUrl(selectedProvider),
                        modelId = modelId,
                        isDefault = uiState.selectedConfig?.isDefault ?: false,
                        maxTokens = maxTokens,
                        temperature = temperature
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
                        baseUrl = LlmClientFactory.getDefaultBaseUrl(selectedProvider),
                        modelId = modelId,
                        isDefault = true,
                        maxTokens = maxTokens,
                        temperature = temperature
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
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.llm_saved_configs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                uiState.configs.forEach { config ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = config.provider,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = config.modelId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Row {
                                if (config.isDefault) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.default_label),
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                TextButton(
                                    onClick = { onDeleteConfig(config) }
                                ) {
                                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
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
