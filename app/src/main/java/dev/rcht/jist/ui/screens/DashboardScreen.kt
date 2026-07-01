package dev.rcht.jist.ui.screens

import dev.rcht.jist.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableIntStateOf
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
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
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
                    // Header Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = getGreeting(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            buildAnnotatedString {
                                append("Jist ")
                                withStyle(style = SpanStyle(color = JistCyan)) {
                                    append(if (uiState.isNotificationListenerActive) stringResource(R.string.active) else stringResource(R.string.dashboard_inactive))
                                }
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

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
                    // Intercepted Card
                    GlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp),
                        hazeState = hazeState
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Security,
                                    contentDescription = null,
                                    tint = JistCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.dashboard_intercepted),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JistCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Column {
                                AnimatedCounter(
                                    target = uiState.totalNotificationsCount,
                                    suffix = stringResource(R.string.dashboard_notifications)
                                )
                            }

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
                    }

                    // Summarized Card
                    GlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp),
                        hazeState = hazeState
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = JistPurple,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.dashboard_summarized),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JistPurple,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column {
                                AnimatedCounter(
                                    target = uiState.summariesTodayCount,
                                    suffix = stringResource(R.string.dashboard_digests)
                                )
                            }
                            // Dashed progress based on real ratio
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
                    }
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
                    GlassCard(
                        modifier = Modifier.weight(1f).height(120.dp).clickable { onWatchListClick() },
                        hazeState = hazeState
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = JistCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.watch_list_title),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JistCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                AnimatedCounter(
                                    target = uiState.watchActiveCount,
                                    suffix = stringResource(R.string.dashboard_watching)
                                )
                            }
                        }
                    }

                    GlassCard(
                        modifier = Modifier.weight(1f).height(120.dp).clickable { onWatchListClick() },
                        hazeState = hazeState
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = JistPurple,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.dashboard_collected),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JistPurple,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                AnimatedCounter(
                                    target = uiState.watchCollectedCount,
                                    suffix = stringResource(R.string.dashboard_items)
                                )
                            }
                        }
                    }
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
                    Text(
                        text = stringResource(R.string.dashboard_recent_activity),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
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
                        ActivityItem(
                            packageName = summary.packageName,
                            title = summary.contactOrGroup,
                            description = summary.summaryText,
                            time = formatRelativeTime(summary.createdAt),
                            accentColor = getAppAccentColor(summary.appName),
                            hazeState = hazeState,
                            onClick = { onSummaryClick(summary.id) }
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

@Composable
fun ActivityItem(
    packageName: String = "",
    title: String,
    description: String,
    time: String,
    accentColor: Color,
    hazeState: HazeState?,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        hazeState = hazeState
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // App Icon
            val appIcon: Drawable? = remember(packageName) {
                try {
                    context.packageManager.getApplicationIcon(packageName)
                } catch (e: Exception) {
                    null
                }
            }
            if (appIcon != null) {
                Image(
                    bitmap = appIcon.toBitmap(48, 48).asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                // Fallback colored circle with first letter
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 20.sp,
                    maxLines = 3,
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
    return when (hour) {
        in 5..11 -> stringResource(R.string.dashboard_good_morning)
        in 12..16 -> stringResource(R.string.dashboard_good_afternoon)
        in 17..20 -> stringResource(R.string.dashboard_good_evening)
        else -> stringResource(R.string.dashboard_good_night)
    }
}

// Helper: Format timestamp as relative time
@Composable
private fun formatRelativeTime(timeMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timeMs
    return when {
        diffMs < 60_000 -> stringResource(R.string.dashboard_just_now)
        diffMs < 3_600_000 -> "${diffMs / 60_000}m ago"
        diffMs < 86_400_000 -> "${diffMs / 3_600_000}h ago"
        diffMs < 604_800_000 -> "${diffMs / 86_400_000}d ago"
        else -> java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
            .format(java.util.Date(timeMs))
    }
}

// Helper: Accent color per app name
private fun getAppAccentColor(appName: String): Color {
    return when (appName.lowercase()) {
        "whatsapp" -> Color(0xFF25D366)
        "telegram" -> Color(0xFF0088CC)
        "slack" -> Color(0xFF4A154B)
        "gmail" -> Color(0xFFFF5252)
        "instagram" -> Color(0xFFE1306C)
        "twitter", "x" -> Color(0xFF1DA1F2)
        "discord" -> Color(0xFF5865F2)
        "messenger" -> Color(0xFF006AFF)
        "signal" -> Color(0xFF3A76F0)
        else -> Color(0xFF6C63FF) // Default purple
    }
}

@Composable
private fun AnimatedCounter(
    target: Int,
    suffix: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    val animatedState = remember { mutableIntStateOf(0) }
    LaunchedEffect(target) {
        val frames = 20
        for (i in 1..frames) {
            delay(40)
            animatedState.intValue = target * i / frames
        }
        animatedState.intValue = target
    }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold)) {
                append(animatedState.intValue.toString())
            }
            withStyle(SpanStyle(fontSize = 12.sp, color = color.copy(alpha = 0.6f))) {
                append("\n$suffix")
            }
        },
        color = color,
        modifier = modifier
    )
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
                        messageCount = 5, modelUsed = "gpt-4o-mini",
                        createdAt = System.currentTimeMillis() - 120_000
                    ),
                    SummaryEntity(
                        id = 2, packageName = "com.google.android.gm",
                        conversationKey = "newsletter", appName = "Gmail",
                        contactOrGroup = "Gmail • Newsletter",
                        summaryText = "\"Weekly Tech Digest\" discusses new AI regulations and 5 productivity tools for developers.",
                        messageCount = 1, modelUsed = "gpt-4o-mini",
                        createdAt = System.currentTimeMillis() - 900_000
                    )
                )
            ),
            hasNotificationListenerPermission = true,
            isBatteryOptimizationDisabled = true
        )
    }
}
