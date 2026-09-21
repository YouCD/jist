package dev.rcht.jist.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.rcht.jist.ui.components.MarkdownText
import dev.rcht.jist.util.DrawableUtil

@Composable
internal fun SourceSectionHeader(
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

@Composable
internal fun SummaryDialog(
    chatName: String,
    messageCount: Int,
    summaryText: String,
    createdAt: Long,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        BackHandler(enabled = true) { onDismiss() }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "「$chatName」摘要 · ${messageCount} 条消息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text("摘要时间：${formatTimestamp(createdAt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (summaryText.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        MarkdownText(
                            markdown = summaryText,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Text(
                        text = "暂无摘要内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onDismiss() }) { Text("收起") }
                }
            }
        }
    }
}

