package dev.rcht.jist.ui.screens

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import dev.rcht.jist.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import dev.rcht.jist.ui.components.GlassScaffold
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import dev.rcht.jist.data.db.entity.NotificationEntity
import dev.rcht.jist.ui.notificationlog.AppNotificationGroup
import dev.rcht.jist.ui.theme.JistCyan

private fun tryNafAction(context: Context, key: String, action: Int, extras: Bundle?): Boolean {
    return try {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val method = NotificationManager::class.java
            .getDeclaredMethod("performNotificationAction",
                String::class.java, Int::class.java, Bundle::class.java)
        method.invoke(nm, key, action, extras)
        true
    } catch (e: Exception) {
        Log.w("NotificationLog", "NAF failed: ${e.message}")
        false
    }
}

private fun formatCount(count: Int): String {
    return if (count > 99) "99+" else count.toString()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotificationLogScreen(
    appGroups: List<AppNotificationGroup> = emptyList(),
    isLoading: Boolean = false,
    onRefresh: () -> Unit = {},
    onDelete: (List<Long>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isSelecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var expandedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lazyListState = rememberLazyListState()
    val stickyHeaderKeys by remember {
        derivedStateOf {
            lazyListState.layoutInfo.visibleItemsInfo
                .filter { it.key is String && (it.key as String).startsWith("header_") }
                .filter { it.offset == lazyListState.layoutInfo.viewportStartOffset }
                .map { it.key as String }
                .toSet()
        }
    }

    val allNotifications = appGroups.flatMap { it.notifications }

    GlassScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (isSelecting) Text("${selectedIds.size} selected", fontWeight = FontWeight.SemiBold)
                    else Text(stringResource(R.string.notification_log_title), fontWeight = FontWeight.SemiBold)
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
                    } else if (!isSelecting && allNotifications.isNotEmpty()) {
                        TextButton(onClick = { isSelecting = true; selectedIds = emptySet() }) {
                            Text(stringResource(R.string.summaries_select))
                        }
                    }
                    if (isSelecting && selectedIds.size < allNotifications.size) {
                        TextButton(onClick = { selectedIds = allNotifications.map { it.id }.toSet() }) {
                            Text(stringResource(R.string.app_settings_select_all))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (appGroups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.notification_log_title), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    appGroups.forEach { group ->
                        val isExpanded = group.packageName in expandedApps
                        val headerKey = "header_${group.packageName}"
                        val isSticky = headerKey in stickyHeaderKeys

                        stickyHeader(key = headerKey) {
                            val rotation by animateFloatAsState(
                                targetValue = if (isExpanded) 180f else 0f,
                                animationSpec = tween(200)
                            )
                            var showMenu by remember { mutableStateOf(false) }
                            val appIcon = remember(group.packageName) {
                                try {
                                    val d = ctx.packageManager.getApplicationIcon(group.packageName)
                                    val b = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
                                    val c = android.graphics.Canvas(b)
                                    d.setBounds(0, 0, 48, 48)
                                    d.draw(c)
                                    b.asImageBitmap()
                                } catch (e: Exception) { null }
                            }
                            val latestTs = group.notifications.maxOfOrNull { it.timestamp } ?: 0L

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                expandedApps = if (isExpanded) {
                                                    expandedApps - group.packageName
                                                } else {
                                                    expandedApps + group.packageName
                                                }
                                            },
                                            onLongClick = { showMenu = true }
                                        )
                                        .padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (appIcon != null) {
                                        Image(bitmap = appIcon, contentDescription = null,
                                            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp)))
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Text(
                                        text = group.appName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${group.count}条",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (latestTs > 0) {
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "· ${formatTimestamp(latestTs)}",
                                            fontSize = 12.sp,
                                            color = timestampColor(latestTs)
                                        )
                                    }
                                    Spacer(Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        modifier = Modifier.size(16.dp).rotate(rotation),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (isExpanded) "全部折叠" else "全部展开") },
                                        onClick = { showMenu = false; expandedApps = if (isExpanded) expandedApps - group.packageName else expandedApps + group.packageName }
                                    )
                                }
                                HorizontalDivider(thickness = 1.dp, color = Color(0xFF333333))
                            }
                        }

                        item(key = "${group.packageName}_notifications") {
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                                exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                            ) {
                                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                    group.notifications.forEach { notification ->
                                        val selected = notification.id in selectedIds
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 6.dp)
                                                .clickable {
                                                    if (isSelecting) {
                                                        selectedIds = if (selected) selectedIds - notification.id
                                                            else selectedIds + notification.id
                                                    } else {
                                                        var nafSuccess = false
                                                        val notifKey = notification.notificationKey
                                                        if (!notifKey.isNullOrBlank()) {
                                                            nafSuccess = tryNafAction(ctx, notifKey, 1, null)
                                                        }
                                                        if (!nafSuccess) {
                                                            val intent = dev.rcht.jist.util.ChatIntentBuilder.buildChatIntent(
                                                                ctx, notification.packageName, notification.conversationKey, notification.title
                                                            )
                                                            if (intent != null) {
                                                                ctx.startActivity(intent)
                                                            }
                                                        }
                                                    }
                                                },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                                    else MaterialTheme.colorScheme.surface
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    if (isSelecting) {
                                                        Checkbox(checked = selected, onCheckedChange = {
                                                            selectedIds = if (selected) selectedIds - notification.id
                                                                else selectedIds + notification.id
                                                        })
                                                        Spacer(Modifier.width(4.dp))
                                                    }
                                                    Column(Modifier.weight(1f)) {
                                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text(
                                                                text = notification.title.let { if (it.length > 30) it.take(30) + "…" else it },
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.getDefault())
                                                                    .format(java.util.Date(notification.timestamp)),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        Spacer(Modifier.height(2.dp))
                                                        Text(notification.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
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
                    onDelete(selectedIds.toList())
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
