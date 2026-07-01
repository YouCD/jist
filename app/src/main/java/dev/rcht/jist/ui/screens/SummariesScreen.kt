package dev.rcht.jist.ui.screens

import dev.rcht.jist.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import dev.rcht.jist.ui.components.MarkdownText
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
import dev.rcht.jist.ui.summaries.SummaryGroup
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
                        Text("${selectedIds.size} selected", fontWeight = FontWeight.SemiBold)
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
                    } else if (!isSelecting && uiState.filteredSummaries.isNotEmpty()) {
                        TextButton(onClick = {
                            isSelecting = true
                            selectedIds = emptySet()
                        }) {
                            Text(stringResource(R.string.summaries_select))
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
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.groupedSummaries.forEach { group ->
                            item {
                                Text(
                                    text = if (isSelecting) "" else group.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }
                            group.summaries.forEach { summary ->
                                val selected = summary.id in selectedIds
                                item {
                                    val ctx = LocalContext.current
                                    val icon = remember(summary.packageName) {
                                        try {
                                            val d = ctx.packageManager.getApplicationIcon(summary.packageName)
                                            val b = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
                                            val c = android.graphics.Canvas(b)
                                            d.setBounds(0, 0, 48, 48)
                                            d.draw(c)
                                            b.asImageBitmap()
                                        } catch (e: Exception) { null }
                                    }
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (isSelecting) {
                                                    selectedIds = if (selected) selectedIds - summary.id else selectedIds + summary.id
                                                } else {
                                                    onSummaryClick(summary.id)
                                                }
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            else MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            if (isSelecting) {
                                                Checkbox(checked = selected, onCheckedChange = {
                                                    selectedIds = if (selected) selectedIds - summary.id else selectedIds + summary.id
                                                }, modifier = Modifier.padding(end = 8.dp))
                                            } else if (icon != null) {
                                                Image(bitmap = icon, contentDescription = null,
                                                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).padding(end = 10.dp))
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(summary.appName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                                        Text(summary.contactOrGroup, style = MaterialTheme.typography.titleSmall)
                                                    }
                                                    Text("${summary.messageCount} msg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                MarkdownText(markdown = summary.summaryText, maxLines = 3)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(summary.createdAt)),
                                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
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

@Composable
private fun SummaryCard(
    packageName: String = "",
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
    val context = LocalContext.current
    val appIcon: androidx.compose.ui.graphics.ImageBitmap? = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                ?: android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888).also {
                    val canvas = android.graphics.Canvas(it)
                    drawable.setBounds(0, 0, 48, 48)
                    drawable.draw(canvas)
                }
            bitmap.asImageBitmap()
        } catch (e: Exception) { null }
    }

    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column {
                        Text(appName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(contactOrGroup, style = MaterialTheme.typography.titleSmall)
                    }
                }
                Text("$messageCount msg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MarkdownText(summaryText, maxLines = 3)
            Text(formatDate(createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
                        modelUsed = "gpt-4o-mini",
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
                        modelUsed = "gpt-4",
                        createdAt = System.currentTimeMillis() - 3600000
                    )
                )
            )
        )
    }
}
