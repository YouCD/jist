package dev.rcht.jist.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import dev.rcht.jist.ui.components.JistTopBar
import dev.rcht.jist.ui.navigation.JistNavHost
import dev.rcht.jist.ui.navigation.Screen
import kotlinx.coroutines.launch

@Composable
fun JistApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val application = context.applicationContext as Application

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Jist",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    // Drawer items
                    DrawerItem(
                        label = "Dashboard",
                        icon = Icons.Filled.Dashboard,
                        selected = navController.currentDestination?.route == Screen.Dashboard.route,
                        onClick = {
                            navController.navigate(Screen.Dashboard.route) {
                                launchSingleTop = true
                                popUpTo(Screen.Dashboard.route) { inclusive = true }
                            }
                            coroutineScope.launch { drawerState.close() }
                        }
                    )

                    DrawerItem(
                        label = "Summaries",
                        icon = Icons.Filled.Summarize,
                        selected = navController.currentDestination?.route == Screen.Summaries.route,
                        onClick = {
                            navController.navigate(Screen.Summaries.route) {
                                launchSingleTop = true
                                popUpTo(Screen.Dashboard.route)
                            }
                            coroutineScope.launch { drawerState.close() }
                        }
                    )

                    DrawerItem(
                        label = "Notification Log",
                        icon = Icons.Filled.Notifications,
                        selected = navController.currentDestination?.route == Screen.NotificationLog.route,
                        onClick = {
                            navController.navigate(Screen.NotificationLog.route) {
                                launchSingleTop = true
                                popUpTo(Screen.Dashboard.route)
                            }
                            coroutineScope.launch { drawerState.close() }
                        }
                    )

                    DrawerItem(
                        label = "Settings",
                        icon = Icons.Filled.Settings,
                        selected = navController.currentDestination?.route == Screen.Settings.route,
                        onClick = {
                            navController.navigate(Screen.Settings.route) {
                                launchSingleTop = true
                                popUpTo(Screen.Dashboard.route)
                            }
                            coroutineScope.launch { drawerState.close() }
                        }
                    )

                    DrawerItem(
                        label = "About",
                        icon = Icons.Filled.Info,
                        selected = navController.currentDestination?.route == Screen.About.route,
                        onClick = {
                            navController.navigate(Screen.About.route) {
                                launchSingleTop = true
                                popUpTo(Screen.Dashboard.route)
                            }
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                JistTopBar(
                    onMenuClick = {
                        coroutineScope.launch { drawerState.open() }
                    }
                )
            }
        ) { paddingValues ->
            JistNavHost(
                navController = navController,
                application = application,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(imageVector = icon, contentDescription = label) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
