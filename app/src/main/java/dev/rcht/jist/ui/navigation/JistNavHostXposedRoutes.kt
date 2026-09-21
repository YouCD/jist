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

fun NavGraphBuilder.xposedChatsRoute(
    navController: NavHostController,
    jistApp: JistApplication
) {
    composable(Screen.XposedChats.route) {
        val viewModel: XposedChatsViewModel = viewModel(
            factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return XposedChatsViewModel(
                        jistApp.watchedChatRepository,
                        jistApp.chatMessageRepository,
                        jistApp.chatSourceRepository,
                        jistApp.summaryRepository,
                        jistApp.llmConfigRepository,
                        jistApp.httpClient
                    ) as T
                }
            }
        )
        val uiState by viewModel.uiState.collectAsState()
        val isRefreshing = remember { mutableStateOf(false) }
        val pendingResummarizeChatId = remember { mutableStateOf<Long?>(null) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.loadData()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        XposedChatsScreen(
            uiState = uiState,
            groups = uiState.groups,
            isLoading = uiState.isLoading,
            isEmpty = uiState.isEmpty,
            onChatClick = { chatId ->
                navController.navigate(Screen.XposedChatDetail.createRoute(chatId))
            },
            onDeleteChats = { ids -> viewModel.deleteChats(ids) },
            onToggleSummarized = { id, v -> viewModel.toggleSummarized(id, v) },
            onSaveChatSettings = { id, prompt, min, retention -> viewModel.updateChatSettings(id, prompt, min, retention) },
            onChatSummarize = { chatId ->
                viewModel.summarizeChat(chatId)
            },
            onSummaryGenerated = { chatId, summaryText ->
                val chats = uiState.groups.flatMap { it.chats }
                val chat = chats.find { it.chat.id == chatId }
                if (chat != null) {
                    viewModel.showSummaryDialogWithText(
                        chatId,
                        chat.chat.chatName,
                        chat.messageCount,
                        chat.latestMessage?.timestamp ?: System.currentTimeMillis(),
                        summaryText
                    )
                }
            },
            pendingResummarizeChatId = pendingResummarizeChatId.value,
            onClearPendingResummarize = { pendingResummarizeChatId.value = null },
            onDismissSummaryDialog = { viewModel.dismissSummaryDialog() },
            onRefresh = {
                isRefreshing.value = true
                viewModel.loadData()
                isRefreshing.value = false
            },
            isRefreshing = isRefreshing.value,
            isSummarizing = uiState.isSummarizing,
            summarizingChatName = uiState.summarizingChatName,
            summarizeError = uiState.summarizeError
        )
    }
}

fun NavGraphBuilder.xposedChatDetailRoute(
    navController: NavHostController,
    jistApp: JistApplication
) {
    composable(
        route = Screen.XposedChatDetail.route,
        arguments = listOf(
            navArgument("chatId") { type = NavType.LongType }
        )
    ) { backStackEntry ->
        val chatId = backStackEntry.arguments?.getLong("chatId") ?: return@composable
        val viewModel: XposedChatDetailViewModel = viewModel(
            factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return XposedChatDetailViewModel(
                        jistApp.watchedChatRepository,
                        jistApp.chatMessageRepository,
                        jistApp.summaryRepository,
                        jistApp.llmConfigRepository,
                        jistApp.chatSourceRepository,
                        jistApp.httpClient,
                        chatId
                    ) as T
                }
            }
        )
        val uiState by viewModel.uiState.collectAsState()
        val detailRefreshing = remember { mutableStateOf(false) }
        XposedChatDetailScreen(
            uiState = uiState,
            onNavigateBack = { navController.popBackStack() },
            onDeleteMessages = { ids -> viewModel.deleteMessages(ids) },
            onSummarize = { viewModel.summarize() },
            isSummarizing = uiState.isSummarizing,
            summarizeError = uiState.summarizeError,
            onRefresh = {
                detailRefreshing.value = true
                viewModel.loadData()
                detailRefreshing.value = false
            },
            isRefreshing = detailRefreshing.value
        )
    }
}
