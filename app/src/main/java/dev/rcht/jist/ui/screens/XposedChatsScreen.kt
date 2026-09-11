package dev.rcht.jist.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import dev.rcht.jist.R
import dev.rcht.jist.util.DrawableUtil
import dev.rcht.jist.util.displayContent
import dev.rcht.jist.ui.components.GlassCard
import dev.rcht.jist.ui.components.GlassScaffold
import dev.rcht.jist.ui.xposedchats.SummaryDialogState
import dev.rcht.jist.ui.xposedchats.XposedChatItem
import dev.rcht.jist.ui.xposedchats.XposedChatsState
import dev.rcht.jist.ui.xposedchats.XposedSourceGroup
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XposedChatsScreen(
    uiState: XposedChatsState,
    groups: List<XposedSourceGroup>,
    isLoading: Boolean,
    isEmpty: Boolean,
    onChatClick: (Long) -> Unit,
    onDeleteChats: (List<Long>) -> Unit,
    onToggleSummarized: (chatId: Long, summarized: Boolean) -> Unit,
    onSaveChatSettings: (chatId: Long, customPrompt: String?, minMessages: Int, retentionDays: Int) -> Unit,
    onChatSummarize: (Long) -> Unit,
    onSummaryGenerated: (Long, String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean = false,
    isSummarizing: Boolean = false,
    summarizeError: String? = null,
    pendingResummarizeChatId: Long? = null,
    onClearPendingResummarize: () -> Unit,
    onDismissSummaryDialog: () -> Unit,
    summarizingChatName: String? = null,
    modifier: Modifier = Modifier
) {
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var knownKeys: List<String> by rememberSaveable { mutableStateOf(ArrayList(groups.map { it.source.packageName })) }
    var expandedGroups: List<String> by rememberSaveable { mutableStateOf(ArrayList(groups.map { it.source.packageName })) }
    var pendingResummarizeChatId by remember { mutableStateOf<Long?>(null) }
    val allKeys = groups.map { it.source.packageName }.toSet()
    LaunchedEffect(allKeys) {
        val added = allKeys - knownKeys.toSet()
        if (added.isNotEmpty()) {
            knownKeys = knownKeys + added.toList()
            expandedGroups = expandedGroups + added.toList()
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    LaunchedEffect(isSummarizing, summarizingChatName) {
        if (!isSummarizing && summarizingChatName != null) {
            // Show the summary dialog after summarization completes
            val chat = groups.flatMap { it.chats }.find { it.chat.chatName == summarizingChatName }
            if (chat != null) {
                onSummaryGenerated(chat.chat.id, "")
            }
        }
    }

    val stickyHeaderKeys by derivedStateOf {
        lazyListState.layoutInfo.visibleItemsInfo
            .filter { it.key is String && (it.key as String).endsWith("_header") }
            .filter { it.offset == lazyListState.layoutInfo.viewportStartOffset }
            .map { it.key as String }
            .toSet()
    }

    GlassScaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (selecting) stringResource(R.string.selection_count, selectedIds.size)
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
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            groups.forEach { group ->
                                val isExpanded = group.source.packageName in expandedGroups
                                val headerKey = "${group.source.packageName}_header"
                                stickyHeader(key = headerKey) {
                                    SourceSectionHeader(
                                        packageName = group.source.packageName,
                                        appName = group.source.displayName,
                                        count = group.chats.size,
                                        totalUnread = group.chats.sumOf { it.messageCount },
                                        latestTimestamp = group.chats.maxOfOrNull { chat ->
                                            chat.latestMessage?.timestamp ?: 0L
                                        } ?: 0L,
                                        isExpanded = isExpanded,
                                        onToggle = {
                                            expandedGroups = if (isExpanded) expandedGroups - group.source.packageName
                                            else expandedGroups + group.source.packageName
                                        },
                                        isSticky = headerKey in stickyHeaderKeys
                                    )
                                }
                                item(key = "${group.source.packageName}_chats") {
                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                                        exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                                    ) {
                                         Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                            group.chats.forEachIndexed { index, chatItem ->
                                                if (index > 0) {
                                                    Spacer(Modifier.height(8.dp))
                                                }
                                                XposedChatCard(
                                                    item = chatItem,
                                                    selecting = selecting,
                                                    selected = chatItem.chat.id in selectedIds,
                                                    onClick = {
                                                        if (selecting) {
                                                            selectedIds = if (chatItem.chat.id in selectedIds)
                                                                selectedIds - chatItem.chat.id
                                                            else selectedIds + chatItem.chat.id
                                                        } else {
                                                            onChatClick(chatItem.chat.id)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        selecting = true
                                                        selectedIds = setOf(chatItem.chat.id)
                                                    },
                                                    onToggleSummarized = { v -> onToggleSummarized(chatItem.chat.id, v) },
                                                    onSaveChatSettings = { prompt, min, retention -> onSaveChatSettings(chatItem.chat.id, prompt, min, retention) },
                                                    onChatSummarize = { onChatSummarize(chatItem.chat.id) },
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

    if (uiState.summaryDialogState != null || pendingResummarizeChatId != null) {
        val state = uiState.summaryDialogState
        SummaryDialog(
            chatName = state?.chatName ?: "",
            messageCount = state?.messageCount ?: 0,
            summaryText = state?.summaryText ?: "",
            createdAt = state?.timestamp ?: System.currentTimeMillis(),
            onDismiss = {
                if (pendingResummarizeChatId != null) {
                    val chatId = pendingResummarizeChatId!!
                    pendingResummarizeChatId = null
                    onChatSummarize(chatId)
                } else {
                    onClearPendingResummarize()
                    onDismissSummaryDialog()
                }
            },
            onResummarize = {
                pendingResummarizeChatId = state?.chatId
                onDismissSummaryDialog()
            }
        )
    }

    LaunchedEffect(isSummarizing, summarizingChatName) {
        if (!isSummarizing && summarizingChatName != null) {
            // Show summary dialog when summarization completes
            val chat = groups.flatMap { it.chats }.find { it.chat.chatName == summarizingChatName }
            if (chat != null) {
                onSummaryGenerated(chat.chat.id, chat.chat.chatName)
            }
        }
    }

    if (isSummarizing) {
        val name = summarizingChatName ?: "该群聊"
        AlertDialog(
            onDismissRequest = { },
            title = { Text("正在生成摘要") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("正在为「$name」生成 AI 摘要...")
                }
            },
            confirmButton = { }
        )
    }
}

@Composable
private fun SourceSectionHeader(
    packageName: String,
    appName: String,
    count: Int,
    totalUnread: Int = 0,
    latestTimestamp: Long = 0L,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isSticky: Boolean = false
) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            DrawableUtil.drawableToBitmap(drawable).asImageBitmap()
        } catch (_: Exception) { null }
    }
    val rotation = if (isExpanded) 180f else 0f
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                )
                .combinedClickable(
                    onClick = onToggle,
                    onLongClick = { showMenu = true }
                )
                .padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (appIcon != null) {
                Icon(
                    bitmap = appIcon,
                    contentDescription = appName,
                    modifier = Modifier.size(28.dp),
                    tint = Color.Unspecified
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = appName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${count}个会话",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (totalUnread > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "· ${formatNumber(totalUnread)}",
                    fontSize = 12.sp,
                    color = Color(0xFFE53935)
                )
            }
            if (latestTimestamp > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "· ${formatTimestamp(latestTimestamp)}",
                    fontSize = 12.sp,
                    color = timestampColor(latestTimestamp)
                )
            }
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "收起" else "展开",
                modifier = Modifier.size(16.dp).rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(if (isExpanded) "全部折叠" else "全部展开") },
                onClick = { showMenu = false; onToggle() }
            )
        }
        HorizontalDivider(thickness = 1.dp, color = Color(0xFF333333))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun XposedChatCard(item: XposedChatItem, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onToggleSummarized: (Boolean) -> Unit, onSaveChatSettings: (customPrompt: String?, minMessages: Int, retentionDays: Int) -> Unit, onChatSummarize: () -> Unit, snackbarHostState: SnackbarHostState? = null) {
    val defaultPrompt by rememberUpdatedState(dev.rcht.jist.llm.getDefaultSystemPrompt())
    var expanded by remember { mutableStateOf(false) }
    var promptText by remember { mutableStateOf(item.chat.customPrompt ?: defaultPrompt) }
    var minMessagesText by remember { mutableStateOf(item.chat.minMessagesForSummary.toString()) }
    var retentionDaysText by remember { mutableStateOf(item.chat.retentionDays.toString()) }
    var savedPrompt by remember { mutableStateOf(item.chat.customPrompt ?: defaultPrompt) }
    var savedMinMessages by remember { mutableStateOf(item.chat.minMessagesForSummary.toString()) }
    var savedRetentionDays by remember { mutableStateOf(item.chat.retentionDays.toString()) }
    val hasChanges by derivedStateOf { promptText != savedPrompt || minMessagesText != savedMinMessages || retentionDaysText != savedRetentionDays }
    val isDefault by derivedStateOf { promptText == defaultPrompt }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val summaryActionWidth = 72.dp
    val summaryActionWidthPx by derivedStateOf { with(density) { summaryActionWidth.toPx() } }
    val offsetX = remember { Animatable(0f) }
    val cardOpen by derivedStateOf { offsetX.value < -0.5f }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .height(45.dp)
                .width(summaryActionWidth)
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable {
                    scope.launch { offsetX.animateTo(0f, tween(200)) }
                    onChatSummarize()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = "AI摘要", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = if (offsetX.value < -summaryActionWidthPx / 2f) -summaryActionWidthPx else 0f,
                                    animationSpec = tween(200)
                                )
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-summaryActionWidthPx, 0f))
                            }
                        }
                    )
                }
        ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                .combinedClickable(
                    onClick = {
                        if (offsetX.value < -0.5f) {
                            scope.launch { offsetX.animateTo(0f, tween(200)) }
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = { if (!selecting) onLongClick() }
                ),
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(12.dp)
        ) {
        Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = if (expanded) 0.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                if (selecting) {
                    Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = item.chat.chatName, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        if (item.latestMessage != null) {
                            Text(text = formatTimestamp(item.latestMessage.timestamp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val msg = item.latestMessage
                        if (msg != null) {
                            Text(
                                text = buildString {
                                    if (msg.senderName.isNotBlank()) {
                                        append(msg.senderName); append(": ")
                                    }
                                    append(displayContent(msg.content))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (item.messageCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(text = item.messageCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
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
                    Spacer(Modifier.height(8.dp))
                    Text("消息保留天数（0=永久）", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    OutlinedTextField(
                        value = retentionDaysText,
                        onValueChange = { retentionDaysText = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("7") }, singleLine = true,
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
                                onSaveChatSettings(defaultPrompt, item.chat.minMessagesForSummary, item.chat.retentionDays)
                                focusManager.clearFocus()
                            }) { Text("重置为默认", color = MaterialTheme.colorScheme.error) }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Button(
                            onClick = {
                                val count = minMessagesText.toIntOrNull() ?: 5
                                val days = retentionDaysText.toIntOrNull() ?: 0
                                if (count > 0) {
                                    onSaveChatSettings(promptText, count, days)
                                }
                                savedPrompt = promptText
                                savedMinMessages = minMessagesText
                                savedRetentionDays = retentionDaysText
                                scope.launch { snackbarHostState?.showSnackbar("已保存") }
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
    }
}

internal fun formatNumber(n: Int): String {
    return when {
        n >= 10_000 -> "${n / 10_000}.${(n % 10_000) / 1_000}万"
        n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}k"
        else -> n.toString()
    }
}

@Composable
internal fun timestampColor(timestamp: Long): Color {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        diff < 3_600_000 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    }
}

internal fun formatTimestamp(timestamp: Long): String {
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

@Composable
private fun SummaryDialog(
    chatName: String,
    messageCount: Int,
    summaryText: String,
    createdAt: Long,
    onDismiss: () -> Unit,
    onResummarize: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("「$chatName」摘要 · ${messageCount} 条消息") },
        text = {
            Column {
                Text("摘要时间：${formatTimestamp(createdAt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (summaryText.isNotEmpty()) {
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "暂无摘要内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) { Text("收起") }
        },
        dismissButton = {
            TextButton(onClick = { onResummarize() }) { Text("重新摘要") }
        }
    )
}

