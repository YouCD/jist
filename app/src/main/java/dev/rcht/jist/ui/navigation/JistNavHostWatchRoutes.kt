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

fun NavGraphBuilder.watchListRoute(
    navController: NavHostController,
    jistApp: JistApplication
) {
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
}

fun NavGraphBuilder.watchEditRoute(
    navController: NavHostController,
    jistApp: JistApplication
) {
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
}

fun NavGraphBuilder.watchDetailRoute(
    navController: NavHostController,
    jistApp: JistApplication
) {
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
