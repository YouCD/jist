package dev.rcht.jist.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.rcht.jist.data.db.entity.LlmConfigEntity
import dev.rcht.jist.ui.settings.LlmConfigUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: LlmConfigUiState = LlmConfigUiState(),
    onSaveConfig: (LlmConfigEntity) -> Unit = {},
    onDeleteConfig: (LlmConfigEntity) -> Unit = {},
    onTestConnection: (LlmConfigEntity) -> Unit = {},
    onClearTestResult: () -> Unit = {},
    onSetDefault: (LlmConfigEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add LLM Config")
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "LLM Configurations",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(uiState.configs.size) { index ->
                    val config = uiState.configs[index]
                    LlmConfigCard(
                        config = config,
                        isDefault = config.isDefault,
                        onDelete = { onDeleteConfig(config) },
                        onTest = { onTestConnection(config) },
                        onSetDefault = { onSetDefault(config) },
                        isTestLoading = uiState.testConnectionLoading,
                        testResult = uiState.testConnectionResult,
                        onClearTestResult = { onClearTestResult() }
                    )
                }

                item {
                    if (uiState.configs.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No LLM configurations",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Tap + to add one",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.error != null) {
            LaunchedEffect(uiState.error) {
                snackbarHostState.showSnackbar(uiState.error!!)
            }
        }
    }

    if (showAddDialog) {
        AddConfigDialog(
            providers = uiState.providers,
            onDismiss = { showAddDialog = false },
            onSave = { config ->
                onSaveConfig(config)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun LlmConfigCard(
    config: LlmConfigEntity,
    isDefault: Boolean,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    onSetDefault: () -> Unit,
    isTestLoading: Boolean = false,
    testResult: String? = null,
    onClearTestResult: () -> Unit = {}
) {
    var showTestResult by remember { mutableStateOf(false) }
    
    if (testResult != null && showTestResult) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showTestResult = false
                onClearTestResult()
            },
            title = { Text("Connection Test Result") },
            text = { Text(testResult) },
            confirmButton = {
                Button(
                    onClick = {
                        showTestResult = false
                        onClearTestResult()
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${config.provider} - ${config.modelId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isDefault) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Default",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        onTest()
                        showTestResult = true
                    },
                    enabled = !isTestLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isTestLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isTestLoading) "Testing..." else "Test")
                }
                TextButton(
                    onClick = onSetDefault,
                    enabled = !isDefault,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Set Default")
                }
                IconButton(onClick = onDelete, modifier = Modifier.weight(0.3f)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AddConfigDialog(
    providers: List<String>,
    onDismiss: () -> Unit,
    onSave: (LlmConfigEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf(providers.firstOrNull() ?: "OPENAI") }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var expandedProvider by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add LLM Configuration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedProvider,
                        onValueChange = {},
                        label = { Text("Provider") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true
                    )
                    // Invisible clickable overlay to open dropdown
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable(enabled = true) { expandedProvider = !expandedProvider }
                    )
                    DropdownMenu(
                        expanded = expandedProvider,
                        onDismissRequest = { expandedProvider = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        providers.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider) },
                                onClick = {
                                    selectedProvider = provider
                                    expandedProvider = false
                                    baseUrl = "" // Reset when provider changes
                                    modelId = ""
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Model ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && apiKey.isNotBlank() && modelId.isNotBlank()) {
                        val config = LlmConfigEntity(
                            name = name,
                            provider = selectedProvider,
                            apiKey = apiKey,
                            baseUrl = baseUrl.ifBlank { 
                                dev.rcht.jist.llm.LlmClientFactory.getDefaultBaseUrl(selectedProvider) 
                            },
                            modelId = modelId,
                            isDefault = false,
                            maxTokens = 1000,
                            temperature = 0.7f
                        )
                        onSave(config)
                    }
                },
                enabled = name.isNotBlank() && apiKey.isNotBlank() && modelId.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
