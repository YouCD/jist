package dev.rcht.jist.ui.screens

import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rcht.jist.ui.components.GlassCard
import dev.rcht.jist.ui.components.GlassScaffold
import dev.rcht.jist.ui.dashboard.DashboardUiState
import dev.rcht.jist.ui.theme.JistCyan
import dev.rcht.jist.ui.theme.JistPurple
import dev.rcht.jist.ui.components.PermissionBanner
import dev.rcht.jist.ui.components.BatteryOptimizationBanner
import dev.rcht.jist.util.BatteryOptimizationHelper
import dev.rcht.jist.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState = DashboardUiState(),
    onSummarizeNow: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    hasNotificationListenerPermission: Boolean = false,
    isBatteryOptimizationDisabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    GlassScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Header Section
                Column {
                    Text(
                        text = "GOOD EVENING",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        buildAnnotatedString {
                            append("Jist ")
                            withStyle(style = SpanStyle(color = JistCyan)) {
                                append(if (uiState.isNotificationListenerActive) "Active" else "Inactive")
                            }
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
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
                            .height(160.dp)
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
                                    text = "INTERCEPTED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JistCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Column {
                                Text(
                                    buildAnnotatedString {
                                        withStyle(style = SpanStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold)) {
                                            append(uiState.totalNotificationsCount.toString())
                                        }
                                        withStyle(style = SpanStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))) {
                                            append("\nnotifications")
                                        }
                                    },
                                    color = Color.White
                                )
                            }

                            LinearProgressIndicator(
                                progress = { 0.7f }, // Placeholder progress
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
                            .height(160.dp)
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
                                    text = "SUMMARIZED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JistPurple,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column {
                                Text(
                                    buildAnnotatedString {
                                        withStyle(style = SpanStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold)) {
                                            append(uiState.summariesTodayCount.toString())
                                        }
                                        withStyle(style = SpanStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))) {
                                            append("\ndigests")
                                        }
                                    },
                                    color = Color.White
                                )
                            }
                            // Battery optimization banner if not disabled
                            if (!isBatteryOptimizationDisabled) {
                                BatteryOptimizationBanner(
                                    onEnable = {
                                        BatteryOptimizationHelper.requestDisableBatteryOptimization(context)
                                    },
                                    onDismiss = {
                                        // In a real app we might want to remember this dismissal
                                    }
                                )
                            }
                            // Fake dashed progress
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                repeat(3) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp)
                                            .background(
                                                color = if(it < 2) JistPurple.copy(alpha = 0.5f) else JistPurple,
                                                shape = RoundedCornerShape(2.dp)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                // Time Saved Card
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFF0D47A1).copy(alpha = 0.3f), CircleShape)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.HourglassEmpty,
                                    contentDescription = null,
                                    tint = JistCyan
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Time saved today",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "~42 minutes", // Placeholder logic
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f)
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
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "View All →",
                        style = MaterialTheme.typography.labelMedium,
                        color = JistCyan
                    )
                }

                // Recent Activity List (Mock Items for now matching the design)
                ActivityItem(
                    appName = "Slack",
                    title = "#Design-Team",
                    description = "Sarah updated the Figma file and requested a review of the dashboard components by 3 PM.",
                    time = "2m ago",
                    accentColor = JistCyan
                )
                
                ActivityItem(
                    appName = "Gmail",
                    title = "Gmail • Newsletter",
                    description = "\"Weekly Tech Digest\" discusses new AI regulations and 5 productivity tools for developers.",
                    time = "15m ago",
                    accentColor = Color(0xFFFF5252) // Red for Gmail
                )



                Spacer(modifier = Modifier.height(80.dp)) // Bottom padding for nav bar
            }
        }
    }
}

@Composable
fun ActivityItem(
    appName: String,
    title: String,
    description: String,
    time: String,
    accentColor: Color
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            // Accent Line
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 20.sp
                )
            }
        }
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
                totalNotificationsCount = 12450,
                unsummarizedCount = 12,
                lastSummarizedTime = "2 hours ago"
            ),
            hasNotificationListenerPermission = true,
            isBatteryOptimizationDisabled = true
        )
    }
}
