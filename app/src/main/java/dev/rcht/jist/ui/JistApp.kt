package dev.rcht.jist.ui

import android.app.Application
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
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.rcht.jist.ui.navigation.JistNavHost
import dev.rcht.jist.ui.navigation.Screen

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

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    modifier = Modifier.background(
                         androidx.compose.ui.graphics.Brush.verticalGradient(
                             colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                         )
                    )
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(item.route) {
                                        launchSingleTop = true
                                        popUpTo(Screen.Dashboard.route) {
                                            saveState = true
                                        }
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                selectedIconColor = dev.rcht.jist.ui.theme.JistCyan,
                                selectedTextColor = dev.rcht.jist.ui.theme.JistCyan,
                                unselectedIconColor = Color.LightGray,
                                unselectedTextColor = Color.LightGray,
                                indicatorColor = dev.rcht.jist.ui.theme.JistCyan.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        JistNavHost(
            navController = navController,
            application = application,
            modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())
        )
    }
}