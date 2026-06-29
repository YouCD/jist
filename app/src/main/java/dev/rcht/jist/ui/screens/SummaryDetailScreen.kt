package dev.rcht.jist.ui.screens

import dev.rcht.jist.R
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import dev.rcht.jist.ui.components.GlassScaffold
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rcht.jist.data.db.entity.NotificationEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryDetailScreen(
    summaryText: String = stringResource(R.string.summary_label),
    appName: String = "WhatsApp",
    contactOrGroup: String = "Team Chat",
    messageCount: Int = 5,
    createdAt: Long = System.currentTimeMillis(),
    notifications: List<NotificationEntity> = emptyList(),
    onNavigateBack: () -> Unit = {},
    onReSummarize: () -> Unit = {},
    isReSummarizing: Boolean = false,
    modifier: Modifier = Modifier,
    formatDate: (Long) -> String = { timeMs ->
        java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timeMs))
    }
) {
    GlassScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.summary_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item {
                // Header card
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = contactOrGroup,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = "$messageCount msg",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatDate(createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.summary_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                    var showGlow by remember(summaryText) { mutableStateOf(false) }
                    LaunchedEffect(summaryText) {
                        showGlow = true
                        delay(80)
                        showGlow = false
                    }
                    if (isReSummarizing) {
                        val t = rememberInfiniteTransition()
                        WaveText("正在生成…", t)
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (showGlow) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                            )
                        ) {
                            Text(
                                text = summaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            // Original notifications section
            if (notifications.isNotEmpty()) {
                item {
                    Text(
                        text = "Original Notifications (${notifications.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(notifications.size) { index ->
                    val notification = notifications[index]
                    NotificationPreviewCard(
                        sender = notification.senderName ?: "Unknown",
                        text = notification.content,
                        timestamp = formatDate(notification.timestamp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
        val infiniteTransition = rememberInfiniteTransition()
        val fabAngle = infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing)
            )
        )
        val fabAlpha = animateFloatAsState(
            targetValue = if (isReSummarizing) 0.4f else 1f,
            animationSpec = tween(300),
            label = "fabAlpha"
        )
        FloatingActionButton(
            onClick = { if (!isReSummarizing) onReSummarize() },
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isReSummarizing) 0.6f else 1f),
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Redo,
                contentDescription = stringResource(R.string.summary_resummarize),
                modifier = Modifier
                    .graphicsLayer(rotationZ = if (isReSummarizing) fabAngle.value else 0f)
                    .graphicsLayer(alpha = fabAlpha.value)
            )
        }
    }
    }
}

@Composable
fun WaveText(text: String, transition: androidx.compose.animation.core.InfiniteTransition) {
    Row(
        modifier = Modifier.padding(16.dp)
    ) {
        text.forEachIndexed { index, char ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, delayMillis = index * 120, easing = LinearEasing)
                )
            )
            Text(
                text = char.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
        }
    }
}

@Composable
private fun NotificationPreviewCard(
    sender: String,
    text: String,
    timestamp: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sender,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = androidx.compose.material3.LocalTextStyle.current.copy().textAlign.let { androidx.compose.ui.text.style.TextOverflow.Ellipsis }
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SummaryDetailScreenPreview() {
    dev.rcht.jist.ui.theme.JistTheme {
        SummaryDetailScreen(
            summaryText = "The team discussed new UI changes including glassmorphism effects, gradient backgrounds, and updated navigation. Sarah shared updated Figma links and requested review by EOD.",
            appName = "WhatsApp",
            contactOrGroup = "Design Team",
            messageCount = 12,
            createdAt = System.currentTimeMillis(),
            notifications = listOf(
                NotificationEntity(
                    id = 1,
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    title = "Design Team",
                    senderName = "Sarah",
                    content = "Hey, I've updated the Figma file with the new dashboard mockups",
                    conversationKey = "team_chat",
                    timestamp = System.currentTimeMillis() - 600000
                ),
                NotificationEntity(
                    id = 2,
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    title = "Design Team",
                    senderName = "Mike",
                    content = "Looks great! I'll review the color changes this afternoon",
                    conversationKey = "team_chat",
                    timestamp = System.currentTimeMillis() - 300000
                )
            )
        )
    }
}
