package dev.rcht.jist.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.rcht.jist.data.db.entity.SummaryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SummaryCard(
    summary: SummaryEntity,
    onClick: () -> Unit = {},
    selected: Boolean = false,
    onSelectChange: ((Boolean) -> Unit)? = null,
    isSelecting: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    useMarkdown: Boolean = true,
    glassHazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appIcon = remember(summary.packageName) {
        try {
            val d = context.packageManager.getApplicationIcon(summary.packageName)
            val b = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            d.setBounds(0, 0, 48, 48)
            d.draw(c)
            b.asImageBitmap()
        } catch (_: Exception) { null }
    }

    val card = @Composable {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (isSelecting && onSelectChange != null) {
                Checkbox(checked = selected, onCheckedChange = onSelectChange,
                    modifier = Modifier.padding(end = 8.dp))
            } else if (appIcon != null) {
                Image(bitmap = appIcon, contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).padding(end = 10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!summary.isRead) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(accentColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                summary.appName,
                                style = MaterialTheme.typography.labelMedium,
                                color = accentColor,
                                fontWeight = if (!summary.isRead) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        Text(summary.contactOrGroup, style = MaterialTheme.typography.titleSmall)
                    }
                    Text("${summary.messageCount} msg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (useMarkdown) {
                    MarkdownText(markdown = summary.summaryText, maxLines = 3)
                } else {
                    Text(
                        summary.summaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatTimeRange(summary.notificationTimeFrom, summary.notificationTimeTo),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "摘要 ${formatCreatedAt(summary.createdAt)}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (glassHazeState != null) {
        GlassCard(
            modifier = modifier.fillMaxWidth().clickable {
                if (isSelecting && onSelectChange != null) onSelectChange(!selected)
                else onClick()
            },
            hazeState = glassHazeState
        ) { card() }
    } else {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .clickable {
                    if (isSelecting && onSelectChange != null) onSelectChange(!selected)
                    else onClick()
                },
            colors = CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else containerColor
            )
        ) { card() }
    }
}

private fun formatTimeRange(from: Long, to: Long): String {
    if (from == 0L || to == 0L) return ""
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dayFmt = SimpleDateFormat("MMM dd", Locale.getDefault())
    val fromStr = fmt.format(Date(from))
    val toStr = fmt.format(Date(to))
    return if (from == to) {
        fromStr
    } else if (dayFmt.format(Date(from)) == dayFmt.format(Date(to))) {
        "$fromStr → $toStr"
    } else {
        "${dayFmt.format(Date(from))} $fromStr → ${dayFmt.format(Date(to))} $toStr"
    }
}

private fun formatCreatedAt(timeMs: Long): String {
    return SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timeMs))
}
