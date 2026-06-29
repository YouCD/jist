package dev.rcht.jist.ui.screens

import dev.rcht.jist.R
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.rcht.jist.JistApplication
import dev.rcht.jist.ui.settings.SettingsViewModel
import dev.rcht.jist.ui.components.GlassScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    startDestination: String? = null, // For deep linking if needed
    onNavigateBack: () -> Unit = {},
    onNavigateToLlmConfig: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as JistApplication
    val viewModel: SettingsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(context, app.preferencesRepository, app.appRuleRepository, app.llmConfigRepository) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()

    var showStyleDialog by remember { mutableStateOf(false) }

    // Refresh notification permission state when returning to this screen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    GlassScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.SemiBold) },
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
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Intelligence Section
            item {
                SettingsSection(title = stringResource(R.string.settings_intelligence)) {
                    SettingsItem(
                        icon = Icons.Outlined.SmartToy, // or similar
                        title = stringResource(R.string.settings_llm_model),
                        value = uiState.llmModelName,
                        onClick = onNavigateToLlmConfig
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f))
                    SettingsItem(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.settings_summarization_style),
                        value = uiState.summarizationStyle,
                        onClick = { showStyleDialog = !showStyleDialog }
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_content_sources)) {
                    SettingsItem(
                        icon = Icons.Outlined.Apps,
                        title = stringResource(R.string.app_settings_title),
                        value = stringResource(R.string.settings_apps_active, uiState.activeAppCount),
                        onClick = onNavigateToApps
                    )
                }
            }

            // Behavior Section
            item {
                SettingsSection(title = stringResource(R.string.settings_behavior)) {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.settings_push_notifications),
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.toggleNotifications(enabled) {
                                // This callback is only invoked when system permission is required
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f))
                    SettingsItem(
                        icon = Icons.Outlined.Schedule,
                        title = stringResource(R.string.settings_daily_digest),
                        // value = uiState.dailyDigestTime,
                        value = stringResource(R.string.coming_soon),
                        onClick = { /* Time picker placeholder */ }
                    )
                    // Haptic Feedback omitted as requested
                }
            }

            // About Section
            item {
                SettingsSection(title = stringResource(R.string.settings_about_section)) {
                    SettingsItem(
                        title = stringResource(R.string.settings_help_support),
                        trailingIcon = Icons.Outlined.OpenInNew,
                        onClick = { /* Open URL */ }
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f))
                    SettingsItem(
                        title = stringResource(R.string.settings_privacy_policy),
                         trailingIcon = Icons.Outlined.OpenInNew,
                        onClick = { /* Open URL */ }
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f))
                    SettingsItem(
                        title = stringResource(R.string.settings_version),
                        value = uiState.version,
                        showChevron = false,
                        onClick = {}
                    )
                }
            }

            // Footer
            item {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = stringResource(R.string.settings_jist_2026),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                 Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showStyleDialog) {
        SummarizationStyleDialog(
            currentTone = uiState.summaryTone,
            currentLength = uiState.summaryLength,
            onSelectTone = { tone -> viewModel.setSummaryTone(tone) },
            onSelectLength = { length -> viewModel.setSummaryLength(length) },
            onDismiss = { showStyleDialog = false }
        )
    }
}

@Composable
fun SummarizationStyleDialog(
    currentTone: String,
    currentLength: String,
    onSelectTone: (String) -> Unit,
    onSelectLength: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedTone by remember { mutableStateOf(currentTone) }
    var selectedLength by remember { mutableStateOf(currentLength.ifBlank { "MEDIUM" }) }

    val isZh = java.util.Locale.getDefault().language == "zh"

    val tones = listOf(
        "PROFESSIONAL" to if (isZh) "专业" else "Professional",
        "CASUAL" to if (isZh) "随意" else "Casual",
        "WITTY" to if (isZh) "幽默" else "Witty",
        "URGENT" to if (isZh) "紧急" else "Urgent"
    )
    val builder = remember { dev.rcht.jist.llm.PromptBuilder() }
    val toneDesc = mapOf(
        "PROFESSIONAL" to if (isZh) "正式、中立" else "Formal & neutral",
        "CASUAL" to if (isZh) "口语化、轻松" else "Conversational",
        "WITTY" to if (isZh) "轻松幽默" else "Light-hearted",
        "URGENT" to if (isZh) "突出紧急事项" else "Action-oriented"
    )
    val lengths = listOf(
        "SHORT" to if (isZh) "短" else "Short",
        "MEDIUM" to if (isZh) "中" else "Medium",
        "LONG" to if (isZh) "长" else "Long"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isZh) "摘要风格" else "Summarization Style") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isZh) "语气" else "Tone",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                tones.forEach { (value, label) ->
                    val isSelected = selectedTone == value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTone = value }
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { selectedTone = value })
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = builder.toneInstruction(value),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isSelected) 0.8f else 0.5f)
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = if (isZh) "长度" else "Length",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    lengths.forEach { (value, label) ->
                        val sub = when (value) {
                            "SHORT" -> if (isZh) "50字以内" else "50 words"
                            "MEDIUM" -> if (isZh) "150字以内" else "150 words"
                            "LONG" -> if (isZh) "300字以内" else "300 words"
                            else -> ""
                        }
                        FilterChip(
                            selected = selectedLength == value,
                            onClick = { selectedLength = value },
                            label = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = label, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(text = sub, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSelectTone(selectedTone)
                onSelectLength(selectedLength)
                onDismiss()
            }) {
                Text(if (isZh) "保存" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isZh) "取消" else "Cancel")
            }
        }
    )
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector? = null,
    title: String,
    value: String? = null,
    showChevron: Boolean = true,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
             Spacer(modifier = Modifier.width(16.dp))
        }
       
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        
        if (trailingIcon != null) {
             Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
         Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
