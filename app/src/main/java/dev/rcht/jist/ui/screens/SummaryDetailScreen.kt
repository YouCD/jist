package dev.rcht.jist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import dev.rcht.jist.ui.components.GlassScaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.rcht.jist.data.db.entity.NotificationEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryDetailScreen(
    summaryText: String = "Sample summary",
    appName: String = "WhatsApp",
    contactOrGroup: String = "Team Chat",
    messageCount: Int = 5,
    createdAt: Long = System.currentTimeMillis(),
    notifications: List<NotificationEntity> = emptyList(),
    onNavigateBack: () -> Unit = {},
    onReSummarize: () -> Unit = {},
    modifier: Modifier = Modifier,
    formatDate: (Long) -> String = { timeMs ->
        java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timeMs))
    }
) {
    GlassScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Summary Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
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
                // Summary section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Summary",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Card {
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
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

            item {
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onReSummarize,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Re-summarize")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
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
