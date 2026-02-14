package dev.rcht.jist.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onNavigateToAbout: () -> Unit,
    onSignOut: () -> Unit // Placeholder
) {
    val context = LocalContext.current
    val app = context.applicationContext as JistApplication
    val viewModel: SettingsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(context, app.preferencesRepository, app.appRuleRepository) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()

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
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                     IconButton(onClick = onNavigateBack) {
                         Icon(
                             imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                             contentDescription = "Back"
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
            // Header Title (Large) - REMOVED


            // Profile Section
            item {
                ProfileCard(
                    name = uiState.userName,
                    email = uiState.userEmail,
                    onClick = { /* Manage Account placeholder */ }
                )
            }

            // Intelligence Section
            item {
                SettingsSection(title = "INTELLIGENCE") {
                    SettingsItem(
                        icon = Icons.Outlined.SmartToy, // or similar
                        title = "LLM Model",
                        value = uiState.llmModelName,
                        onClick = onNavigateToLlmConfig
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f))
                    SettingsItem(
                        icon = Icons.Outlined.Description,
                        title = "Summarization Style",
                        value = uiState.summarizationStyle,
                        onClick = { /* Navigate to Style settings (maybe reuse onboarding step or simple dialog) */ onNavigateToLlmConfig() }
                    )
                }
            }

            // Content Sources Section
            item {
                SettingsSection(title = "CONTENT SOURCES") {
                    SettingsItem(
                        icon = Icons.Outlined.Apps,
                        title = "App Selection",
                        value = "${uiState.activeAppCount} Active", // Logic to update this needed
                        onClick = onNavigateToApps
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f))
                    SettingsItem(
                        icon = Icons.Outlined.Block,
                        title = "Blocked Words",
                        onClick = { /* Placeholder */ }
                    )
                }
            }

            // Behavior Section
            item {
                SettingsSection(title = "BEHAVIOR") {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Notifications,
                        title = "Push Notifications",
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
                        title = "Daily Digest",
                        value = uiState.dailyDigestTime,
                        onClick = { /* Time picker placeholder */ }
                    )
                    // Haptic Feedback omitted as requested
                }
            }

            // About Section
            item {
                SettingsSection(title = "ABOUT") {
                    SettingsItem(
                        title = "Help & Support",
                        trailingIcon = Icons.Outlined.OpenInNew,
                        onClick = { /* Open URL */ }
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f))
                    SettingsItem(
                        title = "Privacy Policy",
                         trailingIcon = Icons.Outlined.OpenInNew,
                        onClick = { /* Open URL */ }
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f))
                    SettingsItem(
                        title = "Version",
                        value = uiState.version,
                        showChevron = false,
                        onClick = {}
                    )
                }
            }

            // Sign Out
            item {
                Button(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f),
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign Out", fontSize = 16.sp)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Jist Intelligence Inc. © 2024",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                 Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun ProfileCard(name: String, email: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Placeholder
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                 Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Manage Account", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
