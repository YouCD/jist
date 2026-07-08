package dev.rcht.jist.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.rcht.jist.data.db.entity.ChatMessageEntity
import dev.rcht.jist.ui.components.GlassCard
import dev.rcht.jist.ui.components.GlassScaffold
import dev.rcht.jist.ui.xposedchats.XposedChatDetailState
import dev.rcht.jist.util.displayContent
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XposedChatDetailScreen(
    uiState: XposedChatDetailState,
    onNavigateBack: () -> Unit,
    onDeleteMessages: (List<Long>) -> Unit,
    onSummarize: () -> Unit,
    isSummarizing: Boolean = false,
    summarizeError: String? = null,
    onRefresh: () -> Unit,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var summarizedExpanded by remember { mutableStateOf(true) }
    var notSummarizedExpanded by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val chatName = uiState.chat?.chatName ?: ""

    GlassScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (selecting) "已选择 ${selectedIds.size} 条"
                        else chatName,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = { selecting = false; selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "退出选择")
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        if (selectedIds.isNotEmpty()) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else if (uiState.chat?.isSummarized == true) {
                        Row {
                            IconButton(
                                onClick = onSummarize,
                                enabled = !isSummarizing
                            ) {
                                if (isSummarizing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = "AI摘要", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
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
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无消息", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        reverseLayout = false
                    ) {
                        val summarized = uiState.messages.filter { it.id in uiState.summarizedMessageIds }
                        val notSummarized = uiState.messages.filter { it.id !in uiState.summarizedMessageIds }

                        if (notSummarized.isNotEmpty()) {
                            item(key = "not_summarized_header") {
                                SectionSubHeader("未摘要", notSummarized.size, notSummarizedExpanded,
                                    onClick = { notSummarizedExpanded = !notSummarizedExpanded })
                            }
                            if (notSummarizedExpanded) {
                                items(notSummarized, key = { it.id }) { msg ->
                                    MessageBubble(
                                        msg = msg,
                                        selecting = selecting,
                                        selected = msg.id in selectedIds,
                                        onClick = {
                                            if (selecting) {
                                                selectedIds = if (msg.id in selectedIds)
                                                    selectedIds - msg.id
                                                else selectedIds + msg.id
                                            }
                                        },
                                        onLongClick = {
                                            selecting = true
                                            selectedIds = setOf(msg.id)
                                        }
                                    )
                                }
                            }
                        }
                        if (summarized.isNotEmpty()) {
                            item(key = "summarized_header") {
                                SectionSubHeader("已摘要", summarized.size, summarizedExpanded,
                                    onClick = { summarizedExpanded = !summarizedExpanded })
                            }
                            if (summarizedExpanded) {
                                items(summarized, key = { it.id }) { msg ->
                                    MessageBubble(
                                        msg = msg,
                                        selecting = selecting,
                                        selected = msg.id in selectedIds,
                                        onClick = {
                                            if (selecting) {
                                                selectedIds = if (msg.id in selectedIds)
                                                    selectedIds - msg.id
                                                else selectedIds + msg.id
                                            }
                                        },
                                        onLongClick = {
                                            selecting = true
                                            selectedIds = setOf(msg.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    summarizeError?.let { err ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { /* auto-dismiss next load */ }) { Text("知道了") }
            }
        ) { Text(err) }
    }

    if (showDeleteDialog && selectedIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定删除选中的 ${selectedIds.size} 条消息？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteMessages(selectedIds.toList())
                        selectedIds = emptySet()
                        selecting = false
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SectionSubHeader(title: String, count: Int, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "折叠" else "展开",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = title, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "($count)", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(msg: ChatMessageEntity, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    GlassCard(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (!selecting) onLongClick() }
            )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            if (selecting) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (msg.senderName.isNotBlank()) {
                        Text(
                            text = msg.senderName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = formatMessageTime(msg.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = displayContent(msg.content),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

