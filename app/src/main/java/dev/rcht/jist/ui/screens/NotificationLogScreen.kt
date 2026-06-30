package dev.rcht.jist.ui.screens

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import dev.rcht.jist.R
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import dev.rcht.jist.ui.components.GlassScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import dev.rcht.jist.data.db.entity.NotificationEntity

/**
 * Try to perform a notification action via NAF (Notification Action Framework).
 * Uses reflection to call hidden @SystemApi NotificationManager.performNotificationAction().
 */
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationLogScreen(
    notifications: List<NotificationEntity> = emptyList(),
    isLoading: Boolean = false,
    onRefresh: () -> Unit = {},
    onDelete: (List<Long>) -> Unit = {},
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
                    } else if (!isSelecting && notifications.isNotEmpty()) {
                        TextButton(onClick = { isSelecting = true; selectedIds = emptySet() }) {
                            Text(stringResource(R.string.summaries_select))
                        }
                    }
                    if (isSelecting && selectedIds.size < notifications.size) {
                        TextButton(onClick = { selectedIds = notifications.map { it.id }.toSet() }) {
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
            } else if (notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.notification_log_title), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                items(notifications, key = { it.id }) { notification ->
                    val selected = notification.id in selectedIds
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelecting) {
                                    selectedIds = if (selected) selectedIds - notification.id else selectedIds + notification.id
                                 } else {
                                    // Priority 1: NAF (Notification Action Framework)
                                    // Requires device with NAF framework patch (Android 16+ LineageOS)
                                    var nafSuccess = false
                                    val notifKey = notification.notificationKey
                                    if (!notifKey.isNullOrBlank()) {
                                        nafSuccess = tryNafAction(ctx, notifKey, 1 /* ACTION_CONTENT */, null)
                                    }
                                    if (!nafSuccess) {
                                        // Fallback: ChatIntentBuilder (always works, opens app main screen)
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
                                    selectedIds = if (selected) selectedIds - notification.id else selectedIds + notification.id
                                })
                                Spacer(Modifier.width(4.dp))
                            }
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            val appIcon = remember(notification.packageName) {
                                try {
                                    val d = ctx.packageManager.getApplicationIcon(notification.packageName)
                                    val b = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
                                    val c = android.graphics.Canvas(b)
                                    d.setBounds(0, 0, 48, 48)
                                    d.draw(c)
                                    b.asImageBitmap()
                                } catch (e: Exception) { null }
                            }
                            if (appIcon != null) {
                                Image(bitmap = appIcon, contentDescription = null,
                                    modifier = Modifier.size(36.dp).padding(end = 8.dp).clip(RoundedCornerShape(6.dp)))
                            }
                            Column(Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    val ctx = androidx.compose.ui.platform.LocalContext.current
                                    val appLabel = remember(notification.packageName) {
                                        try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(notification.packageName, 0)).toString() }
                                        catch (e: Exception) { notification.appName }
                                    }
                                    Text(appLabel.take(20), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.getDefault())
                                            .format(java.util.Date(notification.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(notification.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(notification.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5, overflow = TextOverflow.Ellipsis)
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
