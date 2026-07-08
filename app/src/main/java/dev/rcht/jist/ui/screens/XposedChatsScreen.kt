package dev.rcht.jist.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import dev.rcht.jist.R
import dev.rcht.jist.util.DrawableUtil
import dev.rcht.jist.util.displayContent
import dev.rcht.jist.ui.components.GlassCard
import dev.rcht.jist.ui.components.GlassScaffold
import dev.rcht.jist.ui.xposedchats.XposedChatItem
import dev.rcht.jist.ui.xposedchats.XposedSourceGroup
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XposedChatsScreen(
    groups: List<XposedSourceGroup>,
    isLoading: Boolean,
    isEmpty: Boolean,
    onChatClick: (Long) -> Unit,
    onDeleteChats: (List<Long>) -> Unit,
    onToggleSummarized: (chatId: Long, summarized: Boolean) -> Unit,
    onSavePrompt: (chatId: Long, customPrompt: String?, minMessages: Int) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var expandedGroups by remember { mutableStateOf(groups.map { it.source.packageName }.toSet()) }
    val allKeys = groups.map { it.source.packageName }.toSet()
    LaunchedEffect(allKeys) {
        val added = allKeys - expandedGroups
        if (added.isNotEmpty()) expandedGroups = expandedGroups + added
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    GlassScaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (selecting) "已选择 ${selectedIds.size} 项"
                        else stringResource(R.string.xposed_chats_title),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = { selecting = false; selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "退出选择")
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(onClick = { if (selectedIds.isNotEmpty()) showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    isEmpty -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.xposed_chats_empty), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            groups.forEach { group ->
                                val isExpanded = group.source.packageName in expandedGroups
                                item(key = "${group.source.packageName}_header") {
                                    SourceSectionHeader(
                                        packageName = group.source.packageName,
                                        appName = group.source.displayName,
                                        count = group.chats.size,
                                        isExpanded = isExpanded,
                                        onToggle = {
                                            expandedGroups = if (isExpanded) expandedGroups - group.source.packageName
                                            else expandedGroups + group.source.packageName
                                        }
                                    )
                                }
                                if (isExpanded) {
                                    items(group.chats, key = { it.chat.id }) { item ->
                                        XposedChatCard(
                                            item = item,
                                            selecting = selecting,
                                            selected = item.chat.id in selectedIds,
                                            onClick = {
                                                if (selecting) {
                                                    selectedIds = if (item.chat.id in selectedIds)
                                                        selectedIds - item.chat.id
                                                    else selectedIds + item.chat.id
                                                } else {
                                                    onChatClick(item.chat.id)
                                                }
                                            },
                                            onLongClick = {
                                                selecting = true
                                                selectedIds = setOf(item.chat.id)
                                            },
                                            onToggleSummarized = { v -> onToggleSummarized(item.chat.id, v) },
                                            onSavePrompt = { prompt, min -> onSavePrompt(item.chat.id, prompt, min) },
                                            snackbarHostState = snackbarHostState
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

    if (showDeleteDialog && selectedIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("删除后将清除 ${selectedIds.size} 个会话的所有消息，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteChats(selectedIds.toList())
                        selectedIds = emptySet()
                        selecting = false
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SourceSectionHeader(packageName: String, appName: String, count: Int, isExpanded: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            DrawableUtil.drawableToBitmap(drawable).asImageBitmap()
        } catch (_: Exception) { null }
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "收起" else "展开",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        if (appIcon != null) {
            Icon(
                bitmap = appIcon,
                contentDescription = appName,
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text = appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "$count 个会话", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun XposedChatCard(item: XposedChatItem, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onToggleSummarized: (Boolean) -> Unit, onSavePrompt: (customPrompt: String?, minMessages: Int) -> Unit, snackbarHostState: SnackbarHostState? = null) {
    val defaultPrompt = remember { dev.rcht.jist.llm.getDefaultSystemPrompt() }
    var expanded by remember { mutableStateOf(false) }
    var promptText by remember(item.chat.id, item.chat.customPrompt) { mutableStateOf(item.chat.customPrompt ?: defaultPrompt) }
    var minMessagesText by remember(item.chat.id, item.chat.minMessagesForSummary) { mutableStateOf(item.chat.minMessagesForSummary.toString()) }
    val originalPrompt = item.chat.customPrompt ?: defaultPrompt
    val originalMinMessages = item.chat.minMessagesForSummary.toString()
    val hasChanges = promptText != originalPrompt || minMessagesText != originalMinMessages
    val isDefault = promptText == defaultPrompt
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (!selecting) onLongClick() }
            ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = if (expanded) 0.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selecting) {
                    Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.chat.chatName, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.latestMessage != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = buildString {
                                    if (item.latestMessage.senderName.isNotBlank()) {
                                        append(item.latestMessage.senderName); append(": ")
                                    }
                                    append(displayContent(item.latestMessage.content))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(text = formatTimestamp(item.latestMessage.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
                if (item.messageCount > 0) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(text = item.messageCount.toString(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                if (!selecting && item.chat.isSummarized) {
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.Edit,
                            contentDescription = if (expanded) "收起" else "编辑",
                            tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!selecting) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(checked = item.chat.isSummarized, onCheckedChange = onToggleSummarized, modifier = Modifier.height(24.dp))
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    Text("自定义摘要提示词", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
                    if (isDefault) {
                        Text("正在使用默认提示词 — 编辑下方内容以自定义：",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 4.dp))
                    }
                    OutlinedTextField(
                        value = promptText, onValueChange = { promptText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("自定义提示词") }, minLines = 2, maxLines = 6,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("摘要前最少消息数", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    OutlinedTextField(
                        value = minMessagesText,
                        onValueChange = { minMessagesText = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("5") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        if (!isDefault) {
                            TextButton(onClick = {
                                promptText = defaultPrompt
                                onSavePrompt(null, item.chat.minMessagesForSummary)
                                focusManager.clearFocus()
                            }) { Text("重置为默认", color = MaterialTheme.colorScheme.error) }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Button(
                            onClick = {
                                val count = minMessagesText.toIntOrNull()
                                if (count != null && count > 0) {
                                    onSavePrompt(promptText, count)
                                    scope.launch { snackbarHostState?.showSnackbar("已保存") }
                                }
                                focusManager.clearFocus()
                            },
                            enabled = hasChanges,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff <= 0 -> { val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()); sdf.format(Date(timestamp)) }
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分前"
        diff < 86_400_000 -> "${diff / 3_600_000}时前"
        else -> { val sdf = SimpleDateFormat("MM/dd", Locale.getDefault()); sdf.format(Date(timestamp)) }
    }
}

