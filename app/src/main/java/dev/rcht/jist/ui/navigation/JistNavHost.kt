package dev.rcht.jist.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.rcht.jist.JistApplication
import dev.rcht.jist.ui.dashboard.DashboardViewModel
import dev.rcht.jist.ui.screens.AboutScreen
import dev.rcht.jist.ui.screens.DashboardScreen
import dev.rcht.jist.ui.screens.NotificationLogScreen
import dev.rcht.jist.ui.screens.SettingsScreen
import dev.rcht.jist.ui.screens.SummariesScreen
import dev.rcht.jist.ui.screens.SummaryDetailScreen
import dev.rcht.jist.ui.settings.SettingsViewModel
import dev.rcht.jist.ui.summaries.SummariesViewModel
import androidx.compose.runtime.remember

@Composable
fun JistNavHost(
    navController: NavHostController,
    application: Application,
    modifier: Modifier = Modifier
) {
    val jistApp = application as JistApplication
    
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            val viewModel: DashboardViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return DashboardViewModel(
                            jistApp.notificationRepository,
                            jistApp.summaryRepository
                        ) as T
                    }
                }
            )
            val uiState by viewModel.uiState.collectAsState()
            DashboardScreen(uiState = uiState)
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
                    // Navigate to detail screen (route params can be added later with type-safe navigation)
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
            
            // Load summary when screen appears
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
            val viewModel: SettingsViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return SettingsViewModel(
                            jistApp.llmConfigRepository,
                            jistApp.httpClient
                        ) as T
                    }
                }
            )
            val uiState by viewModel.uiState.collectAsState()
            SettingsScreen(
                uiState = uiState,
                onSaveConfig = { config -> viewModel.saveConfig(config) },
                onDeleteConfig = { config -> viewModel.deleteConfig(config) },
                onTestConnection = { config -> viewModel.testConnection(config) },
                onClearTestResult = { viewModel.clearTestResult() },
                onSetDefault = { config -> viewModel.setDefaultConfig(config) }
            )
        }
        composable(Screen.NotificationLog.route) {
            NotificationLogScreen()
        }
        composable(Screen.About.route) {
            AboutScreen()
        }
    }
}
