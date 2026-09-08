package dev.rcht.jist.ui.screens

import dev.rcht.jist.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.blur
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.rcht.jist.data.db.entity.SummaryEntity
import dev.rcht.jist.ui.components.GlassCard
import dev.rcht.jist.ui.components.GlassScaffold
import dev.rcht.jist.ui.components.StatPanelCard
import dev.rcht.jist.ui.components.SummaryCard
import dev.rcht.jist.ui.dashboard.DashboardUiState
import dev.rcht.jist.ui.dashboard.WatchRecentMatch
import dev.rcht.jist.ui.theme.JistCyan
import dev.rcht.jist.ui.theme.JistPurple
import dev.rcht.jist.ui.components.PermissionBanner
import dev.rcht.jist.ui.components.BatteryOptimizationBanner
import dev.rcht.jist.util.BatteryOptimizationHelper
import dev.rcht.jist.util.PermissionHelper
import java.util.Calendar
import kotlinx.coroutines.delay
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState = DashboardUiState(),
    onSummarizeNow: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onViewAllClick: () -> Unit = {},
    onSummaryClick: (Long) -> Unit = {},
    onWatchTopicClick: (Long) -> Unit = {},
    onWatchCreateClick: () -> Unit = {},
    onWatchListClick: () -> Unit = {},
    hasNotificationListenerPermission: Boolean = false,
    isBatteryOptimizationDisabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPreviewMode = LocalInspectionMode.current
    val hazeState = if (isPreviewMode) null else rememberHazeState()

    GlassScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        buildAnnotatedString {
                            append(getGreeting())
                            append("，Jist ")
                            withStyle(style = SpanStyle(color = JistCyan)) {
                                append(if (uiState.isNotificationListenerActive) stringResource(R.string.active) else stringResource(R.string.dashboard_inactive))
                            }
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = JistCyan)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background layer with hazeSource (only in non-preview mode)
                if (!isPreviewMode && hazeState != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(hazeState)
                    ) {
                        // Blue blur oval in background
                        Box(
                            modifier = Modifier
                                .width(0.dp)
                                .height(0.dp)
                                .offset(y = 700.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF00E5FF).copy(alpha = 0.4f),
                                            Color(0xFF00E5FF).copy(alpha = 0.2f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = RoundedCornerShape(0)
                                )
                                .blur(radius = 60.dp)
                        )
                    }
                }
                
                // Foreground layer with glass cards
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Permission banner if listener not enabled
                    if (!hasNotificationListenerPermission) {
                    PermissionBanner(
                        onEnable = {
                            PermissionHelper.openNotificationSettings(context)
                        },
                        onDismiss = {}
                    )
                }

                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatPanelCard(
                        icon = Icons.Outlined.Security,
                        iconTint = JistCyan,
                        title = stringResource(R.string.dashboard_intercepted),
                        counter = uiState.totalNotificationsCount,
                        suffix = stringResource(R.string.dashboard_notifications),
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f),
                        bottomContent = {
                            LinearProgressIndicator(
                                progress = {
                                    if (uiState.totalNotificationsCount > 0) {
                                        (uiState.totalNotificationsCount.coerceAtMost(200) / 200f)
                                    } else 0f
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = JistCyan,
                                trackColor = JistCyan.copy(alpha = 0.2f),
                            )
                        }
                    )

                    StatPanelCard(
                        icon = Icons.Outlined.AutoAwesome,
                        iconTint = JistPurple,
                        title = stringResource(R.string.dashboard_summarized),
                        counter = uiState.summariesTodayCount,
                        suffix = stringResource(R.string.dashboard_digests),
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f),
                        unreadCount = uiState.unreadSummariesCount,
                        bottomContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val ratio = if (uiState.totalNotificationsCount > 0) {
                                    (uiState.summariesTodayCount.toFloat() / uiState.totalNotificationsCount.coerceAtLeast(1)).coerceIn(0f, 1f)
                                } else 0f
                                val filledSegments = (ratio * 3).toInt().coerceIn(0, 3)
                                repeat(3) { index ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp)
                                            .background(
                                                color = if (index <= filledSegments) JistPurple else JistPurple.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(2.dp)
                                            )
                                    )
                                }
                            }
                        }
                    )
                }

                // Battery optimization banner if not disabled
                if (!isBatteryOptimizationDisabled) {
                    BatteryOptimizationBanner(
                        onEnable = {
                            BatteryOptimizationHelper.requestDisableBatteryOptimization(context)
                        },
                        onDismiss = {}
                    )
                }

                // Watch Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatPanelCard(
                        icon = Icons.Filled.Visibility,
                        iconTint = JistCyan,
                        title = stringResource(R.string.watch_list_title),
                        counter = uiState.watchActiveCount,
                        suffix = stringResource(R.string.dashboard_watching),
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f),
                        height = 120.dp,
                        onClick = onWatchListClick
                    )

                    StatPanelCard(
                        icon = Icons.Filled.Info,
                        iconTint = JistPurple,
                        title = stringResource(R.string.dashboard_collected),
                        counter = uiState.watchCollectedCount,
                        suffix = stringResource(R.string.dashboard_items),
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f),
                        height = 120.dp,
                        onClick = onWatchListClick
                    )
                }

                // Recent Watch Matches
                if (uiState.recentWatchMatches.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.dashboard_recent_matches),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.dashboard_view_all),
                            style = MaterialTheme.typography.labelMedium,
                            color = JistCyan,
                            modifier = Modifier.clickable { onWatchListClick() }
                        )
                    }

                    uiState.recentWatchMatches.forEach { match ->
                        WatchMatchItem(
                            match = match,
                            hazeState = hazeState,
                            onClick = { onWatchTopicClick(match.topicId) }
                        )
                    }
                }

                // Recent Activity Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.dashboard_recent_activity),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (uiState.unreadSummariesCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(JistCyan.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${uiState.unreadSummariesCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JistCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.dashboard_view_all),
                        style = MaterialTheme.typography.labelMedium,
                        color = JistCyan,
                        modifier = Modifier.clickable { onViewAllClick() }
                    )
                }

                // Recent Activity List from real data
                if (uiState.recentSummaries.isEmpty()) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        hazeState = hazeState
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.dashboard_no_summaries),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Text(
                                text = stringResource(R.string.dashboard_tap_to_summarize),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }
                } else {
                    uiState.recentSummaries.forEach { summary ->
                        SummaryCard(
                            summary = summary,
                            onClick = { onSummaryClick(summary.id) },
                            useMarkdown = true,
                            glassHazeState = hazeState,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(80.dp)) // Bottom padding for nav bar
                }
            }
        }
    }
}

@Composable
private fun WatchMatchItem(
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
private fun getGreeting(): String {
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
private fun DashboardScreenPreview() {
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
