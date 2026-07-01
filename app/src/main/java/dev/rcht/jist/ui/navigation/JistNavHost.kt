package dev.rcht.jist.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
                onOpenAppSettings = { navController.navigate(Screen.AppSettings.createRoute(fromOnboarding = true)) }
            )
        }
        composable(Screen.Dashboard.route) {
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
                val context = LocalContext.current
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
                },
                onDeleteSummaries = { ids -> viewModel.deleteSummaries(ids) }
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
                    onReSummarize = { viewModel.reSummarize() },
                    isReSummarizing = uiState.isReSummarizing
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
                            jistApp.preferencesRepository,
                            jistApp.appRuleRepository,
                            jistApp.llmConfigRepository
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
                notifications = state.notifications,
                isLoading = state.isLoading,
                onRefresh = { vm.refresh() },
                onDelete = { ids -> vm.deleteNotifications(ids) }
            )
        }
        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.WatchList.route) {
            val ctx = LocalContext.current
            val watchListTimeJustNow = ctx.getString(R.string.watch_time_just_now)
            val watchListTimeMinAgo = ctx.getString(R.string.watch_time_min_ago)
            val watchListTimeHourAgo = ctx.getString(R.string.watch_time_hour_ago)
            val watchListTimeDayAgo = ctx.getString(R.string.watch_time_day_ago)
            val watchListTimeNever = ctx.getString(R.string.watch_time_never)
            val viewModel: WatchListViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return WatchListViewModel(
                            jistApp.watchTopicRepository,
                            jistApp.watchCollectedItemRepository,
                            watchListTimeJustNow,
                            watchListTimeMinAgo,
                            watchListTimeHourAgo,
                            watchListTimeDayAgo,
                            watchListTimeNever
                        ) as T
                    }
                }
            )
            val uiState by viewModel.uiState.collectAsState()
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        viewModel.loadData()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            WatchListScreen(
                items = uiState.items,
                isLoading = uiState.isLoading,
                isEmpty = uiState.isEmpty,
                onCreateClick = {
                    navController.navigate(Screen.WatchEdit.createRoute()) {
                        launchSingleTop = true
                    }
                },
                onItemClick = { watchId ->
                    navController.navigate(Screen.WatchDetail.createRoute(watchId))
                },
                onToggleEnabled = { item -> viewModel.toggleEnabled(item.topic) },
                onDeleteClick = { item -> viewModel.deleteTopic(item.topic) }
            )
        }
        composable(
            route = Screen.WatchEdit.route,
            arguments = listOf(
                navArgument("watchId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val watchIdStr = backStackEntry.arguments?.getString("watchId")
            val watchId = watchIdStr?.toLongOrNull()
            val ctx2 = LocalContext.current
            val editErrorTitle = ctx2.getString(R.string.watch_error_title_required)
            val editErrorKeyword = ctx2.getString(R.string.watch_error_keyword_required)
            val editErrorSaveFailed = ctx2.getString(R.string.watch_error_save_failed)
            val editKeywordPrompt = ctx2.getString(R.string.watch_prompt_keywords)
            val editGenerateError = ctx2.getString(R.string.watch_generate_keywords_error)
            val editErrorTitleDuplicate = ctx2.getString(R.string.watch_error_title_duplicate)
            val viewModel: WatchEditViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return WatchEditViewModel(
                            jistApp.watchTopicRepository,
                            watchId,
                            editErrorTitle,
                            editErrorKeyword,
                            editErrorSaveFailed,
                            jistApp.llmConfigRepository,
                            jistApp.httpClient,
                            editKeywordPrompt,
                            editGenerateError,
                            editErrorTitleDuplicate
                        ) as T
                    }
                }
            )
            val state by viewModel.uiState.collectAsState()
            WatchEditScreen(
                title = state.title,
                description = state.description,
                keywords = state.keywords,
                matchMode = state.matchMode,
                isSaving = state.isSaving,
                isGeneratingKeywords = state.isGeneratingKeywords,
                generatedKeywords = state.generatedKeywords,
                error = state.error,
                isEditing = state.isEditing,
                onTitleChange = { viewModel.updateTitle(it) },
                onDescriptionChange = { viewModel.updateDescription(it) },
                onAddKeyword = { viewModel.addKeyword(it) },
                onRemoveKeyword = { viewModel.removeKeyword(it) },
                onMatchModeChange = { viewModel.updateMatchMode(it) },
                onGenerateKeywords = { viewModel.generateKeywords() },
                onConfirmGeneratedKeywords = { viewModel.confirmGeneratedKeywords() },
                onDiscardGeneratedKeywords = { viewModel.discardGeneratedKeywords() },
                onSave = { viewModel.save { navController.popBackStack() } },
                onNavigateBack = { navController.popBackStack() },
                onClearError = { viewModel.clearError() }
            )
        }
        composable(
            route = Screen.WatchDetail.route,
            arguments = listOf(
                navArgument("watchId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val watchId = backStackEntry.arguments?.getLong("watchId") ?: return@composable
            val ctx3 = LocalContext.current
            val viewModel: WatchDetailViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return WatchDetailViewModel(
                            jistApp.watchTopicRepository,
                            jistApp.watchCollectedItemRepository,
                            jistApp.notificationRepository,
                            watchId,
                            ctx3.packageManager
                        ) as T
                    }
                }
            )
            val uiState by viewModel.uiState.collectAsState()
            WatchDetailScreen(
                uiState = uiState,
                onNavigateBack = { navController.popBackStack() },
                onToggleEnabled = { viewModel.toggleEnabled() },
                onEdit = {
                    navController.navigate(Screen.WatchEdit.createRoute(watchId))
                },
                onDelete = { viewModel.delete { navController.popBackStack() } }
            )
        }
    }
}
