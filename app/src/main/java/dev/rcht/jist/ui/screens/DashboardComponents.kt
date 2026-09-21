package dev.rcht.jist.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.rcht.jist.R
import dev.rcht.jist.data.db.entity.SummaryEntity
import dev.rcht.jist.ui.components.GlassCard
import dev.rcht.jist.ui.dashboard.DashboardUiState
import dev.rcht.jist.ui.dashboard.WatchRecentMatch
import dev.rcht.jist.ui.theme.JistCyan
import dev.rcht.jist.ui.theme.JistTheme
import java.util.Calendar
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme

@Composable
internal fun WatchMatchItem(
    match: WatchRecentMatch,
    hazeState: HazeState?,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        hazeState = hazeState
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = LocalContext.current
            val iconBitmap = remember(match.packageName) {
                try {
                    val d = context.packageManager.getApplicationIcon(match.packageName)
                    val bmp = Bitmap.createBitmap(
                        d.intrinsicWidth.coerceAtLeast(1), d.intrinsicHeight.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    val c = Canvas(bmp)
                    d.setBounds(0, 0, c.width, c.height)
                    d.draw(c)
                    bmp.asImageBitmap()
                } catch (_: Exception) { null }
            }
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        match.appName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "·",
                        color = Color.White.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        match.timeAgo,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.watch_match_display, match.matchedKeyword),
                    style = MaterialTheme.typography.labelSmall,
                    color = JistCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Helper: Dynamic greeting based on time of day
@Composable
internal fun getGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val minute = Calendar.getInstance().get(Calendar.MINUTE)
    val time = hour * 60 + minute
    return when {
        time < 5*60 -> stringResource(R.string.dashboard_good_wee_hours)
        time < 8*60 -> stringResource(R.string.dashboard_good_morning)
        time < 12*60 -> stringResource(R.string.dashboard_good_forenoon)
        time < 13*60 -> stringResource(R.string.dashboard_good_noon)
        time < 18*60 -> stringResource(R.string.dashboard_good_afternoon)
        time < 19*60 + 30 -> stringResource(R.string.dashboard_good_evening)
        else -> stringResource(R.string.dashboard_good_night)
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
internal fun DashboardScreenPreview() {
    dev.rcht.jist.ui.theme.JistTheme {
        DashboardScreen(
            uiState = DashboardUiState(
                isLoading = false,
                isNotificationListenerActive = true,
                notificationsTodayCount = 128,
                summariesTodayCount = 45,
                totalNotificationsCount = 128,
                unsummarizedCount = 12,
                lastSummarizedTime = "2 hours ago",
                timeSavedMinutes = 42,
                recentSummaries = listOf(
                    SummaryEntity(
                        id = 1, packageName = "com.slack",
                        conversationKey = "design_team", appName = "Slack",
                        contactOrGroup = "#Design-Team",
                        summaryText = "Sarah updated the Figma file and requested a review of the dashboard components by 3 PM.",
                        messageCount = 5, modelUsed = "unknown",
                        createdAt = System.currentTimeMillis() - 120_000
                    ),
                    SummaryEntity(
                        id = 2, packageName = "com.google.android.gm",
                        conversationKey = "newsletter", appName = "Gmail",
                        contactOrGroup = "Gmail • Newsletter",
                        summaryText = "\"Weekly Tech Digest\" discusses new AI regulations and 5 productivity tools for developers.",
                        messageCount = 1, modelUsed = "unknown",
                        createdAt = System.currentTimeMillis() - 900_000
                    )
                )
            ),
            hasNotificationListenerPermission = true,
            isBatteryOptimizationDisabled = true
        )
    }
}
