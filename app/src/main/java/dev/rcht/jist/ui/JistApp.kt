package dev.rcht.jist.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Summarize
// removed duplicate import
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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

val bottomNavItems = listOf(
    BottomNavItem("Dashboard", Screen.Dashboard.route, Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    BottomNavItem("Summaries", Screen.Summaries.route, Icons.Filled.Summarize, Icons.Outlined.Summarize),
    BottomNavItem("Alerts", Screen.NotificationLog.route, Icons.Filled.Notifications, Icons.Outlined.Notifications)
)

val mainTabRoutes = bottomNavItems.map { it.route }.toSet()

@Composable
fun JistApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as Application

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
                    items = bottomNavItems,
                    currentRoute = currentRoute,
                    hazeState = hazeState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}