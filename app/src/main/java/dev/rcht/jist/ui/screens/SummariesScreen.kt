package dev.rcht.jist.ui.screens

import dev.rcht.jist.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import dev.rcht.jist.ui.components.GlassScaffold
import dev.rcht.jist.ui.components.SummaryCard
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rcht.jist.ui.summaries.SummariesUiState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummariesScreen(
    uiState: SummariesUiState = SummariesUiState(),
    onSearchChange: (String) -> Unit = {},
    onAppFilterChange: (String?) -> Unit = {},
    onSummaryClick: (Long) -> Unit = {},
    onDeleteSummaries: (List<Long>) -> Unit = {},
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isSelecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    GlassScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (isSelecting) {
                        Text("${stringResource(R.string.selection_count, selectedIds.size)}", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(stringResource(R.string.summaries_title), fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    if (isSelecting) {
                        IconButton(onClick = { isSelecting = false; selectedIds = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    }
                },
                actions = {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (isSelecting && selectedIds.size < uiState.filteredSummaries.size) {
                        TextButton(onClick = {
                            selectedIds = uiState.filteredSummaries.map { it.id }.toSet()
                        }) {
                            Text(stringResource(R.string.app_settings_select_all))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    if (!isSelecting) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text(stringResource(R.string.summaries_search), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (uiState.filteredSummaries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    LazyColumn {
                        uiState.filteredSummaries.forEachIndexed { index, summary ->
                            val selected = summary.id in selectedIds
                            item(key = summary.id) {
                                SummaryCard(
                                    summary = summary,
                                    onClick = { onSummaryClick(summary.id) },
                                    onLongClick = {
                                        if (!isSelecting) {
                                            isSelecting = true
                                            selectedIds = setOf(summary.id)
                                        }
                                    },
                                    selected = selected,
                                    isSelecting = isSelecting,
                                    onSelectChange = { checked ->
                                        selectedIds = if (checked) selectedIds + summary.id else selectedIds - summary.id
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (index < uiState.filteredSummaries.lastIndex) {
                                item(key = "divider_${summary.id}") {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.summaries_delete_title)) },
            text = { Text(stringResource(R.string.summaries_delete_message, selectedIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSummaries(selectedIds.toList())
                    showDeleteConfirm = false
                    isSelecting = false
                    selectedIds = emptySet()
                }) {
                    Text(stringResource(R.string.summaries_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.summaries_delete_cancel))
                }
            }
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun SummariesScreenPreview() {
    dev.rcht.jist.ui.theme.JistTheme {
        SummariesScreen(
            uiState = SummariesUiState(
                isLoading = false,
                filteredSummaries = listOf(
                    dev.rcht.jist.data.db.entity.SummaryEntity(
                        id = 1,
                        packageName = "com.whatsapp",
                        conversationKey = "team_chat",
                        appName = "WhatsApp",
                        contactOrGroup = "Design Team",
                        summaryText = "Sarah shared new mockups for the dashboard redesign. The team discussed color palette changes and approved the glassmorphism approach.",
                        messageCount = 12,
                        modelUsed = "unknown",
                        createdAt = System.currentTimeMillis()
                    ),
                    dev.rcht.jist.data.db.entity.SummaryEntity(
                        id = 2,
                        packageName = "com.slack",
                        conversationKey = "general",
                        appName = "Slack",
                        contactOrGroup = "#general",
                        summaryText = "Sprint planning meeting moved to Friday. New deployment pipeline is ready for testing.",
                        messageCount = 8,
                        modelUsed = "unknown",
                        createdAt = System.currentTimeMillis() - 3600000
                    )
                )
            )
        )
    }
}
