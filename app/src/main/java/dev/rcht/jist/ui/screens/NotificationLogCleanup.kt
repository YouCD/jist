package dev.rcht.jist.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rcht.jist.R
import dev.rcht.jist.data.db.entity.NotificationEntity
import dev.rcht.jist.ui.notificationlog.AppNotificationGroup
import dev.rcht.jist.ui.theme.JistCyan
import kotlin.math.roundToInt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

/**
 * Custom slider with tick nodes and aligned labels, used by the
 * notification-cleanup dialog.
 */
@Composable
internal fun TickSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        value = value,
        onValueChange = onValueChange,
        valueRange = 0f..4f,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier.fillMaxWidth(),
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
            val isCurrent = i == value.roundToInt()
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
}

/**
 * Cleanup confirmation dialog: pick the app scope, the age cutoff and
 * confirm the deletion (with a secondary confirmation for large counts).
 */
@Composable
internal fun CleanupDialog(
    appGroups: List<AppNotificationGroup>,
    allNotifications: List<NotificationEntity>,
    selectedAppPkg: String?,
    onSelectedAppPkgChange: (String?) -> Unit,
    onDismiss: () -> Unit,
    onCleanup: (packageName: String?, days: Int?) -> Unit
) {
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
val selectedAppName = if (selectedAppPkg == null) {
    stringResource(R.string.notification_log_cleanup_all_apps)
} else {
    appGroups.first { it.packageName == selectedAppPkg }.appName
}
val days = cleanupSelectedDays
val cutoff = if (days != null) {
    System.currentTimeMillis() - days * 86400_000L
} else {
    Long.MAX_VALUE
}
val estimateCount = if (selectedAppPkg == null) {
    allNotifications.count { it.timestamp < cutoff }
} else {
    appGroups.find { it.packageName == selectedAppPkg }
        ?.notifications?.count { it.timestamp < cutoff } ?: 0
}
val hasRecords = estimateCount > 0
val animatedCount by animateIntAsState(
    targetValue = estimateCount,
    animationSpec = tween(300)
)

AlertDialog(
    onDismissRequest = { onDismiss() },
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
                        onClick = { onSelectedAppPkgChange(null); expandedAppDropdown = false }
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
                            onClick = { onSelectedAppPkgChange(group.packageName); expandedAppDropdown = false }
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
                    TickSlider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        onValueChangeFinished = { sliderPosition = sliderPosition.roundToInt().toFloat() },
                        modifier = Modifier.fillMaxWidth()
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
                    onDismiss()
                    onCleanup(selectedAppPkg, cleanupSelectedDays)
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
        TextButton(onClick = { onDismiss() }) {
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
                onDismiss()
                onCleanup(selectedAppPkg, cleanupSelectedDays)
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
