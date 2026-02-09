package dev.rcht.jist.data.preferences

import kotlinx.serialization.Serializable

@Serializable
data class JistPreferences(
    val isOnboardingComplete: Boolean = false,
    val defaultMode: String = "AUTO", // AUTO, MANUAL, DISABLED
    val defaultBatchWindowMinutes: Int = 15,
    val defaultMinMessages: Int = 3,
    val autoDeleteNotificationsAfterDays: Int = 7,
    val autoDeleteSummariesAfterDays: Int = 30,
    val deleteRawAfterSummarizing: Boolean = false,
    val theme: String = "SYSTEM", // SYSTEM, LIGHT, DARK
    val summaryNotificationSound: Boolean = true,
    val summaryNotificationVibrate: Boolean = true
)
