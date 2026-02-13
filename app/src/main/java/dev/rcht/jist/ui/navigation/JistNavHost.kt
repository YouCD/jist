package dev.rcht.jist.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.rcht.jist.JistApplication
import dev.rcht.jist.ui.dashboard.DashboardViewModel
import dev.rcht.jist.ui.screens.AboutScreen
import dev.rcht.jist.ui.screens.AppSettingsScreen
import dev.rcht.jist.ui.screens.DashboardScreen
import dev.rcht.jist.ui.screens.NotificationLogScreen
import dev.rcht.jist.ui.screens.OnboardingScreen
import dev.rcht.jist.ui.screens.LlmConfigScreen
import dev.rcht.jist.ui.screens.SettingsScreen
import dev.rcht.jist.ui.screens.SummariesScreen
import dev.rcht.jist.ui.screens.SummaryDetailScreen
import dev.rcht.jist.ui.settings.SettingsViewModel
import dev.rcht.jist.ui.summaries.SummariesViewModel
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import android.util.Log

@Composable
fun JistNavHost(
    navController: NavHostController,
    application: Application,
    modifier: Modifier = Modifier
) {
    val jistApp = application as JistApplication
    val context = LocalContext.current
    
    // Collect onboarding status - no initial value, wait for actual state
    val preferences by jistApp.preferencesRepository.preferencesFlow.collectAsState(
        initial = null
    )
    
    // Wait for preferences to be loaded
    if (preferences == null) {
        // Show loading screen while preferences load
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }

    // Also load existing app rules - if there are no rules, show onboarding even if preferences say complete
    val NAV_TAG = "JistNavHost"
    val appRules by produceState<List<dev.rcht.jist.data.db.entity.AppRuleEntity>?>(initialValue = null) {
        value = try {
            // load all rules from DB (suspend)
            jistApp.appRuleRepository.getAll()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Wait for app rules to be loaded
    if (appRules == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }

    val appRulesList = appRules!!

    // Debug logging for onboarding visibility
    Log.d(NAV_TAG, "preferences.isOnboardingComplete=${preferences!!.isOnboardingComplete}")
    Log.d(NAV_TAG, "appRulesCount=${appRulesList.size} sample=${appRulesList.take(5).joinToString(",") { it.packageName }}")

    // Determine whether to start onboarding:
    // - If onboarding not completed in preferences, show onboarding
    // - OR if there are no app rules yet (fresh install or DB cleared), show onboarding to set defaults
    val shouldShowOnboarding = !preferences!!.isOnboardingComplete || appRulesList.isEmpty()
    val startDestination = if (shouldShowOnboarding) {
        Screen.Onboarding.route
    } else {
        Screen.Dashboard.route
    }
    
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onOpenSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Dashboard.route) {
            val viewModel: DashboardViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return DashboardViewModel(
                            jistApp.notificationRepository,
                            jistApp.summaryRepository,
                            jistApp.summaryEngine,
                            dev.rcht.jist.notification.SummaryNotificationManager(context)
                        ) as T
                    }
                }
            )
            val uiState by viewModel.uiState.collectAsState()
            DashboardScreen(
                uiState = uiState,
                onSummarizeNow = { viewModel.summarizeNow() },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Summaries.route) {
            val viewModel: SummariesViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return SummariesViewModel(
                            jistApp.summaryRepository
                        ) as T
                    }
                }
            )
            val uiState by viewModel.uiState.collectAsState()
            SummariesScreen(
                uiState = uiState,
                onSearchChange = { query -> viewModel.searchSummaries(query) },
                onAppFilterChange = { app -> viewModel.filterByApp(app) },
                onSummaryClick = { summaryId ->
                    navController.navigate("summary_detail/$summaryId")
                }
            )
        }
        composable("summary_detail/{summaryId}") { backStackEntry ->
            val summaryIdStr = backStackEntry.arguments?.getString("summaryId") ?: return@composable
            val summaryId = summaryIdStr.toLongOrNull() ?: return@composable
            
            val viewModel: dev.rcht.jist.ui.summarydetail.SummaryDetailViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return dev.rcht.jist.ui.summarydetail.SummaryDetailViewModel(
                            jistApp.summaryRepository,
                            jistApp.notificationRepository,
                            jistApp.summaryEngine
                        ) as T
                    }
                }
            )
            
            androidx.compose.runtime.LaunchedEffect(summaryId) {
                viewModel.loadSummary(summaryId)
            }
            
            val uiState by viewModel.uiState.collectAsState()
            val summary = uiState.summary
            
            if (summary != null) {
                SummaryDetailScreen(
                    summaryText = summary.summaryText,
                    appName = summary.appName,
                    contactOrGroup = summary.contactOrGroup,
                    messageCount = summary.messageCount,
                    createdAt = summary.createdAt,
                    notifications = uiState.notifications,
                    onNavigateBack = { navController.popBackStack() },
                    onReSummarize = { viewModel.reSummarize() }
                )
            }
        }
        composable(Screen.Settings.route) {
            val viewModel: dev.rcht.jist.ui.settings.SettingsViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return dev.rcht.jist.ui.settings.SettingsViewModel(
                            context,
                            jistApp.preferencesRepository
                        ) as T
                    }
                }
            )
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLlmConfig = { navController.navigate(Screen.LlmConfig.route) },
                onNavigateToApps = { navController.navigate(Screen.AppSettings.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onSignOut = { /* Handle sign out */ }
            )
        }
        composable(Screen.LlmConfig.route) {
            val viewModel: dev.rcht.jist.ui.settings.LlmConfigViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return dev.rcht.jist.ui.settings.LlmConfigViewModel(
                            jistApp.llmConfigRepository,
                            jistApp.httpClient
                        ) as T
                    }
                }
            )
            val uiState by viewModel.uiState.collectAsState()
            dev.rcht.jist.ui.screens.LlmConfigScreen(
                uiState = uiState,
                onSaveConfig = { config -> viewModel.saveConfig(config) },
                onDeleteConfig = { config -> viewModel.deleteConfig(config) },
                onTestConnection = { config -> viewModel.testConnection(config) },
                onClearTestResult = { viewModel.clearTestResult() },
                onSetDefault = { config -> viewModel.setDefaultConfig(config) },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAppSettings = { navController.navigate(Screen.AppSettings.route) },
                onRunOnboarding = { navController.navigate(Screen.Onboarding.route) }
            )
        }
        composable(Screen.AppSettings.route) {
            AppSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.NotificationLog.route) {
            NotificationLogScreen()
        }
        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
