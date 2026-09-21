package dev.rcht.jist.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.rcht.jist.R
import dev.rcht.jist.ui.components.GlassScaffold
import dev.rcht.jist.ui.xposedchats.XposedChatsState
import dev.rcht.jist.ui.xposedchats.XposedSourceGroup

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

    LaunchedEffect(summarizeError) {
        val err = summarizeError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(err, duration = SnackbarDuration.Short)
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
            }
        )
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
