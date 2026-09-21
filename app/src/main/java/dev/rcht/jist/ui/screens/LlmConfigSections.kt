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
import androidx.compose.animation.ExperimentalAnimationApi

@OptIn(ExperimentalAnimationApi::class)


@Composable
internal fun LlmProviderCardsRow(
    selectedProvider: String,
    onSelectProvider: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val providers = listOf("OPENAI", "ANTHROPIC")
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
                    .clickable { onSelectProvider(provider) },
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

@Composable
internal fun LlmModelSelectionSection(
    selectedModel: String,
    isCustomModel: Boolean,
    customModelName: String,
    onCustomModelNameChange: (String) -> Unit,
    expandedModel: Boolean,
    onExpandedModelChange: (Boolean) -> Unit,
    fetchedModels: List<String>,
    onModelSelected: (String) -> Unit,
    onCustomModelToggle: () -> Unit,
    onFetchModels: () -> Unit,
    modelsLoading: Boolean,
    modelsError: String?,
    apiKey: String,
    baseUrl: String,
    selectedProvider: String
) {
    Column {
        Text(
            text = stringResource(R.string.llm_model_version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Model input with fetch button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
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
                        IconButton(onClick = { onExpandedModelChange(true) }) {
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
                        .clickable { onExpandedModelChange(true) }
                )
                
                DropdownMenu(
                    expanded = expandedModel,
                    onDismissRequest = { onExpandedModelChange(false) },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    fetchedModels.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = model,
                                    fontWeight = if (model == selectedModel && !isCustomModel) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onModelSelected(model)
                                onExpandedModelChange(false)
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
                            onCustomModelToggle()
                            onExpandedModelChange(false)
                        }
                    )
                }
            }
            
            Button(
                onClick = onFetchModels,
                modifier = Modifier.height(56.dp),
                enabled = !modelsLoading && apiKey.isNotBlank() && (baseUrl.isNotBlank() || selectedProvider in listOf("OPENAI", "ANTHROPIC")),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (modelsLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Fetch")
                }
            }
        }
        
        // Models error message
        if (modelsError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = modelsError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
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
                    onValueChange = { onCustomModelNameChange(it) },
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
}

@Composable
internal fun LlmAuthenticationSection(
    apiKey: String,
    onApiKeyChange: (String) -> Unit
) {
    var showApiKey by remember { mutableStateOf(false) }

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
            onValueChange = { onApiKeyChange(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.llm_api_key_hint)) },
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showApiKey = !showApiKey }) {
                    Icon(
                        imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showApiKey) stringResource(R.string.llm_hide) else stringResource(R.string.llm_show)
                    )
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
}

@Composable
internal fun LlmBaseUrlField(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.llm_base_url),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { onBaseUrlChange(it) },
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
    }
}
