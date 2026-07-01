package dev.rcht.jist.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rcht.jist.R
import dev.rcht.jist.ui.components.GlassCard
import dev.rcht.jist.ui.components.GlassScaffold
import dev.rcht.jist.ui.theme.JistCyan
import dev.rcht.jist.ui.theme.JistPurple
import dev.rcht.jist.ui.components.MarkdownText
import dev.rcht.jist.ui.watchdetail.AppGroup
import dev.rcht.jist.ui.watchdetail.CollectedItemDisplay
import dev.rcht.jist.ui.watchdetail.TimeGroup
import dev.rcht.jist.ui.watchdetail.WatchDetailUiState
import dev.rcht.jist.util.AppIconExtractor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WatchDetailScreen(
    uiState: WatchDetailUiState,
    onNavigateBack: () -> Unit,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topic = uiState.topic
    var showDeleteConfirm by remember { mutableStateOf(false) }

    GlassScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(topic?.title ?: "", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.watch_content_desc_back))
                    }
                },
                actions = {
                    if (topic != null) {
                        IconButton(onClick = onToggleEnabled) {
                            Icon(
                                if (topic.isEnabled) Icons.Outlined.Visibility
                                else Icons.Outlined.VisibilityOff,
                                contentDescription = if (topic.isEnabled)
                                    stringResource(R.string.watch_content_desc_pause)
                                else stringResource(R.string.watch_content_desc_enable)
                            )
                        }
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit,
                                contentDescription = stringResource(R.string.watch_content_desc_edit))
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete,
                                contentDescription = stringResource(R.string.watch_content_desc_delete),
                                tint = MaterialTheme.colorScheme.error)
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
        } else if (topic == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.watch_not_found),
                    style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val expandedMap = remember { mutableStateMapOf<String, Boolean>() }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { MetaInfoCard(uiState = uiState) }

                if (uiState.groups.isNotEmpty()) {
                    item { SummaryCard(uiState = uiState) }
                }

                uiState.groups.forEach { group ->
                    val expanded = expandedMap.getOrDefault(group.packageName, true)
                    item {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column {
                                AppGroupHeader(
                                    appName = group.appName,
                                    packageName = group.packageName,
                                    expanded = expanded,
                                    onToggle = { expandedMap[group.packageName] = !expanded }
                                )
                                if (expanded) {
                                    group.timeGroups.forEach { timeGroup ->
                                        TimeSubHeader(label = timeGroup.label)
                                        timeGroup.items.forEach { item ->
                                            CollectedItemCard(item = item)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.watch_delete_title)) },
            text = { Text(stringResource(R.string.watch_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.watch_delete_confirm),
                        color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.watch_delete_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaInfoCard(uiState: WatchDetailUiState) {
    val topic = uiState.topic ?: return
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (topic.description.isNotBlank()) {
                Text(topic.description, style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }

            val keywordList = try {
                kotlinx.serialization.json.Json
                    .decodeFromString<List<String>>(topic.keywords)
            } catch (_: Exception) { emptyList<String>() }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.watch_keywords_label_colon),
                    style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    keywordList.forEach { kw ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(kw, style = MaterialTheme.typography.labelSmall) }
                        )
                }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                if (topic.isEnabled) stringResource(R.string.watch_status_active)
                else stringResource(R.string.watch_status_paused),
                style = MaterialTheme.typography.labelSmall,
                color = if (topic.isEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryCard(uiState: WatchDetailUiState) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.watch_collected_summary, uiState.totalCount, uiState.appCount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AppGroupHeader(appName: String, packageName: String, expanded: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName) {
        try {
            val d = AppIconExtractor.getAppIcon(context, packageName) ?: return@remember null
            val bmp = Bitmap.createBitmap(d.intrinsicWidth.coerceAtLeast(1), d.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            d.setBounds(0, 0, canvas.width, canvas.height)
            d.draw(canvas)
            bmp.asImageBitmap()
        } catch (_: Exception) { null }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconBitmap != null) {
                    Image(bitmap = iconBitmap, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
            if (expanded) {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun TimeSubHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun CollectedItemCard(item: CollectedItemDisplay) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    if (item.notificationTitle.isNotBlank()) {
                        Text(
                            item.notificationTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    item.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            if (item.notificationContent.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                MarkdownText(
                    markdown = item.notificationContent,
                    maxLines = Int.MAX_VALUE
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = JistPurple.copy(alpha = 0.1f)
                ) {
                    Text(
                        stringResource(R.string.watch_item_match, item.matchedKeyword),
                        style = MaterialTheme.typography.labelSmall,
                        color = JistPurple,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (item.aiExtractedInfo != null && item.matchType == "AI_SEMANTIC") {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = JistCyan.copy(alpha = 0.1f)
                    ) {
                        Text(
                            item.aiExtractedInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = JistCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
