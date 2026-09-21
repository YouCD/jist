package dev.rcht.jist.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared text-formatting helpers used by screens that display
 * chat / notification timestamps and message counts.
 */
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
