package dev.rcht.jist.ui.screens

import dev.rcht.jist.R
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import dev.rcht.jist.JistApplication
import dev.rcht.jist.data.config.ConfigManager
import dev.rcht.jist.ui.settings.SettingsViewModel
import dev.rcht.jist.ui.components.GlassScaffold
import dev.rcht.jist.ui.components.JistSnackbarHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
                return SettingsViewModel(context, app.preferencesRepository, app.appRuleRepository, app.llmConfigRepository, app) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val copyText: (String, String) -> Unit = { text, msg ->
        clipboardManager.setText(AnnotatedString(text))
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }
    val configManager = remember {
        ConfigManager(context, app.preferencesRepository, app.llmConfigRepository, app.appRuleRepository, app.customPromptRepository, app.watchTopicRepository, app.chatSourceRepository, app.watchedChatRepository)
    }

    var showStyleDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

    val exportOk = stringResource(R.string.settings_config_exported)
    val exportFail = stringResource(R.string.settings_config_export_failed)
    val importOk = stringResource(R.string.settings_config_imported)
    val importFail = stringResource(R.string.settings_config_import_failed, "")

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                configManager.exportToUri(uri).fold(
                    onSuccess = { snackbarHostState.showSnackbar(exportOk) },
                    onFailure = { snackbarHostState.showSnackbar(exportFail) }
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = configManager.importFromUri(uri)
                result.fold(
                    onSuccess = {
                        snackbarHostState.showSnackbar(importOk)
                    },
                    onFailure = { e ->
                        snackbarHostState.showSnackbar(importFail + e.message)
                    }
                )
            }
        }
    }

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
        snackbarHost = { JistSnackbarHost(hostState = snackbarHostState) },
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

            // Data Management Section
            item {
                SettingsSection(title = stringResource(R.string.settings_data_management)) {
                    SettingsItem(
                        icon = Icons.Default.Share,
                        title = stringResource(R.string.settings_export_config),
                            onClick = {
                                val ts = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
                                exportLauncher.launch("jist-config-$ts.json")
                            }
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f))
                    SettingsItem(
                        icon = Icons.Default.Add,
                        title = stringResource(R.string.settings_import_config),
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                    )
                }
            }

            // MCP Section
            item {
                SettingsSection(title = "MCP 服务（外部 Agent 接入）") {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Lan,
                        title = "启用 MCP 服务（只读查询）",
                        checked = uiState.mcpEnabled,
                        onCheckedChange = { viewModel.setMcpEnabled(it) }
                    )
                    if (uiState.mcpEnabled) {
                        if (uiState.mcpError != null) {
                            Text(
                                text = "⚠️ ${uiState.mcpError}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        } else if (uiState.mcpRunning) {
                            Text(
                                text = "运行中：http://${if (uiState.mcpAllowLan) "0.0.0.0" else "127.0.0.1"}:${uiState.mcpPort}/mcp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f))
                        SettingsItem(
                            icon = Icons.Outlined.Key,
                            title = "认证 Token",
                            value = if (uiState.mcpToken.length > 12) uiState.mcpToken.take(8) + "…" else uiState.mcpToken,
                            trailingIcon = Icons.Outlined.ContentCopy,
                            onClick = { copyText(uiState.mcpToken, "Token 已复制") }
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Sync,
                            title = "重新生成 Token",
                            showChevron = false,
                            trailingIcon = Icons.Outlined.ContentCopy,
                            onClick = { viewModel.regenerateMcpToken { copyText(it, "已生成新 Token") } }
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Numbers,
                            title = "端口",
                            value = uiState.mcpPort.toString(),
                            showChevron = false,
                            onClick = { showPortDialog = true }
                        )
                        SettingsSwitchItem(
                            icon = Icons.Outlined.Lan,
                            title = "允许局域网访问（0.0.0.0）",
                            checked = uiState.mcpAllowLan,
                            onCheckedChange = { viewModel.setMcpAllowLan(it) }
                        )

                        SettingsItem(
                            icon = Icons.Outlined.DataObject,
                            title = "客户端配置（MCP client JSON）",
                            trailingIcon = Icons.Outlined.ContentCopy,
                            onClick = {
                                val host = if (uiState.mcpAllowLan) "<手机IP>" else "127.0.0.1"
                                val json = """
                                {
                                  "mcpServers": {
                                    "jist": {
                                      "url": "http://$host:${uiState.mcpPort}/mcp",
                                      "headers": {
                                        "Authorization": "Bearer ${uiState.mcpToken}"
                                      }
                                    }
                                  }
                                }
                                """.trimIndent()
                                copyText(json, "配置已复制")
                            }
                        )
                    }
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

    if (showPortDialog) {
        McpPortDialog(
            currentPort = uiState.mcpPort,
            onConfirm = { port ->
                showPortDialog = false
                viewModel.setMcpPort(port)
            },
            onDismiss = { showPortDialog = false }
        )
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
