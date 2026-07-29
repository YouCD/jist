package dev.rcht.jist.ui.screens

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import dev.rcht.jist.R
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import dev.rcht.jist.ui.components.GlassScaffold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.SpanStyle
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
    onCleanup: (packageName: String?, days: Int?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var isSelecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var displayLimits by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var showCleanupDialog by remember { mutableStateOf(false) }
    var cleanupSelectedAppPkg by remember { mutableStateOf<String?>(null) }
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
                    if (isSelecting) Text(stringResource(R.string.selection_count, selectedIds.size), fontWeight = FontWeight.SemiBold)
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
                    if (!isSelecting) {
                        IconButton(onClick = { showCleanupDialog = true }) {
                            Icon(Icons.Filled.AutoDelete, contentDescription = stringResource(R.string.notification_log_cleanup))
                        }
                    }
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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
                        val isExpanded = group.packageName in displayLimits
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
                                                displayLimits = if (isExpanded) {
                                                    displayLimits - group.packageName
                                                } else {
                                                    displayLimits + (group.packageName to 100)
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
                                        onClick = {
                                            showMenu = false
                                            displayLimits = if (isExpanded) {
                                                displayLimits - group.packageName
                                            } else {
                                                displayLimits + (group.packageName to group.notifications.size)
                                            }
                                        }
                                    )
                                }
                                HorizontalDivider(thickness = 1.dp, color = Color(0xFF333333))
                            }
                        }

                        val displayList = group.notifications.take(displayLimits[group.packageName] ?: 0)

                        if (isExpanded) {
                            items(displayList, key = { it.id }) { notification ->
                                val selected = notification.id in selectedIds
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .combinedClickable(
                                            onClick = {
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
                                            onLongClick = {
                                                if (!isSelecting) {
                                                    isSelecting = true
                                                    selectedIds = setOf(notification.id)
                                                }
                                            }
                                        ),
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

                            val totalCount = group.notifications.size
                            val currentLimit = displayLimits[group.packageName] ?: 0
                            if (currentLimit < totalCount) {
                                item(key = "${group.packageName}_show_more") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        TextButton(onClick = {
                                            val newLimit = minOf(currentLimit + 100, totalCount)
                                            displayLimits = displayLimits + (group.packageName to newLimit)
                                        }) {
                                            Text(stringResource(R.string.notification_log_load_more, totalCount - currentLimit), color = MaterialTheme.colorScheme.primary)
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

    if (showCleanupDialog) {
        var expandedAppDropdown by remember { mutableStateOf(false) }
        var showSecondaryConfirm by remember { mutableStateOf(false) }
        var sliderPosition by remember { mutableStateOf(2f) }
        val cleanupSelectedDays by remember {
            derivedStateOf {
                when (sliderPosition.toInt()) {
                    0 -> null
                    1 -> 1
                    2 -> 7
                    3 -> 30
                    4 -> 60
                    else -> 7
                }
            }
        }
        val selectedAppName = if (cleanupSelectedAppPkg == null) {
            stringResource(R.string.notification_log_cleanup_all_apps)
        } else {
            appGroups.first { it.packageName == cleanupSelectedAppPkg }.appName
        }
        val days = cleanupSelectedDays
        val cutoff = if (days != null) {
            System.currentTimeMillis() - days * 86400_000L
        } else {
            Long.MAX_VALUE
        }
        val estimateCount = if (cleanupSelectedAppPkg == null) {
            allNotifications.count { it.timestamp < cutoff }
        } else {
            appGroups.find { it.packageName == cleanupSelectedAppPkg }
                ?.notifications?.count { it.timestamp < cutoff } ?: 0
        }
        val hasRecords = estimateCount > 0
        val animatedCount by animateIntAsState(
            targetValue = estimateCount,
            animationSpec = tween(300)
        )

        AlertDialog(
            onDismissRequest = { showCleanupDialog = false },
            title = { Text(stringResource(R.string.notification_log_cleanup_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.notification_log_cleanup_app), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Box {
                        TextButton(onClick = { expandedAppDropdown = true }) {
                            Text(selectedAppName, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = expandedAppDropdown, onDismissRequest = { expandedAppDropdown = false }) {
                            val allAppCount = allNotifications.count { it.timestamp < cutoff }
                            DropdownMenuItem(
                                text = {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stringResource(R.string.notification_log_cleanup_all_apps))
                                        Text("$allAppCount", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    }
                                },
                                onClick = { cleanupSelectedAppPkg = null; expandedAppDropdown = false }
                            )
                            appGroups.forEach { group ->
                                val gc = group.notifications.count { it.timestamp < cutoff }
                                DropdownMenuItem(
                                    text = {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(group.appName)
                                            Text("$gc", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                        }
                                    },
                                    onClick = { cleanupSelectedAppPkg = group.packageName; expandedAppDropdown = false }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(stringResource(R.string.notification_log_cleanup_range), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (sliderPosition.toInt()) {
                            0 -> stringResource(R.string.notification_log_cleanup_all)
                            1 -> stringResource(R.string.notification_log_cleanup_days, 1)
                            2 -> stringResource(R.string.notification_log_cleanup_days, 7)
                            3 -> stringResource(R.string.notification_log_cleanup_days, 30)
                            4 -> stringResource(R.string.notification_log_cleanup_days, 60)
                            else -> ""
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    val tickCount = 5
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val trackBgColor = primaryColor.copy(alpha = 0.08f)
                    val visitedTickColor = primaryColor.copy(alpha = 0.5f)
                    val unvisitedTickColor = Color.White.copy(alpha = 0.2f)
                    val labelTexts = listOf(
                        stringResource(R.string.notification_log_cleanup_all),
                        stringResource(R.string.notification_log_cleanup_days, 1),
                        stringResource(R.string.notification_log_cleanup_days, 7),
                        stringResource(R.string.notification_log_cleanup_days, 30),
                        stringResource(R.string.notification_log_cleanup_days, 60)
                    )
                    val textMeasurer = rememberTextMeasurer()
                    val labelTextStyle = TextStyle(fontSize = 11.sp, fontFamily = MaterialTheme.typography.labelSmall.fontFamily)
                    Slider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        valueRange = 0f..4f,
                        onValueChangeFinished = { sliderPosition = sliderPosition.roundToInt().toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        thumb = { Box(Modifier.size(1.dp)) },
                        track = { sliderState ->
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                val w = size.width
                                val trackCenterY = size.height / 2f - 8.dp.toPx()
                                val trackHeight = 4.dp.toPx()
                                val range = sliderState.valueRange
                                val fraction = (sliderState.value - range.start) /
                                    (range.endInclusive - range.start)
                                val currentX = w * fraction

                                // 1. Background track
                                drawRoundRect(
                                    color = trackBgColor,
                                    topLeft = Offset(0f, trackCenterY - trackHeight / 2f),
                                    size = Size(w, trackHeight),
                                    cornerRadius = CornerRadius(trackHeight / 2f)
                                )

                                // 2. Progress fill (gradient cyan → primary)
                                if (currentX > 0.5f) {
                                    drawRoundRect(
                                        brush = Brush.linearGradient(
                                            colors = listOf(JistCyan, primaryColor),
                                            start = Offset.Zero,
                                            end = Offset(currentX, 0f)
                                        ),
                                        topLeft = Offset(0f, trackCenterY - trackHeight / 2f),
                                        size = Size(currentX, trackHeight),
                                        cornerRadius = CornerRadius(trackHeight / 2f)
                                    )
                                }

                                // 3. Tick nodes
                                val visitedR = 5.dp.toPx()
                                val unvisitedR = 4.dp.toPx()
                                for (i in 0 until tickCount) {
                                    val x = w * i / (tickCount - 1)
                                    drawCircle(
                                        color = if (x < currentX) visitedTickColor else unvisitedTickColor,
                                        radius = if (x < currentX) visitedR else unvisitedR,
                                        center = Offset(x, trackCenterY)
                                    )
                                }

                                // 4. Current node (glowing hero dot)
                                val nodeR = 7.dp.toPx()
                                val glowR = 13.dp.toPx()
                                drawCircle(color = primaryColor.copy(alpha = 0.07f), radius = glowR, center = Offset(currentX, trackCenterY))
                                drawCircle(color = primaryColor.copy(alpha = 0.15f), radius = glowR * 0.65f, center = Offset(currentX, trackCenterY))
                                drawCircle(color = primaryColor, radius = nodeR, center = Offset(currentX, trackCenterY))
                                drawCircle(color = Color.White, radius = nodeR - 1.dp.toPx(), center = Offset(currentX, trackCenterY), style = Stroke(2.dp.toPx()))

                                // 5. Labels perfectly aligned with each tick
                                val labelY = size.height - 14.dp.toPx()
                                labelTexts.forEachIndexed { i, label ->
                                    val isCurrent = i == sliderPosition.roundToInt()
                                    val x = w * i / (tickCount - 1)
                                    val result = textMeasurer.measure(
                                        text = label,
                                        style = labelTextStyle.copy(
                                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isCurrent) primaryColor else Color.White.copy(alpha = 0.4f)
                                        )
                                    )
                                    drawText(
                                        textLayoutResult = result,
                                        topLeft = Offset(
                                            x = x - result.size.width / 2f,
                                            y = labelY + if (isCurrent) (-2).dp.toPx() else 0f
                                        )
                                    )
                                }
                            }
                        }
                    )

                    Spacer(Modifier.height(16.dp))

                    if (hasRecords) {
                        val estPrefix = stringResource(R.string.notification_log_cleanup_estimate_prefix)
                        val estSuffix = stringResource(R.string.notification_log_cleanup_estimate_suffix)
                        Text(
                            text = buildAnnotatedString {
                                append(estPrefix)
                                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp))
                                append("$animatedCount")
                                pop()
                                append(estSuffix)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            stringResource(R.string.notification_log_cleanup_no_records),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚠",
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.notification_log_cleanup_irreversible),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }

                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (estimateCount > 500) {
                            showSecondaryConfirm = true
                        } else {
                            showCleanupDialog = false
                            onCleanup(cleanupSelectedAppPkg, cleanupSelectedDays)
                        }
                    },
                    enabled = hasRecords
                ) {
                    Text(
                        stringResource(R.string.notification_log_cleanup_confirm),
                        color = if (hasRecords) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupDialog = false }) {
                    Text(stringResource(R.string.summaries_delete_cancel))
                }
            }
        )

        if (showSecondaryConfirm) {
            AlertDialog(
                onDismissRequest = { showSecondaryConfirm = false },
                title = { Text(stringResource(R.string.notification_log_cleanup_secondary_title)) },
                text = { Text(stringResource(R.string.notification_log_cleanup_secondary_message, estimateCount)) },
                confirmButton = {
                    TextButton(onClick = {
                        showSecondaryConfirm = false
                        showCleanupDialog = false
                        onCleanup(cleanupSelectedAppPkg, cleanupSelectedDays)
                    }) {
                        Text(stringResource(R.string.notification_log_cleanup_secondary_confirm), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSecondaryConfirm = false }) {
                        Text(stringResource(R.string.summaries_delete_cancel))
                    }
                }
            )
        }
    }
}
