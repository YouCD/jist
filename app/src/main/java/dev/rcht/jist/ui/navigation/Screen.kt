package dev.rcht.jist.ui.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Dashboard : Screen("dashboard")
    data object Summaries : Screen("summaries")
    data object Settings : Screen("settings")
    data object AppSettings : Screen("app_settings/{fromOnboarding}") {
        fun createRoute(fromOnboarding: Boolean = false) = "app_settings/$fromOnboarding"
    }
    data object NotificationLog : Screen("notification_log")
    data object About : Screen("about")
    
    data object LlmConfig : Screen("llm_config")
    data object WatchList : Screen("watch_list")
    data object WatchEdit : Screen("watch_edit?watchId={watchId}") {
        fun createRoute(watchId: Long? = null) = "watch_edit?watchId=$watchId"
    }
    data object WatchDetail : Screen("watch_detail/{watchId}") {
        fun createRoute(watchId: Long) = "watch_detail/$watchId"
    }
    
    companion object {
        val allScreens = listOf(Dashboard, Summaries, Settings, AppSettings, NotificationLog, About, LlmConfig, WatchList)
    }
}
