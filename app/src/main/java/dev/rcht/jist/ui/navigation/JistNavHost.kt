package dev.rcht.jist.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.rcht.jist.ui.screens.AboutScreen
import dev.rcht.jist.ui.screens.DashboardScreen
import dev.rcht.jist.ui.screens.NotificationLogScreen
import dev.rcht.jist.ui.screens.SettingsScreen
import dev.rcht.jist.ui.screens.SummariesScreen

@Composable
fun JistNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen()
        }
        composable(Screen.Summaries.route) {
            SummariesScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(Screen.NotificationLog.route) {
            NotificationLogScreen()
        }
        composable(Screen.About.route) {
            AboutScreen()
        }
    }
}
