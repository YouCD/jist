package dev.rcht.jist.ui

import dev.rcht.jist.BuildConfig
import dev.rcht.jist.R
import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Visibility
// removed duplicate import
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.rcht.jist.ui.components.GlassBottomNavigation
import dev.rcht.jist.ui.navigation.JistNavHost
import dev.rcht.jist.ui.navigation.Screen
import dev.rcht.jist.ui.theme.AppBackground
import dev.rcht.jist.ui.theme.JistCyan
import dev.rcht.jist.ui.theme.JistPurple

data class BottomNavItem(
    val label: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun bottomNavItems(): List<BottomNavItem> {
    val items = mutableListOf(
        BottomNavItem(stringResource(R.string.nav_dashboard), Screen.Dashboard.route, Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
        BottomNavItem(stringResource(R.string.nav_summaries), Screen.Summaries.route, Icons.Filled.Summarize, Icons.Outlined.Summarize),
        BottomNavItem(stringResource(R.string.nav_alerts), Screen.NotificationLog.route, Icons.Filled.Notifications, Icons.Outlined.Notifications),
        BottomNavItem(stringResource(R.string.watch_bottom_nav), Screen.WatchList.route, Icons.Filled.Visibility, Icons.Outlined.Visibility)
    )
    if (BuildConfig.isXposedFlavor) {
        items.add(1, BottomNavItem(stringResource(R.string.nav_xposed_chats), Screen.XposedChats.route, Icons.Filled.Chat, Icons.Outlined.Chat))
    }
    return items
}

val mainTabRoutes = setOf(Screen.Dashboard.route, Screen.Summaries.route, Screen.NotificationLog.route, Screen.WatchList.route, Screen.XposedChats.route)

@Composable
fun JistApp(deepLinkSummaryId: String? = null) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as Application
    androidx.compose.runtime.LaunchedEffect(deepLinkSummaryId) {
        if (deepLinkSummaryId != null) {
            val parts = deepLinkSummaryId.split(":")
            val id = parts.getOrNull(0) ?: return@LaunchedEffect
            if (id.isBlank()) return@LaunchedEffect
            try {
                kotlinx.coroutines.delay(500)
                navController.navigate("summary_detail/$id") {
                    launchSingleTop = true
                }
            } catch (e: Exception) {
                android.util.Log.e("JistApp", "Navigation failed", e)
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in mainTabRoutes
    val hazeState = rememberHazeState()

    Scaffold(
        containerColor = AppBackground,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            JistNavHost(
                navController = navController,
                application = application,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .hazeSource(hazeState, zIndex = 0f)
            )
            
            if (showBottomBar) {
                GlassBottomNavigation(
                    navController = navController,
                    items = bottomNavItems(),
                    currentRoute = currentRoute,
                    hazeState = hazeState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}