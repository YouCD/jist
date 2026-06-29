package dev.rcht.jist.ui.screens

import dev.rcht.jist.R
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import dev.rcht.jist.ui.components.GlassScaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rcht.jist.data.db.entity.NotificationEntity
import dev.rcht.jist.ui.summaries.GroupedSummariesUiState

@Composable
fun GroupedSummariesScreen(
    uiState: GroupedSummariesUiState = GroupedSummariesUiState(),
    onSearchChange: (String) -> Unit = {},
    onAppFilterChange: (String?) -> Unit = {},
    onSummaryClick: (Long) -> Unit = {},
    onToggleGrouping: () -> Unit = {},
    modifier: Modifier = Modifier,
    formatDate: (Long) -> String = { timeMs ->
        java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timeMs))
    }
) {
    GlassScaffold(modifier = modifier.fillMaxSize()) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                // Search bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text(stringResource(R.string.summaries_search)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Summary list or empty state
                if (uiState.groups.isEmpty() || uiState.groups.all { it.summaries.isEmpty() }) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.summaries_empty),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.summaries_empty_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Group headers and summaries
                        for (group in uiState.groups) {
                            if (group.summaries.isNotEmpty()) {
                                item {
                                    // Group header
                                    Text(
                                        text = group.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                                    )
                                }

                                items(group.summaries.size) { index ->
                                    val summary = group.summaries[index]
                                    SummaryCard(
                                        appName = summary.appName,
                                        contactOrGroup = summary.contactOrGroup,
                                        summaryText = summary.summaryText,
                                        messageCount = summary.messageCount,
                                        createdAt = summary.createdAt,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSummaryClick(summary.id) }
                                    )
                                }

                                item {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    appName: String,
    contactOrGroup: String,
    summaryText: String,
    messageCount: Int,
    createdAt: Long,
    modifier: Modifier = Modifier,
    formatDate: (Long) -> String = { timeMs ->
        java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timeMs))
    }
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with app and contact info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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

            // Summary text (preview)
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Timestamp
            Text(
                text = formatDate(createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun GroupedSummariesScreenPreview() {
    dev.rcht.jist.ui.theme.JistTheme {
        GroupedSummariesScreen(
            uiState = dev.rcht.jist.ui.summaries.GroupedSummariesUiState(
                isLoading = false
            )
        )
    }
}
