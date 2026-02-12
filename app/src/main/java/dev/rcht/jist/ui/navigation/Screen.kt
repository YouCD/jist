package dev.rcht.jist.ui.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Dashboard : Screen("dashboard")
    data object Summaries : Screen("summaries")
    data object Settings : Screen("settings")
    data object AppSettings : Screen("app_settings")
    data object NotificationLog : Screen("notification_log")
    data object About : Screen("about")
    
    companion object {
        val allScreens = listOf(Dashboard, Summaries, Settings, AppSettings, NotificationLog, About)
    }
}
