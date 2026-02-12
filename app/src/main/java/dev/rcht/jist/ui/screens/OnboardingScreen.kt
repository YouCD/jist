package dev.rcht.jist.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.rcht.jist.JistApplication

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
    val stepsCount = 6

    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    var listenerEnabled by remember { mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(pkg)) }
    var batteryIgnored by remember { mutableStateOf(try {
        val pm = context.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(pkg) ?: false
    } catch (_: Exception) { false }) }

    var llmConfigured by remember { mutableStateOf(false) }

    fun refreshStatuses() {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(context).contains(pkg)
        batteryIgnored = try {
            val pm = context.getSystemService(PowerManager::class.java)
            pm?.isIgnoringBatteryOptimizations(pkg) ?: false
        } catch (_: Exception) { false }
        scope.launch {
            llmConfigured = try {
                (context.applicationContext as JistApplication).llmConfigRepository.getAll().any { it.apiKey.isNotBlank() }
            } catch (e: Exception) {
                false
            }
        }
    }

    // Poll automatically while on a step so 'Next' enables without user clicking 'Check Status'
    LaunchedEffect(step) {
        while (true) {
            refreshStatuses()
            delay(1500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Step ${step + 1} of $stepsCount", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))

        Column(modifier = Modifier.fillMaxWidth(0.85f), horizontalAlignment = Alignment.CenterHorizontally) {
            when (step) {
                0 -> {
                    Text(
                        text = "Welcome to Jist",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = "Jist summarizes messages across your apps. This quick setup will guide you through the required permissions and configuration.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Get Started")
                    }
                }

                1 -> {
                    Text(text = "Allow notifications", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                    Text(text = "Jist needs to post and read notifications to summarize messages.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 16.dp))

                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, pkg) }
                            context.startActivity(intent)
                        }
                    }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Request Notification Permission" else "Open Notification Settings")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = { if (step > 0) step-- }, modifier = Modifier.width(140.dp).height(48.dp)) { Text("Back") }
                        Button(onClick = { step++ }, enabled = notificationsEnabled, modifier = Modifier.width(140.dp).height(48.dp)) { Text("Next") }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(onClick = { step++ }, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text("Skip this step") }
                }

                2 -> {
                    Text(text = "Enable notification access", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                    Text(text = "To capture incoming messages for summarization, enable Notification Access for Jist.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 16.dp))

                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Open Notification Access") }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = { if (step > 0) step-- }, modifier = Modifier.width(140.dp).height(48.dp)) { Text("Back") }
                        Button(onClick = { step++ }, enabled = listenerEnabled, modifier = Modifier.width(140.dp).height(48.dp)) { Text("Next") }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(onClick = { step++ }, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text("Skip this step") }
                }

                3 -> {
                    Text(text = "Allow background activity", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                    Text(text = "Whitelist Jist from battery optimizations so it can run reliably.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 16.dp))

                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:$pkg") }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Request Battery Whitelist") }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = { if (step > 0) step-- }, modifier = Modifier.width(140.dp).height(48.dp)) { Text("Back") }
                        Button(onClick = { step++ }, enabled = batteryIgnored, modifier = Modifier.width(140.dp).height(48.dp)) { Text("Next") }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(onClick = { step++ }, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text("Skip this step") }
                }

                4 -> {
                    Text(text = "Configure AI model (LLM)", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                    Text(text = "Add your LLM API key to enable summaries.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 16.dp))

                    Button(onClick = { onOpenSettings() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Open LLM Settings") }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = { if (step > 0) step-- }, modifier = Modifier.width(140.dp).height(48.dp)) { Text("Back") }
                        Button(onClick = { step++ }, enabled = llmConfigured, modifier = Modifier.width(140.dp).height(48.dp)) { Text("Next") }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(onClick = { step++ }, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text("Skip this step") }
                }

                5 -> {
                    Text(text = "Enable messaging apps & finish", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                    Text(text = "This will enable summarization for detected messaging & email apps.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 16.dp))

                    Button(onClick = {
                        viewModel.startSetup()
                        scope.launch {
                            delay(500)
                            refreshStatuses()
                        }
                    }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Start Setup (Enable messaging apps)") }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = { if (step > 0) step-- }, modifier = Modifier.width(140.dp).height(48.dp)) { Text("Back") }
                        Button(onClick = {
                            scope.launch {
                                viewModel.finishOnboarding()
                                onOnboardingComplete()
                            }
                        }, modifier = Modifier.width(140.dp).height(48.dp)) { Text("Finish") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextActionRight(text: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(onClick = onClick) { Text(text) }
    }
}
