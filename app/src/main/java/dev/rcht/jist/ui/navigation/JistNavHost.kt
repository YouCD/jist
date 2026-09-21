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
        onboardingRoute(navController, jistApp)
        dashboardRoute(navController, jistApp)
        summariesRoute(navController, jistApp)
        summaryDetailRoute(navController, jistApp)
        settingsRoute(navController, jistApp)
        llmConfigRoute(navController, jistApp)
        appSettingsRoute(navController, jistApp)
        notificationLogRoute(navController, jistApp)
        aboutRoute(navController, jistApp)
        watchListRoute(navController, jistApp)
        watchEditRoute(navController, jistApp)
        watchDetailRoute(navController, jistApp)
        xposedChatsRoute(navController, jistApp)
        xposedChatDetailRoute(navController, jistApp)
    }
}
