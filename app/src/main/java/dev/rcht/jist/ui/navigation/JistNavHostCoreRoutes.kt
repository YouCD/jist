package dev.rcht.jist.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.rcht.jist.R
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
import dev.rcht.jist.ui.screens.WatchListScreen
import dev.rcht.jist.ui.screens.WatchEditScreen
import dev.rcht.jist.ui.screens.WatchDetailScreen
import dev.rcht.jist.ui.settings.SettingsViewModel
import dev.rcht.jist.ui.summaries.SummariesViewModel
import dev.rcht.jist.ui.watchlist.WatchListViewModel
import dev.rcht.jist.ui.watchedit.WatchEditViewModel
import dev.rcht.jist.ui.watchdetail.WatchDetailViewModel
import dev.rcht.jist.ui.screens.XposedChatsScreen
import dev.rcht.jist.ui.screens.XposedChatDetailScreen
import dev.rcht.jist.ui.xposedchats.XposedChatsViewModel
import dev.rcht.jist.ui.xposedchats.XposedChatDetailViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import android.util.Log
import androidx.navigation.NavGraphBuilder

fun NavGraphBuilder.onboardingRoute(
    navController: NavHostController,
    jistApp: JistApplication
) {
    composable(Screen.Onboarding.route) {
        OnboardingScreen(
            onOnboardingComplete = {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            },
            onOpenAppSettings = { navController.navigate(Screen.AppSettings.createRoute(fromOnboarding = true)) }
        )
    }
}

fun NavGraphBuilder.dashboardRoute(
    navController: NavHostController,
    jistApp: JistApplication
) {
    composable(Screen.Dashboard.route) {
        val context = LocalContext.current
        val viewModel: DashboardViewModel = viewModel(
            factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return DashboardViewModel(
                        context,
                        jistApp.notificationRepository,
                        jistApp.summaryRepository,
                        jistApp.watchTopicRepository,
                        jistApp.watchCollectedItemRepository,
                        jistApp.summaryEngine,
                        dev.rcht.jist.notification.SummaryNotificationManager(context)
                    ) as T
                }
            }
        )
        val uiState by viewModel.uiState.collectAsState()
            val hasNotificationListenerPermission = remember { mutableStateOf(dev.rcht.jist.util.PermissionHelper.hasNotificationListenerPermission(context)) }
            val isBatteryOptimizationDisabled = remember { mutableStateOf(dev.rcht.jist.util.BatteryOptimizationHelper.isBatteryOptimizationDisabled(context)) }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshData()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            DashboardScreen(
                uiState = uiState,
                onSummarizeNow = { viewModel.summarizeNow() },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onViewAllClick = {
                    navController.navigate(Screen.Summaries.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onSummaryClick = { summaryId ->
                    navController.navigate("summary_detail/$summaryId")
                },
                onWatchTopicClick = { topicId ->
                    navController.navigate("watch_detail/$topicId")
                },
                onWatchCreateClick = {
                    navController.navigate(Screen.WatchEdit.route)
                },
                onWatchListClick = {
                    navController.navigate(Screen.WatchList.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                hasNotificationListenerPermission = hasNotificationListenerPermission.value,
                isBatteryOptimizationDisabled = isBatteryOptimizationDisabled.value
            )
    }
}

fun NavGraphBuilder.summariesRoute(
    navController: NavHostController,
    jistApp: JistApplication
) {
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
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refreshSummaries()
            }
        }
        val uiState by viewModel.uiState.collectAsState()
        SummariesScreen(
            uiState = uiState,
            onSearchChange = { query -> viewModel.searchSummaries(query) },
            onAppFilterChange = { app -> viewModel.filterByApp(app) },
            onSummaryClick = { summaryId ->
                navController.navigate("summary_detail/$summaryId")
            },
            onDeleteSummaries = { ids -> viewModel.deleteSummaries(ids) },
            onRefresh = { viewModel.refreshSummaries() },
            isRefreshing = uiState.isRefreshing,
            onLoadMore = { viewModel.loadMoreSummaries() },
            isLoadingMore = uiState.isLoadingMore,
            hasMore = uiState.hasMore
        )
    }
}

fun NavGraphBuilder.summaryDetailRoute(
    navController: NavHostController,
    jistApp: JistApplication
) {
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
                        jistApp.chatMessageRepository,
                        jistApp.summaryEngine
                    ) as T
                }
            }
        )

        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(summaryId) {
            viewModel.loadAll(summaryId)
        }

        SummaryDetailScreen(
            uiState = uiState,
            onNavigateBack = { navController.popBackStack() },
            onPageChanged = { index -> viewModel.onPageChanged(index) },
            isReSummarizing = uiState.isReSummarizing
        )
    }
}

fun NavGraphBuilder.settingsRoute(
    navController: NavHostController,
    jistApp: JistApplication
) {
    composable(Screen.Settings.route) {
            val context = LocalContext.current
        val viewModel: dev.rcht.jist.ui.settings.SettingsViewModel = viewModel(
            factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return dev.rcht.jist.ui.settings.SettingsViewModel(
                        context,
                        jistApp.preferencesRepository,
                        jistApp.appRuleRepository,
                        jistApp.llmConfigRepository,
                        jistApp
                    ) as T
                }
            }
        )
        SettingsScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToLlmConfig = { navController.navigate(Screen.LlmConfig.route) },
            onNavigateToApps = { navController.navigate(Screen.AppSettings.createRoute(fromOnboarding = false)) },
            onNavigateToAbout = { navController.navigate(Screen.About.route) }
        )
    }
}

fun NavGraphBuilder.llmConfigRoute(
    navController: NavHostController,
    jistApp: JistApplication
) {
    composable(Screen.LlmConfig.route) {
        val viewModel: dev.rcht.jist.ui.settings.LlmConfigViewModel = viewModel(
            factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return dev.rcht.jist.ui.settings.LlmConfigViewModel(
                        jistApp,
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
            onNavigateToAppSettings = { navController.navigate(Screen.AppSettings.createRoute(fromOnboarding = false)) },
            onRunOnboarding = { navController.navigate(Screen.Onboarding.route) }
        )
    }
}

fun NavGraphBuilder.appSettingsRoute(
    navController: NavHostController,
    jistApp: JistApplication
) {
    composable(
        route = Screen.AppSettings.route,
        arguments = listOf(
            navArgument("fromOnboarding") {
                type = NavType.BoolType
                defaultValue = false
            }
        )
    ) {
        AppSettingsScreen(
            onNavigateBack = { 
                navController.popBackStack()
            },
            onDone = {
                // Pop back to the previous screen (Onboarding or Settings)
                navController.popBackStack()
            }
        )
    }
}

fun NavGraphBuilder.notificationLogRoute(
    navController: NavHostController,
    jistApp: JistApplication
) {
    composable(Screen.NotificationLog.route) {
        val vm: dev.rcht.jist.ui.notificationlog.NotificationLogViewModel = viewModel(
            factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return dev.rcht.jist.ui.notificationlog.NotificationLogViewModel(
                        jistApp.notificationRepository
                    ) as T
                }
            }
        )
        val state by vm.uiState.collectAsState()
        NotificationLogScreen(
            appGroups = state.appGroups,
            isLoading = state.isLoading,
            onRefresh = { vm.refresh() },
            onDelete = { ids -> vm.deleteNotifications(ids) }
        )
    }
}

fun NavGraphBuilder.aboutRoute(
    navController: NavHostController,
    jistApp: JistApplication
) {
    composable(Screen.About.route) {
        AboutScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
}
