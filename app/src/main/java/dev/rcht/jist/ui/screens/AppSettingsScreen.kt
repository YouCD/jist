package dev.rcht.jist.ui.screens

import dev.rcht.jist.R
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.rcht.jist.JistApplication
import dev.rcht.jist.data.db.entity.AppRuleEntity
import dev.rcht.jist.ui.settings.AppSettingsViewModel
import dev.rcht.jist.ui.components.GlassScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onDone: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as JistApplication
    
    val viewModel: AppSettingsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AppSettingsViewModel(
                    context,
                    app.appRuleRepository,
                    app.notificationRepository
                ) as T
            }
        }
    )
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GlassScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_settings_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                     IconButton(onClick = onNavigateBack) {
                         Icon(
                             imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                              contentDescription = stringResource(R.string.nav_back)
                         )
                     }
                },
                actions = {
                    TextButton(onClick = { 
                        onDone?.invoke() ?: onNavigateBack()
                    }) {
                        Text(stringResource(R.string.done), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
        // FAB Removed
    ) { paddingValues ->
        if (uiState.isLoading) {
             Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                 CircularProgressIndicator()
             }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                // Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    placeholder = { Text(stringResource(R.string.app_settings_search), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                     trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                // Stats Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${uiState.allAppsCount} APPS INSTALLED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { viewModel.toggleAll(true) }) {
                         Text(stringResource(R.string.app_settings_select_all), fontSize = 12.sp)
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Suggested Section
                    if (uiState.suggestedApps.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.app_settings_suggested),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f))
                            ) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    uiState.suggestedApps.forEachIndexed { index, app ->
                                        AppItem(
                                            app = app,
                                            onToggle = { viewModel.toggleAppEnabled(app) },
                                            onUpdateCustomPrompt = { viewModel.updateCustomPrompt(app, it) }
                                        )
                                        if (index < uiState.suggestedApps.size - 1) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // All Other Apps (No grouping)
                    if (uiState.otherApps.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.app_settings_all_apps),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f)
                                )
                                TextButton(onClick = { viewModel.disableAllNonSuggested() }) {
                                    Text(stringResource(R.string.app_settings_disable_all), fontSize = 12.sp)
                                }
                            }
                        }
                        
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f))
                            ) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    uiState.otherApps.forEachIndexed { index, app ->
                                        AppItem(
                                            app = app,
                                            onToggle = { viewModel.toggleAppEnabled(app) },
                                            onUpdateCustomPrompt = { viewModel.updateCustomPrompt(app, it) }
                                        )
                                        if (index < uiState.otherApps.size - 1) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(60.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppItem(app: AppRuleEntity, onToggle: (Boolean) -> Unit, onUpdateCustomPrompt: (String) -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }
    var promptText by remember(app.id, app.customPrompt) { mutableStateOf(app.customPrompt ?: dev.rcht.jist.llm.getDefaultSystemPrompt()) }
    val hasPrompt = app.customPrompt != null && app.customPrompt.isNotBlank()
    val isDefault = promptText == dev.rcht.jist.llm.getDefaultSystemPrompt()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = app.packageName,
                appName = app.appName,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    text = if (app.enabled) stringResource(R.string.active) else stringResource(R.string.disabled), 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (app.enabled) {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.Edit,
                        contentDescription = if (expanded) "Collapse" else "Custom prompt",
                        tint = if (hasPrompt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Switch(
                checked = app.enabled,
                onCheckedChange = { enabled ->
                    if (!enabled) expanded = false
                    onToggle(enabled)
                }
            )
        }
        
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                Text(
                    text = "Custom Prompt",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                if (isDefault) {
                    Text(
                        text = "Using default prompt — edit below to customize:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom Prompt") },
                    minLines = 2,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!isDefault) {
                        TextButton(onClick = {
                            promptText = dev.rcht.jist.llm.getDefaultSystemPrompt()
                        }) {
                            Text("Reset to default", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Button(
                        onClick = { onUpdateCustomPrompt(promptText) },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun AppIcon(packageName: String, appName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = drawable.toBitmap()
                iconBitmap = bitmap.asImageBitmap()
            } catch (e: Exception) {
                // Ignore, keep null
            }
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        // Fallback: Colored Box + First Letter
        Box(
            modifier = modifier
                .background(
                    if (packageName.contains("whatsapp")) Color(0xFF25D366) 
                    else if (packageName.contains("slack")) Color(0xFF4A154B)
                    else if (packageName.contains("discord")) Color(0xFF5865F2)
                    else MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
             Text(
                 text = appName.take(1).uppercase(),
                 color = MaterialTheme.colorScheme.onPrimaryContainer,
                 fontWeight = FontWeight.Bold
             )
        }
    }
}
